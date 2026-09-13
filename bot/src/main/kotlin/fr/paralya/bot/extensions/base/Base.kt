package fr.paralya.bot.extensions.base

import dev.kord.common.asJavaLocale
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.MessageUpdateEvent
import dev.kord.rest.builder.message.embed
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.checks.noGuild
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.commands.application.slash.converters.impl.defaultingEnumChoice
import dev.kordex.core.commands.converters.impl.defaultingBoolean
import dev.kordex.core.commands.converters.impl.optionalChannel
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.optionalSnowflake
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.suggestStringMap
import dev.kordex.i18n.Key
import dev.kordex.i18n.I18n as KI18n
import fr.paralya.bot.common.config.ConfigManager
import fr.paralya.bot.I18n
import fr.paralya.bot.common.GameRegistry
import fr.paralya.bot.common.adminOnly
import fr.paralya.bot.common.asUser
import fr.paralya.bot.common.contextTranslate
import fr.paralya.bot.common.gameMode
import fr.paralya.bot.common.getCorrespondingMessage
import fr.paralya.bot.common.getWebhook
import fr.paralya.bot.common.isNotEphemeral
import fr.paralya.bot.common.isUser
import fr.paralya.bot.common.sendAsWebhook
import fr.paralya.bot.common.snowflake
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import org.koin.core.component.inject

private const val MAX_EXPORTABLE_MESSAGE = 1000

/**
 * Base extension for the bot.
 *
 * This extension handles the basic setup and configuration of the bot.
 * It includes basic commands and event listeners.
 *
 * @property name The name of the extension.
 * @property logger The logger for the extension.
 */
class Base : Extension() {
	override val name = "Base"
	private val logger = KotlinLogging.logger(this::class.java.simpleName)
	private val configManager by inject<ConfigManager>()
	private val gameRegistry by inject<GameRegistry>()
    override suspend fun setup() {
		val dmChannelId = configManager.botConfig.dmLogChannelId.snowflake
		event<ReadyEvent> {
			action {
				logger.info { "Bot connected to Discord as ${event.self.username}" }
			}
		}

		event<MessageCreateEvent> {
			check {
				isNotEphemeral()
				noGuild()
				isNotBot()
				failIf { message?.isNotEmpty() == false }
			}
			action {
				val message = event.message
				dmChannelId.sendAsWebhook(
					bot,
					message.author?.tag ?: "Inconnu",
					message.author?.avatar?.cdnUrl?.toUrl(),
					"DM"
				) {
					content = message.content
					val referencedMessage = message.referencedMessage
					if (referencedMessage != null) embed {
						title = I18n.Transmission.Reference.title.contextTranslate()
						description = referencedMessage.content
					}
				}
			}
		}
		event<MessageUpdateEvent> {
			check {
				noGuild()
				isNotBot()
				isUser()
			}

			action {
				val oldMessage = event.old?.let {
					MessageChannelBehavior(dmChannelId, kord).getCorrespondingMessage(it)
				}
				if (oldMessage != null) dmChannelId.sendAsWebhook(
					bot,
					event.new.author.value?.asUser(kord)?.tag ?: "Inconnu",
					event.new.author.value.asUser(kord)?.avatar?.cdnUrl?.toUrl(),
					"DM"
				) {
					content = event.new.content.value.orEmpty()
					embed {
						title = I18n.Transmission.Update.title.contextTranslate()
						description = event.new.content.value.orEmpty()
					}
				}
			}
		}

		event<MessageDeleteEvent> {
			check {
				noGuild()
				isNotBot()
				failIf { event.message == null }
			}
			action {
				val oldMessage = event.message?.let {
					MessageChannelBehavior(dmChannelId, kord).getCorrespondingMessage(it)
				}
				if (oldMessage != null) {
					val webhook = bot.getWebhook(dmChannelId, "DM")
					// Keep the NPE here, we want a loud error if it's null
					@Suppress("UnsafeCallOnNullableType")
					webhook.deleteMessage(webhook.token!!, oldMessage.id)
				}
			}
		}

		ephemeralSlashCommand(::StartGameArguments) {
			name = I18n.StartGame.Command.name
			description = I18n.StartGame.Command.description
			action {
				this@Base.kord.editPresence {
					gameMode(gameRegistry.getGameMode(arguments.game))
				}
				respond {
					content = I18n.StartGame.Response.success.contextTranslate(arguments.game)
				}
			}
		}

		ephemeralSlashCommand {
			name = I18n.StopGame.Command.name
			description = I18n.StopGame.Command.description
			action {
				val bKord = this@Base.kord
				bKord.editPresence {
					gameMode(null)
				}
				respond {
					content = I18n.StopGame.Response.success.contextTranslate()
				}
			}
		}

        ephemeralSlashCommand(::ExportArguments) {
            name = I18n.ChatExport.Command.name
            description = I18n.ChatExport.Command.description

            adminOnly {
                val channel = arguments.channel as MessageChannelBehavior? ?: channel
				val start = arguments.start
				val end = arguments.end
				val count = arguments.count

				val messages = when {
					start != null && end != null -> channel
						.getMessagesAfter(start)
						.takeWhile { message -> message.id != end }
					start != null && count != null -> channel
						.getMessagesAfter(start)
						.take(count)
					else -> channel.getMessagesBefore(
						channel.asChannel().lastMessageId ?: Snowflake.max,
						arguments.count
					)
				}
                respond {
                    content = I18n.ChatExport.Response.Success.txt.contextTranslate()
                    when (arguments.format.parsed) {
                        Format.TXT -> addStringExport(channel, guild, messages, arguments.anonymous)
                        Format.HTML -> addHtmlExport(channel, guild, messages, arguments)
                    }
                }
            }
        }
	}

	/**
	 * Arguments for the start game command.
	 * This class defines the arguments required to start a game.
	 * It includes a game argument to specify the game mode.
	 *
	 * @property game The game mode to start. Loaded from the game registry.
	 */
	inner class StartGameArguments : Arguments() {
		val game by string {
			name = I18n.StartGame.Argument.Game.name
			description = I18n.StartGame.Argument.Game.description
			validate {
				failIf(I18n.StartGame.Argument.Game.Error.notFound) { !gameRegistry.hasGameMode(value) }
			}
			autoComplete {
				val effectiveLocale = (locale ?: guildLocale)?.asJavaLocale() ?: KI18n.defaultLocale
				suggestStringMap(gameRegistry.toChoices().mapKeys {
					entry -> entry.key.translateLocale(effectiveLocale)
				})
			}
		}
	}

    class ExportArguments : Arguments() {
        val count: Int? by optionalInt {
            name = I18n.ChatExport.Argument.Count.name
            description = I18n.ChatExport.Argument.Count.description
            minValue = 1
            maxValue = MAX_EXPORTABLE_MESSAGE
            validate {
                if (value != null && end != null) fail(I18n.ChatExport.Argument.Count.Error.mutuallyExclusive)
            }
        }

        val start: Snowflake? by optionalSnowflake {
            name = I18n.ChatExport.Argument.Start.name
            description = I18n.ChatExport.Argument.Start.description
        }

        val end: Snowflake? by optionalSnowflake {
            name = I18n.ChatExport.Argument.End.name
            description = I18n.ChatExport.Argument.End.description
			validate {
				val value = value ?: return@validate
				val start = start ?: return@validate fail(I18n.ChatExport.Argument.End.Error.startRequired)
				if (value < start) fail(I18n.ChatExport.Argument.End.Error.invalidRange)
			}
        }

        val channel by optionalChannel {
            name = I18n.ChatExport.Argument.Channel.name
            description = I18n.ChatExport.Argument.Channel.description
        }

        val format = defaultingEnumChoice<Format> {
            name = I18n.ChatExport.Argument.Format.name
            description = I18n.ChatExport.Argument.Format.description
            typeName = I18n.ChatExport.Argument.Format.typeName
            defaultValue = Format.TXT
        }

        val anonymous by defaultingBoolean {
            name = I18n.ChatExport.Argument.Anonymous.name
            description = I18n.ChatExport.Argument.Anonymous.description
            defaultValue = false
        }
    }
}

enum class Format(override val readableName: Key): ChoiceEnum {
    TXT(I18n.ChatExport.Argument.Format.txt),
	HTML(I18n.ChatExport.Argument.Format.html)
}
