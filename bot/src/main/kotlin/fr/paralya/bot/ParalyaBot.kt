package fr.paralya.bot

import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.embed
import dev.kordex.core.ExtensibleBot
import dev.kordex.i18n.I18n as KI18n
import dev.kordex.core.utils.loadModule
import dev.kordex.core.plugins.PluginManager as KordExPluginManager
import fr.paralya.bot.common.GameRegistry
import fr.paralya.bot.common.plugins.PluginManager
import fr.paralya.bot.common.config.ConfigManager
import fr.paralya.bot.common.gameMode
import fr.paralya.bot.extensions.base.Base
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import dev.kordex.core.DISCORD_RED
import dev.kordex.core.annotations.warnings.ReplacingDefaultErrorResponseBuilder
import dev.kordex.i18n.generated.CoreTranslations
import fr.paralya.bot.common.InternalBotApi
import fr.paralya.bot.extensions.Monitoring
import fr.paralya.bot.extensions.plugins.PluginExtension
import org.slf4j.LoggerFactory
import java.util.Locale

internal val botDeveloper = System.getenv("BOT_DEVELOPER_ID").toULong()

/**
 * Main entry point for the Paralya's Discord bot.
 */
suspend fun main(args: Array<String>) {
	val bot = buildBot(args)
	bot.start()
}

/**
 * Configures the logging level based on the development mode.
 */
private fun configureLogging(devMode: Boolean) {
	val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
	rootLogger.level = if (devMode) Level.DEBUG else Level.INFO
}


/**
 * Builds and configures the bot instance.
 *
 * @param args Command line arguments for the bot.
 * @return An instance of [ExtensibleBot], ready to be started.
 */
@OptIn(ReplacingDefaultErrorResponseBuilder::class, InternalBotApi::class)
suspend fun buildBot(args: Array<String>): ExtensibleBot {
	val devModeArg = args.contains("--dev")
	configureLogging(devModeArg) // Initial logger setup
	val configManager = ConfigManager() // Bootstrap to get the token
	return configManager.bootstrapBot {
		// Add another option to enable dev mode; the CLI flag
		// KordEx also provides env vars to set it (see the doc)
		devMode = devMode || devModeArg
		configureLogging(devMode) // Reconfigure logging after the "true" dev mode is set
		val logger = KotlinLogging.logger("ParalyaBot")
		logger.info { "Starting bot in ${if (devMode) "development" else "production"} mode" }
		extensions {
			sentry {
				enable = false
			}
			add(::Base)
			add(::PluginExtension)
			add(::Monitoring)
			help {
				enableBundledExtension = false
			}
		}

		plugins {
			manager = ::PluginManager
			pluginPaths.clear() // We only need one single path, not the default one
			pluginPath(System.getenv("PARALYA_BOT_PLUGINS_DIR") ?: "./plugins")
		}

		// Some privileged intents are required for the bot to function properly
		@OptIn(PrivilegedIntent::class)
		intents {
			+Intent.GuildMembers
			+Intent.MessageContent
			+Intent.DirectMessages
		}

		// Configure internationalization with French as the default language
		i18n {
			KI18n.defaultLocale = Locale.FRENCH
			applicationCommandLocale(Locale.FRENCH)
			interactionUserLocaleResolver()
			interactionGuildLocaleResolver()
		}

		members { all() }

		presence { gameMode(null) }

		errorResponse { message, type ->
			val locale = message.locale ?: KI18n.defaultLocale
			embed {
				title = I18n.Error.title.translateLocale(locale)
				description = if (message.bundle != CoreTranslations.bundle) message.translateLocale(locale)
				else I18n.Error.description.translateLocale(locale, message, type.error::class.simpleName)
				color = DISCORD_RED
			}
		}

		hooks {
			beforeKoinSetup {
				loadModule {
                    single<ConfigManager> { configManager } withOptions {
						named("configManager")
						createdAtStart()
					}
					singleOf(::GameRegistry) withOptions {
						named("registry")
						createdAtStart()
					}
				}
			}

			afterKoinSetup {
				loadModule {
					// To expose our custom manager for injection, we need to re-register it
					// on top of what `manager` already does in the plugin block
					// as KordEx only bind its own type
					single<PluginManager> { get<KordExPluginManager>() as PluginManager }
				}
			}
		}
	}
}
