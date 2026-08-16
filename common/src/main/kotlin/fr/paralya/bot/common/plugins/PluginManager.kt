package fr.paralya.bot.common.plugins

import dev.kordex.core.ExtensibleBot
import dev.kordex.core.koin.KordExKoinComponent
import dev.kordex.core.utils.loadModule
import fr.paralya.bot.common.ApiVersion
import fr.paralya.bot.common.CommonModule
import fr.paralya.bot.common.runCatchingException
import fr.paralya.bot.common.runCatchingTypedException
import dev.kordex.core.plugins.PluginManager as KordExPluginManager
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.pf4j.PluginRuntimeException
import org.pf4j.PluginState
import org.pf4j.PluginStateEvent
import org.pf4j.PluginStateListener
import org.pf4j.PluginWrapper
import java.nio.file.Path
import kotlin.reflect.KClass

class PluginManager(roots: List<Path>, enabled: Boolean) : KordExPluginManager(roots, enabled), KordExKoinComponent {
    init {
        addPluginStateListener(PluginListener())
    }

    private val koinModules: MutableMap<String, Module> = mutableMapOf()

    @Suppress("ThrowsCount")
    internal fun validatePlugin(wrapper: PluginWrapper, pluginPath: Path) {
        val classLoader = wrapper.pluginClassLoader
        val pluginClass = classLoader.loadClass(wrapper.descriptor.pluginClass)
        val pluginId = wrapper.pluginId
        logger.debug {
            "Verifying validity of the plugin at path $pluginPath with main class ${wrapper.descriptor.pluginClass}"
        }
        if (!Plugin::class.java.isAssignableFrom(pluginClass)) {
            throw PluginValidationException("Plugin $pluginId does not extend Plugin", pluginId)
        }

        val annotation = pluginClass.getAnnotation(ApiVersion::class.java) ?:
        throw PluginValidationException(
            "Plugin $pluginId is missing @ApiVersion annotation",
            pluginId
        )

        if (!versionManager.checkVersionConstraint(
                annotation.version, ">=${CommonModule.MIN_COMPATIBLE_VERSION}"
            )) throw PluginInvalidVersionException("Plugin $pluginId is not compatible with the current API version. " +
                "Minimum compatible version: ${CommonModule.MIN_COMPATIBLE_VERSION}", pluginId, annotation.version)

        if (!versionManager.checkVersionConstraint(
                annotation.version, "<=${CommonModule.API_VERSION}"
            )) throw PluginInvalidVersionException("Plugin $pluginId requires API version ${annotation.version}, " +
                "but the current API version is ${CommonModule.API_VERSION}", pluginId, annotation.version)

    }


    override fun loadPluginFromPath(pluginPath: Path): PluginWrapper {
        val wrapper = super.loadPluginFromPath(pluginPath)
        validatePlugin(wrapper, pluginPath)
        val plugin = wrapper.plugin as? Plugin
            ?: throw PluginLoadingException("The plugin at path $pluginPath is not a Plugin despite " +
                    "passing validation.", pluginId = wrapper.pluginId)
        val module = loadModule {
            @Suppress("UNCHECKED_CAST")
            single { plugin } bind plugin::class as KClass<Plugin>
        }
        koinModules[wrapper.pluginId] = module
        return wrapper
    }


    /**
     * Reloads a plugin.
     *
     * @param pluginId The ID of the plugin to be reloaded.
     * @param newPath The new path to the plugin. If null, the plugin will be reloaded from its current path.
     */
    fun reloadPlugin(pluginId: String, newPath: Path? = null): PluginReloadResult {
        val plugin = getPlugin(pluginId) ?: return OldPluginNotFound
        val pluginPath = plugin.pluginPath!! // PluginWrapper requires a path in its constructor
        val fullPath = if (pluginPath.isAbsolute) pluginPath else pluginsRoot.resolve(pluginPath)
        val reloadStrategy = createReloadStrategy(
            pluginId,
            oldPluginPath = fullPath,
            newPluginZipPath = newPath ?: fullPath,
            logger
        )
        return reloadStrategy.reload()
    }

    @Suppress("ForbiddenComment", "UnusedPrivateFunction") // Already tracked
    // TODO: Consolidate handling and expose to public API
    private fun reloadPlugins() {
        unloadPlugins()
        loadPlugins()
        startPlugins()
    }

    fun tryStopPlugin(pluginId: String) = runCatchingTypedException<PluginRuntimeException, _> {
        // Non-nullable enum
        // And if PF4J changes, we want to get a failure not a success holding null
        stopPlugin(pluginId)!!
    }

    fun tryLoadAndStartPlugin(pluginPath: Path) = runCatchingException {
        val fullPath = if (pluginPath.isAbsolute) pluginPath else pluginsRoot.resolve(pluginPath)
        val pluginId = plugins.entries.firstOrNull { it.value.pluginPath == fullPath }?.key
            ?: loadPlugin(fullPath)
        // Non-nullable enum
        // And if PF4J changes, we want to get a failure not a success holding null
        startPlugin(pluginId)!!
    }

    override fun stopPlugin(pluginId: String, stopDependent: Boolean): PluginState? {
        if (plugins.containsKey(pluginId) && checkPluginState(pluginId, PluginState.STARTED)) {
            koinModules[pluginId]
                ?.let { module ->
                    unloadKoinModules(module)
                    koinModules.remove(pluginId)
                }
                ?: logger.error {
                    "Plugin $pluginId not found in Koin modules despite the plugin being a started plugin."
                }
        }
        return super.stopPlugin(pluginId, stopDependent)
    }

    private inner class PluginListener : PluginStateListener, KordExKoinComponent {
        override fun pluginStateChanged(event: PluginStateEvent?) {
            event ?: return
            val bot = getKoin().getOrNull<ExtensibleBot>()
            if (bot == null) {
                logger.debug { "Plugin state changed before initialization of the bot itself" }
                return
            }
            logger.info { "Plugin ${event.plugin.pluginId} changed state to ${event.pluginState}" }
            if (event.pluginState == PluginState.STARTED) bot.kordRef.launch {
                bot.send(PluginReadyEvent(
                    event.plugin.pluginId, // Access to pluginState already required a non-null `plugin`
                    bot.kordRef.guilds.toSet()
                ))
            }
        }
    }
}
