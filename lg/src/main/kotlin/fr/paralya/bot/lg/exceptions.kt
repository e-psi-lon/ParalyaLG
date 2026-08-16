package fr.paralya.bot.lg

import fr.paralya.bot.common.plugins.PluginException
import fr.paralya.bot.common.plugins.getPluginInstance

private val pluginId by lazy { getPluginInstance<LgPlugin>().pluginId }
open class LgException(message: String) : PluginException(message, pluginId)

class LgChannelNotFoundException(message: String) : LgException(message)
