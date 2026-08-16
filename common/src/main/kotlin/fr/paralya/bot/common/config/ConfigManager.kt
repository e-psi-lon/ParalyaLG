package fr.paralya.bot.common.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.builders.ExtensibleBotBuilder
import dev.kordex.core.koin.KordExKoinComponent
import dev.kordex.core.utils.loadModule
import fr.paralya.bot.common.InternalBotApi
import fr.paralya.bot.common.orUnknownClass
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import kotlinx.serialization.serializer
import org.koin.core.module.Module
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Configuration manager for the bot.
 * Handles the lifecycle of the bot's configuration(s) including their respective access mediums.
 * Allows for dynamic registration of game-specific configurations.
 *
 * @property configFile The file where the configuration is stored.
 * @property botConfig The core bot configuration.
 * @constructor Creates a new [ConfigManager] instance and automatically populates the base configuration.
 */
@OptIn(ExperimentalSerializationApi::class)
class ConfigManager internal constructor(private val configFile: Path) : KordExKoinComponent {
	private val logger = KotlinLogging.logger("ConfigManager")


	private var state: ConfigState = try {
		loadState()
	} catch (e: ConfigException) {
		logger.error(e) { "Failed to load config file on startup, cannot continue" }
		throw e
	}

	private val configs = mutableMapOf<String, ConfigEntry>()
	private var hasBootstrapped = false


	// Core bot configuration, directly integrated into the ConfigManager
	val botConfig: BotConfig
		get() = state.botConfig.toPublic()

	constructor() : this(Path(System.getenv("PARALYA_BOT_CONFIG_FILE") ?: "config.conf"))

	/**
	 * Loads the configuration file into a [ConfigState] object
	 * If the file does not exist, it creates a default configuration file
	 * It also validates the given configuration
	 */
	private fun loadState(): ConfigState {
		if (!configFile.exists()) createDefaultConfig()
		val raw = ConfigFactory.parseFile(configFile.toFile())
		val botConfig = Hocon.decodeFromConfig<PrivateBotConfig>(getSubConfig("bot", raw))
		validateConfig(botConfig)
		return ConfigState(raw, botConfig)
	}

	fun reload() {
		state = try {
			loadState()
		} catch (e: ConfigException) {
			logger.warn(e) { "Failed to reload config file, keeping previous configuration" }
			state
		}
		@Suppress("TooGenericExceptionCaught")
		for ((name, configEntry) in configs) try {
			unregisterConfig(name)
			configEntry.configRegister(configEntry)
			logger.debug { "Config for $name reloaded successfully" }
		} catch (e: Exception) { // Many failure point with the same result handling
			logger.error(e) { "Failed to reload config for $name, config may be unavailable" }
		}
	}

	/**
	 * Creates a default configuration file with placeholders for required values.
	 * This method is called if the config file does not exist.
	 */
	private fun createDefaultConfig() {
		configFile.writeText(
			"""
			|bot {
			|    token = ""
			|    admins = []
			|    dmLogChannelId = 0
			|    paralyaId = 0
			|}
			|
			|# Game-specific configurations will be added here
			|games {
			|}
        	""".trimMargin()
		)
		logger.error { "Default config created at ${configFile.absolutePathString()}. Please fill in required values." }
		throw MissingConfigException()
	}

	/**
	 * Registers a game-specific configuration.
	 * This method allows for dynamic registration of configurations for different games.
	 * It uses Koin to manage the lifecycle of the configuration object and is completely type-safe.
	 *
	 * @param name The name of the configuration, used as a key in the config file.
	 */
	@PublishedApi
	internal fun <T : ValidatedConfig> registerConfigInternal(
		name: String,
		className: String?,
		serializer: KSerializer<T>,
		moduleBuilder: Module.(T) -> Unit
	) {
		val configObject = getConfigObject(serializer, className, name) ?: return
		val module = loadModule(true) { moduleBuilder(configObject) }
		configs[name] = ConfigEntry(module) { configEntry ->
			val newObject = getConfigObject(serializer, className, name) ?: return@ConfigEntry
			val newModule = loadModule(true) { moduleBuilder(newObject) }
			configs[name] = ConfigEntry(newModule, configEntry.configRegister)
		}
		logger.debug { "Config for $name registered successfully" }
	}

	@PublishedApi
	internal inline fun <reified T : ValidatedConfig> registerConfig(name: String) {
		registerConfigInternal(name, T::class.simpleName, serializer<T>()) { config ->
			single<T> { config } withOptions { named(name) }
		}
	}

	internal fun unregisterConfig(name: String) {
		configs.remove(name)?.let { (module, _) ->
			getKoin().unloadModules(listOf(module))
			logger.debug { "Koin module for $name unloaded successfully" }
		} ?: logger.warn { "No Koin module found for $name" }
	}

	private fun <T : ValidatedConfig> getConfigObject(serializer: KSerializer<T>, className: String?, name: String): T? {
		val actualName = name.lowercase()
        logger.debug {
			"Registering config for $name at path games.$actualName with datatype ${className.orUnknownClass()}"
		}
        val configObject = try {
            Hocon.decodeFromConfig(serializer, getSubConfig("games.$actualName"))
        } catch (e: IllegalArgumentException) {
            logger.error(e) {
                "Failed to find the configuration for name $name at path games.$actualName. " +
						"Please ensure it exists in the config file."
            }
            return null
        }
        validateConfig(configObject)
        return configObject
    }

	private fun getSubConfig(path: String, config: Config = state.raw): Config {
		if (!config.hasPath(path))
			throw MissingConfigEntryException(path)
		return config.getConfig(path)
	}

	private fun validateConfig(config: ValidatedConfig) {
		val result = config.validate()
		if (result.isValid) logger.info { "Configuration for ${config::class.simpleName.orUnknownClass()} is valid." }
		else throw InvalidConfigException(config::class.simpleName, result.errors)
	}

	private data class ConfigState(
		val raw: Config,
		val botConfig: PrivateBotConfig
	)

	private data class ConfigEntry(
		val module: Module,
		val configRegister: (ConfigEntry) -> Unit
	)



	/**
	 * Bootstraps the bot with the provided configuration.
	 *
	 * This method is made public for cross-module boundary calls between the common module and the bot entrypoint as it
	 * is the only legitimate way to use the token.
	 * It should NOT be called directly by any plugin consumer.
	 *
	 * @param builder The configuration builder for the bot.
	 * @return The initialized [ExtensibleBot] instance on first call.
	 *
	 * @throws BotAlreadyBootstrappedException if the bot has already been bootstrapped.
	 */
	@InternalBotApi
	suspend fun bootstrapBot(builder: suspend ExtensibleBotBuilder.() -> Unit): ExtensibleBot {
		if (hasBootstrapped) throw BotAlreadyBootstrappedException()
		hasBootstrapped = true
		// To avoid leaking the token in public API, uses the private state's token
		return ExtensibleBot(state.botConfig.token) {
			builder()
		}
	}
}
