package fr.paralya.bot.common

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.execute
import dev.kord.core.cache.data.WebhookData
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.Webhook
import dev.kord.rest.Image
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.create.UserMessageCreateBuilder
import dev.kord.rest.builder.message.create.WebhookMessageCreateBuilder
import dev.kord.rest.request.RestRequestException
import dev.kordex.core.ExtensibleBot
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger("MessageUtils")

/**
 * Sends a temporary message to a channel and deletes it after a specified delay.
 *
 * @param delay The delay in milliseconds before the message is deleted. Default is 10 milliseconds.
 * @param messageBuilder A lambda to build the message content.
 * @return The sent message.
 */
@Suppress("InjectDispatcher")
suspend fun MessageChannelBehavior.sendTemporaryMessage(
	delay: Duration = 10.seconds,
	messageBuilder: UserMessageCreateBuilder.() -> Unit
): Message {
	return createMessage(messageBuilder).also { message ->
		message.kord.launch(
			context = Dispatchers.IO + CoroutineName("TemporaryMessageDeletion-${message.id}"),
		) {
			delay(delay)
			try {
				withContext(NonCancellable + Dispatchers.IO) {
					message.delete()
				}
			} catch (e: RestRequestException) {
				logger.error(e) { "Failed to delete temporary message" }
			}
		}
	}
}

/**
 * Sends a temporary message to a channel with a simple string content and deletes it after a specified delay.
 *
 * @param message The content of the message to send.
 * @param delay The delay in milliseconds before the message is deleted. Default is 10 milliseconds.
 * @return The sent message.
 */
suspend fun MessageChannelBehavior.sendTemporaryMessage(message: String, delay: Duration = 10.seconds): Message {
	return sendTemporaryMessage(delay) {
		content = message
	}
}


/**
 * Checks if two messages are similar based on their content and attachments.
 *
 * @param msg1 The first message to compare.
 * @param msg2 The second message to compare.
 * @return `true` if the messages are similar, `false` otherwise.
 */
fun areMessagesSimilar(msg1: Message, msg2: Message): Boolean {
	if (msg1.content != msg2.content) return false

	val attachments1 = msg1.attachments.map { Triple(it.filename, it.size, it.isSpoiler) }.sortedBy { it.first }
	val attachments2 = msg2.attachments.map { Triple(it.filename, it.size, it.isSpoiler) }.sortedBy { it.first }

	return attachments1 == attachments2
}

const val MESSAGE_SEARCH_RANGE = 20
/**
 * Retrieves the corresponding message in the channel based on the timestamp of the provided message.
 *
 * @param message The message to find the corresponding message for.
 * @return The corresponding message if found, or null if not found.
 */
suspend fun MessageChannelBehavior.getCorrespondingMessage(message: Message): Message? {
	val date = message.timestamp

	val beforeMessage = getMessagesBefore(Snowflake.max, MESSAGE_SEARCH_RANGE)
		.filter { it.timestamp >= date }
		.toList()
		.sortedBy { it.timestamp }
		.firstOrNull { areMessagesSimilar(msg1 = message, msg2 = it) }

	if (beforeMessage != null) return beforeMessage
	return getMessagesAfter(Snowflake.min, MESSAGE_SEARCH_RANGE)
		.filter { it.timestamp <= date }
		.toList()
		.sortedByDescending { it.timestamp }
		.firstOrNull { areMessagesSimilar(msg1 = message, msg2 = it) }
		?: run {
			logger.warn {
				"No corresponding similar message found for message ${message.id} when searching in channel $id"
			}
			null
		}
}

private const val REACTION_DELAY = 250L

/**
 * Adds multiple reactions to a message.
 *
 * @param emojis List of emoji strings to add as reactions
 */
suspend fun Message.addReactions(emojis: List<ReactionEmoji>) {
	for (emoji in emojis) {
		addReaction(emoji)
		delay(REACTION_DELAY.milliseconds) // Add small delay to avoid rate limiting
	}
}

/**
 * Adds multiple reactions to a message.
 *
 * @param emojis Vararg of emoji strings to add as reactions
 */
suspend fun Message.addReactions(vararg emojis: ReactionEmoji) = addReactions(emojis.toList())

/**
 * Formats a ReactionEmoji for display in a message.
 *
 * @return The formatted emoji string
 */
fun ReactionEmoji.format(): String {
	return when (this) {
		is ReactionEmoji.Custom -> this.mention
		is ReactionEmoji.Unicode -> this.name
	}
}

/**
 * Extension function to easily add an emoji to message content in builders.
 *
 * @param emoji The emoji to add (Unicode or custom emoji format)
 */
fun MessageCreateBuilder.appendEmoji(emoji: String) {
	content = content.orEmpty() + emoji
}


/**
 * Retrieves or creates a webhook in a specified channel.
 *
 * @receiver The Kord instance used to interact with Discord.
 * @param channel The ID of the channel where the webhook will be retrieved or created.
 * @param name The name of the webhook to retrieve or create.
 * @param avatar An optional avatar image for the webhook. If not provided, Discord's default avatar will be used.
 * @return The retrieved or newly created webhook.
 */
@Suppress("SuspendFunWithCoroutineScopeReceiver")
suspend fun Kord.getWebhook(channel: Snowflake, name: String, avatar: Image? = null): Webhook {
	val existing = rest.webhook.getChannelWebhooks(channel)
		.firstOrNull { it.name == name }
	val data = existing ?: rest.webhook.createWebhook(channel, name) {
		this.avatar = avatar ?: getAsset("bot")
	}
	return Webhook(WebhookData.from(data), this)
}

suspend fun ExtensibleBot.getWebhook(channel: Snowflake, name: String, avatar: Image? = null) =
	kordRef.getWebhook(channel, name, avatar)

/**
 * Sends a message as a webhook in a specified channel.
 *
 * @receiver The Snowflake representing the channel ID where the message will be sent.
 * @param bot The instance of the [ExtensibleBot] used to interact with Discord.
 * @param name The name of the webhook to use or create.
 * @param avatar An optional avatar image for the webhook. If not provided, Discord's default avatar will be used.
 * @param message A message builder block to configure the message content and properties.
 * @return The message sent by the webhook, or null if the webhook token is unavailable.
 *
 * This function retrieves or creates a webhook in the specified channel, then uses it to send a message
 * with the provided content and properties. The webhook is identified by its name and optionally customized
 * with an avatar image.
 */
suspend fun Snowflake.sendAsWebhook(
	bot: ExtensibleBot,
	name: String,
	avatar: Image? = null,
	webhookName: String? = null,
	message: suspend WebhookMessageCreateBuilder.() -> Unit
): Message? {
	val webhook = bot.getWebhook(this, webhookName ?: name, avatar)
	return webhook.token?.let { token ->
		webhook.execute(token) {
			username = name
			message()
		}
	}
}

/**
 * Sends a message as a webhook in a specified channel with a string avatar URL.
 *
 * @receiver The Snowflake representing the channel ID where the message will be sent.
 * @param bot The instance of the [ExtensibleBot] used to interact with Discord.
 * @param name The name of the webhook to use or create.
 * @param avatar An optional avatar URL for the webhook. If not provided, Discord's default avatar will be used.
 * @param message A message builder block to configure the message content and properties.
 * @return The message sent by the webhook, or null if the webhook token is unavailable.
 */
suspend fun Snowflake.sendAsWebhook(
	bot: ExtensibleBot,
	name: String,
	avatar: String? = null,
	webhookName: String? = null,
	message: suspend WebhookMessageCreateBuilder.() -> Unit
): Message? {
	val webhook = bot.getWebhook(this, webhookName ?: name)
	return webhook.token?.let { token ->
		webhook.execute(token) {
			avatarUrl = avatar
			username = name
			message()
		}
	}
}
