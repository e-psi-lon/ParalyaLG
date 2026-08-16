package fr.paralya.bot.lg

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.entity.PermissionOverwrite
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kord.core.entity.effectiveName
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.role
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ExtensionState
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.dm
import dev.kordex.core.utils.hasRole
import fr.paralya.bot.common.I18n.Messages
import fr.paralya.bot.common.adminOnly
import fr.paralya.bot.common.contextTranslate
import fr.paralya.bot.common.getAsset
import fr.paralya.bot.common.sendAsWebhook
import fr.paralya.bot.common.snowflake
import fr.paralya.bot.common.MessageForm
import fr.paralya.bot.common.runCatchingException
import fr.paralya.bot.lg.data.LgChannelType
import fr.paralya.bot.lg.data.LgConfig
import fr.paralya.bot.lg.data.getGameData
import fr.paralya.bot.lg.I18n as Lg
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.koin.core.component.get
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The main extension class for the Werewolf (Loup-Garou) game.
 *
 * This class is responsible for registering commands, event listeners and managing game state.
 *
 *
 * @property name The name of the extension.
 * @property logger The logger for the extension.
 * @property botCache An alias for the bot's cache for easier access.
 * @property pluginRef A reference to the plugin instance.
 */
class LG : Extension() {
	override val name = "LG"
	val logger = KotlinLogging.logger(this::class.java.name)
	val botCache by lazy { kord.cache }
	val pluginRef by inject<LgPlugin>()

	@Suppress("ForbiddenComment")
	// TODO: Add this to a parent class to avoid repetition with other extensions
	override suspend fun setState(state: ExtensionState) {
		try {
			@Suppress("MagicNumber")
			withTimeout(1000.milliseconds) {
				super.setState(state)
			}
		} catch (timeout: TimeoutCancellationException) {
			logger.warn(timeout) {
				"KordEx's default setState hangs at boot because of an uninitialized Flow. " +
						"This timeout allows the rest of the setup to continue"
			}
			this.state = state
		}
	}

	override suspend fun setup() {
		publicSlashCommand {
			name = Lg.Command.name
			description = Lg.Command.description

			registerVotingCommands()
			registerDayCycleCommands()

			ephemeralSubCommand(::NotifArguments, ::MessageForm) {
				name = Lg.Notif.Command.name
				description = Lg.Notif.Command.description
				action { modal ->
					if (guild == null) {
						respond { content = Messages.Error.onlyInGuild.contextTranslate() }
						return@action
					}

					val failed = mutableListOf<String>()
					try {
						guild?.members?.collect { member ->
							if (member.hasRole(arguments.role)) runCatchingException {
								member.dm(
									Lg.Notif.Content.main.contextTranslate(
										modal?.message?.value ?: Lg.Notif.Content.error
									)
								)
							}.onFailure { failed.add(member.username) }
								.onSuccess { if (it == null) failed.add(member.username) }
						}
					} catch (e: CancellationException) {
						throw e
					} catch (e: IllegalStateException) {
						respond { content = Lg.Notif.Response.failed.contextTranslate(e.message) }
					}

					respond {
						content = if (failed.isEmpty()) {
							Lg.Notif.Response.success.contextTranslate()
						} else {
							Lg.Notif.Response.failed.contextTranslate(failed.joinToString(", "))
						}
					}
				}
			}

			ephemeralSubCommand(::InterviewArguments) {
				name = Lg.Interview.Command.name
				description = Lg.Interview.Command.description
				adminOnly {
					val guild = guild ?: return@adminOnly
					val interviewChannel =
						guild.getChannel(LgChannelType.INTERVIEW.toId()) as TopGuildChannel
					val user = arguments.user
					interviewChannel.addOverwrite(
						PermissionOverwrite.forMember(user.id, Permissions(Permission.SendMessages))
					)
				}
			}

			ephemeralSubCommand(::EndDayArguments) {
				name = Lg.EndDay.Command.name
				description = Lg.EndDay.Command.description
				adminOnly {
					val hour = arguments.hour
					val day = arguments.day ?: botCache.getGameData().phase.number
					val config = get<LgConfig>()

					LgChannelType.ANNONCES_VILLAGE.toId().sendAsWebhook(
						bot,
						BOT_NICKNAME,
						pluginRef.getAsset(PROFILE_PICTURE)
					) {
						content = Lg.EndDay.Response.infoMessage.contextTranslate(day, hour, config.aliveRole)
					}
					respond {
						content = Lg.EndDay.Response.success.contextTranslate(
							day,
							hour
						)
					}
				}
			}

            ephemeralSubCommand(::KillArguments) {
                name = Lg.Kill.Command.name
                description = Lg.Kill.Command.description

                adminOnly {
					val guild = guild ?: return@adminOnly
                    val target = arguments.target
                    val reason = arguments.reason ?: Lg.Kill.Argument.Reason.default.contextTranslate()
                    val config = get<LgConfig>()

                    guild.getMember(target.id).swapRoles(
                        addRoleId = config.deadRole.snowflake,
                        removeRoleId = config.aliveRole.snowflake,
                        reason = reason
                    )
                    respond {
                        content = Lg.Kill.Response.success.contextTranslate(target.effectiveName, reason)
                    }
                }
            }

		}

		registerListeners()
	}

	/**
	 * Nested class to define /lg notif command arguments.
	 *
	 * @property role The role to notify.
	 */
	class NotifArguments : Arguments() {
		val role by role {
			name = Lg.Notif.Argument.Role.name
			description = Lg.Notif.Argument.Role.description
		}
	}

	/**
	 * Nested class to define /lg interview command arguments.
	 *
	 * @property user The user to be interviewed.
	 */
	class InterviewArguments : Arguments() {
		val user by user {
			name = Lg.Interview.Argument.User.name
			description = Lg.Interview.Argument.User.description
		}
	}

	class EndDayArguments : Arguments() {
		val hour by string {
			name = Lg.EndDay.Argument.Hour.name
			description = Lg.EndDay.Argument.Hour.description
		}

		val day by optionalInt {
			name = Lg.EndDay.Argument.Day.name
			description = Lg.EndDay.Argument.Day.description
		}
	}

    class KillArguments : Arguments() {
        val target by user {
            name = Lg.Kill.Argument.Target.name
            description = Lg.Kill.Argument.Target.description
        }

        val reason by optionalString {
            name = Lg.Kill.Argument.Reason.name
            description = Lg.Kill.Argument.Reason.description
        }
    }
}
