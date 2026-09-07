package fr.paralya.bot.common.plugins

import dev.kordex.core.koin.KordExKoinComponent
import dev.kordex.core.plugins.KordExPlugin
import dev.kordex.i18n.Key
import fr.paralya.bot.common.GameRegistry
import fr.paralya.bot.common.config.ConfigManager
import fr.paralya.bot.common.config.ValidatedConfig
import fr.paralya.bot.common.orUnknownClass
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.pf4j.PluginWrapper
import kotlin.getValue
import kotlin.lazy

abstract class Plugin: KordExPlugin() {
    abstract val name: String
    abstract val key: Key
    @PublishedApi
    internal val components = mutableListOf<Module>()


    @PublishedApi
    internal val configManager by inject<ConfigManager>()
    private val pluginManager by inject<PluginManager>()

    val pluginWrapper: PluginWrapper? by lazy {
        pluginManager.whichPlugin(this::class.java)
    }

    val pluginId: String by lazy {
        pluginWrapper?.pluginId ?:
            error("Plugin ${this::class.simpleName.orUnknownClass()} identifier couldn't be found.")
    }

    val version: String by lazy {
        pluginWrapper?.descriptor?.version ?:
            error("Plugin ${this::class.simpleName.orUnknownClass()} version couldn't be found.")
    }

    override suspend fun setup() {
        prepareRegistration()
        onSetup()
        try {
            getKoin().loadModules(components)
        } catch (_: IllegalStateException) {
            bot.logger.info { "Koin not started, loading $name components after Koin setup" }
            settings {
                hooks { afterKoinSetup { getKoin().loadModules(components) } }
            }
        }
    }

    protected inline fun <reified T : KordExKoinComponent> registerComponent(noinline constructor: () -> T) {
        val module = module {
            singleOf<T>(constructor) {
                createdAtStart()
            }
        }
        components.add(module)
    }

    protected inline fun <reified T: KordExKoinComponent> registerComponent(component: T) {
        val module = module {
            single<T> { component }
        }
        components.add(module)
    }

    /**
     * Register your plugin's config type by calling `define<T>()`.
     */
    protected abstract fun defineConfig() : ConfigDefinition


    open suspend fun onSetup() {}
    open suspend fun onDelete() {}
    open suspend fun onStop() {}

    override fun delete() = runBlocking {
        kord.launch {
            removeAllRegistration()
            onDelete()
        }.join()
    }.also { super.delete() }

    override fun stop() = runBlocking {
        kord.launch {
            removeAllRegistration()
            onStop()
        }.join()
    }.also { super.stop() }

    private fun removeAllRegistration() {
        configManager.unregisterConfig(name)
        extraInternalUnregistration()
        getKoin().unloadModules(components)
    }

    /**
     * Perform the registration of the config type.
     * As the only way to obtain the required [ConfigDefinition] instance,
     * you must call it in [defineConfig]
     */
    protected inline fun <reified T : ValidatedConfig> define() : ConfigDefinition {
        // This function is seemingly doing a lot of work, but
        // It is necessary to avoid too many indirections
        configManager.registerConfig<T>(name)
        extraInternalRegistration()
        return ConfigDefinition()
    }

    @PublishedApi
    internal open fun extraInternalRegistration() {}
    internal open fun extraInternalUnregistration() {}



    private fun prepareRegistration() {
        try {
            getKoin()
        } catch (_: IllegalStateException) {
            bot.logger.info { "Koin not started, registering $name config and game hook after Koin setup" }
            settings {
                hooks { afterKoinSetup { defineConfig() } }
            }
            return
        }
        bot.logger.info { "Koin already started, registering $name config and game" }
        defineConfig()
    }

    class ConfigDefinition @PublishedApi internal constructor() // Marker class to force subclasses to call define<T>()
}


abstract class GamePlugin: Plugin() {
    private val gameRegistry by inject<GameRegistry>()

    final override fun extraInternalRegistration() {
        gameRegistry.registerGameMode(key, name)
    }

    final override fun extraInternalUnregistration() {
        gameRegistry.unloadGameMode(name)
    }
}