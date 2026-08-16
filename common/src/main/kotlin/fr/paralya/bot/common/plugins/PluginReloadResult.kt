package fr.paralya.bot.common.plugins

import org.pf4j.PluginRuntimeException

sealed interface PluginReloadResult
data object SuccessfulPluginReload : PluginReloadResult

sealed class PluginReloadError(val exception: Exception?) : PluginReloadResult

data object OldPluginNotFound : PluginReloadError(null)
class OldPluginFailedToDelete(exception: PluginRuntimeException? = null) : PluginReloadError(exception)

class OldPluginReusedAsFallback(exception: Exception) : PluginReloadError(exception)
class OldPluginFallbackFailedToLoad(exception: Exception, val fallbackException: Exception) :
    PluginReloadError(exception)

