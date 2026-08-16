package fr.paralya.bot.lg

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.User
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.MessageUpdateEvent
import dev.kord.core.event.message.ReactionAddEvent
import dev.kord.core.event.message.ReactionRemoveEvent
import dev.kord.core.exception.EntityNotFoundException
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.embed
import dev.kord.rest.request.RestRequestException
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.events.EventContext
import dev.kordex.core.koin.KordExKoinComponent
import fr.paralya.bot.common.config.BotConfig
import fr.paralya.bot.common.contextTranslate
import fr.paralya.bot.common.format
import fr.paralya.bot.common.getAsset
import fr.paralya.bot.common.getCorrespondingMessage
import fr.paralya.bot.common.getWebhook
import fr.paralya.bot.common.isAdmin
import fr.paralya.bot.common.sendAsWebhook
import fr.paralya.bot.common.sendTemporaryMessage
import fr.paralya.bot.lg.data.getLastWerewolfMessageSender
import fr.paralya.bot.lg.data.getProfilePictureState
import fr.paralya.bot.lg.data.setLastWerewolfMessageSender
import fr.paralya.bot.lg.data.toggleProfilePicture
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import org.koin.core.component.get
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes
import fr.paralya.bot.lg.I18n as Lg

private const val MESSAGE_MAX_LENGTH = 2000
class LgRelayService : KordExKoinComponent {
    private val logger = KotlinLogging.logger(this::class.java.name)
    private val bot by inject<ExtensibleBot>()
    private val plugin by inject<LgPlugin>()
    private val botCache by lazy { bot.kordRef.cache }

    private fun User?.shouldIgnore(botConfig: BotConfig) = this == null ||
            isAdmin(botConfig) || this.isBot || this.isSelf

    context(context: EventContext<MessageCreateEvent>)
    suspend fun onMessageSent(
        webhookName: String,
        outChannel: Snowflake,
        isAnonymous: Boolean
    ) {
        val botConfig = this.get<BotConfig>()
        val message = context.event.message
        if (message.author.shouldIgnore(botConfig)) return

        if (message.content.length > MESSAGE_MAX_LENGTH) {
            val channel = message.channel
            logger.warn {
                "Received a message too long (${message.content.length} characters). " +
                        "It'll be deleted and the user will be alerted."
            }
            channel.sendTemporaryMessage(
                Lg.Transmission.Error.messageTooLong.contextTranslate(message.content.length),
                1.minutes
            )
            message.content.chunked(MESSAGE_MAX_LENGTH).forEach {
                channel.sendTemporaryMessage(it, 1.minutes)
            }
            message.delete(Lg.Transmission.Error.Reason.messageTooLong.contextTranslate())
            return
        }

        logger.debug { "Message sender is ${message.author?.id?.value ?: "not known"}" }
        val (userName, userAvatar) = getMessageIdentity(message.author, isAnonymous, true)
        logger.debug { "Avatar is $userAvatar and name is $userName" }
        val content = buildRelayContent(message)
        if (isAnonymous) outChannel.sendAsWebhook(
            bot,
            userName,
            plugin.getAsset(userAvatar),
            webhookName,
            content
        ) else outChannel.sendAsWebhook(
            bot,
            userName,
            userAvatar,
            webhookName = webhookName,
            message = content
        )
    }


    context(context: EventContext<MessageDeleteEvent>)
    suspend fun onMessageDelete(
        webhookName: String,
        outChannel: Snowflake?,
    ) {
        val botConfig =  this.get<BotConfig>()
        val eventMessage = context.event.message ?: return
        if (eventMessage.author.shouldIgnore(botConfig)) return
        val oldMessage = outChannel?.let {
            MessageChannelBehavior(outChannel, bot.kordRef).getCorrespondingMessage(eventMessage)
        }
        if (oldMessage != null) {
            val webhook = bot.getWebhook(outChannel, webhookName)
            try {
                // Keep the NPE here, we want a loud error if it's null
                @Suppress("UnsafeCallOnNullableType")
                webhook.deleteMessage(webhook.token!!, oldMessage.id)
            } catch (e: RestRequestException) {
                logger.error(e) { "Error while deleting message" }
            }
        }
    }

    context(context: EventContext<MessageUpdateEvent>)
    suspend fun onMessageUpdate(
        webhookName: String,
        outChannel: Snowflake,
        isAnonymous: Boolean
    ) {
        val botConfig = this.get<BotConfig>()
        val event = context.event
        if (event.old?.author.shouldIgnore(botConfig)) return
        val oldMessage = event.old?.let { MessageChannelBehavior(outChannel, bot.kordRef).getCorrespondingMessage(it) }
        val webhook = bot.getWebhook(outChannel, webhookName)
        val newMessage = event.message.asMessage()
        if (oldMessage != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                // Keep the NPE here, we want a loud error if it's null
                @Suppress("UnsafeCallOnNullableType")
                webhook.getMessage(webhook.token!!, oldMessage.id).edit {
                    content = newMessage.content
                    val referencedMessage = newMessage.referencedMessage
                    if (referencedMessage != null) embed {
                        title = Lg.Transmission.Reference.title.contextTranslate()
                        description = referencedMessage.content
                    } else embeds?.clear()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                logger.debug { "Message sender is ${event.old?.author?.id?.value ?: "not known"}" }
                val (userName, userAvatar) = getMessageIdentity(event.old?.author, isAnonymous)
                logger.debug { "Avatar is $userAvatar and name is $userName" }
                val content = buildRelayContent(newMessage) {
                    embed {
                        title = Lg.Transmission.Update.title.contextTranslate()
                        description = oldMessage.content
                    }
                }
                outChannel.sendAsWebhook(
                    bot,
                    userName,
                    userAvatar,
                    webhookName = webhookName,
                    message = content
                )
            }
        }
    }


    context(context: EventContext<ReactionAddEvent>)
    suspend fun onReactionAdd(
        webhookName: String,
        outChannel: Snowflake,
        isAnonymous: Boolean
    ) {
        val event = context.event
        try {
            onReactionChange(
                webhookName, outChannel, isAnonymous,
                event.getUserOrNull(), event.message.asMessage(), event.emoji, true
            )
        } catch (_: EntityNotFoundException) {
            logger.debug { "Message not found while processing reaction add" }
        }
    }

    context(context: EventContext<ReactionRemoveEvent>)
    suspend fun onReactionRemove(
        webhookName: String,
        outChannel: Snowflake,
        isAnonymous: Boolean
    ) {
        val event = context.event
        try {
            onReactionChange(
                webhookName, outChannel, isAnonymous,
                event.getUserOrNull(), event.message.asMessage(), event.emoji, false
            )
        } catch (_: EntityNotFoundException) {
            logger.debug { "Message not found while processing reaction remove" }
        }
    }


    context(ctx: EventContext<*>)
    private suspend fun onReactionChange(
        webhookName: String,
        outChannel: Snowflake,
        isAnonymous: Boolean,
        author: User?,
        message: Message,
        emoji: ReactionEmoji,
        isAdd: Boolean
    ) {
        val botConfig = this.get<BotConfig>()
        if (message.author.shouldIgnore(botConfig) || author.shouldIgnore(botConfig)) return
        val (userName, userAvatar) = getMessageIdentity(author, isAnonymous)
        val content = buildRelayReactionContent(emoji, message, isAdd)
        if (isAnonymous) outChannel.sendAsWebhook(
            bot,
            userName,
            plugin.getAsset(userAvatar),
            webhookName,
            content
        ) else outChannel.sendAsWebhook(
            bot,
            userName,
            userAvatar,
            webhookName,
            content
        )
    }

    private suspend fun getMessageIdentity(
        author: User?,
        isAnonymous: Boolean,
        updateExisting: Boolean = false
    ) = if (isAnonymous) {
        if (updateExisting && author != null && author.id != botCache.getLastWerewolfMessageSender()) {
            botCache.setLastWerewolfMessageSender(author.id)
            botCache.toggleProfilePicture()
        }
        val profilePictureState = botCache.getProfilePictureState()
        val name = if (profilePictureState) "🐺 Anonyme" else "🐺Anonyme"
        val pp = if (profilePictureState) "wolf_variant_2" else "wolf_variant_1"
        name to pp
    } else (author?.username ?: "Message Author") to author?.avatar?.cdnUrl?.toUrl().orEmpty()

    context(ctx: EventContext<*>)
    private fun buildRelayContent(
        message: Message,
        additionalElements: (suspend MessageBuilder.() -> Unit)? = null
    ): suspend MessageBuilder.() -> Unit = {
        val botConfig = ctx.get<BotConfig>()
        content = message.content
        val referencedMessage = message.referencedMessage
        if (referencedMessage != null && !referencedMessage.author.isAdmin(botConfig)) embed {
            title = Lg.Transmission.Reference.title.contextTranslate()
            description = referencedMessage.content
        }

        additionalElements?.invoke(this)
    }

    context(ctx: EventContext<*>)
    private fun buildRelayReactionContent(
        reaction: ReactionEmoji,
        message: Message,
        isAdd: Boolean,
        additionalElements: (suspend MessageBuilder.() -> Unit)? = null
    ): suspend MessageBuilder.() -> Unit = {
        content = (if (isAdd) Lg.Transmission.Reaction.add
            else Lg.Transmission.Reaction.remove).contextTranslate(reaction.format()
        )

        embed {
            title = Lg.Transmission.Reaction.Content.title.contextTranslate()
            description = message.content
        }
        val botConfig = ctx.get<BotConfig>()
        val referencedMessage = message.referencedMessage
        if (referencedMessage != null && !referencedMessage.author.isAdmin(botConfig)) embed {
            title = Lg.Transmission.Reference.title.contextTranslate()
            description = referencedMessage.content
        }

        additionalElements?.invoke(this)
    }
}
