package fr.paralya.bot.common.plugins

import fr.paralya.bot.common.ParalyaBotException

open class PluginException(message: String, val pluginId: String) : ParalyaBotException(message)

open class PluginLoadingException(message: String, pluginId: String) : PluginException(message, pluginId)
class PluginFailedToLoad(message: String, pluginId: String) : PluginLoadingException(message, pluginId)
open class PluginValidationException(message: String, pluginId: String) : PluginException(message, pluginId)
class PluginInvalidVersionException(message: String, pluginId: String, val version: String) :
    PluginValidationException(message, pluginId)

class PluginConfigurationException(message: String, pluginId: String) : PluginException(message, pluginId)
