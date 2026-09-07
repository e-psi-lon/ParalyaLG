package fr.paralya.bot.ai

import fr.paralya.bot.common.ApiVersion
import fr.paralya.bot.common.CommonModule
import fr.paralya.bot.common.plugins.Plugin

internal const val BOT_NICKNAME = "ParalyaAsk"
internal const val PROFILE_PICTURE = "paralya_ai"

@ApiVersion(CommonModule.API_VERSION)
class AiPlugin : Plugin() {
	override val name = "AI"
	override val key = I18n.Plugin.key

	override fun defineConfig() = define<AiHelpConfig>()

	/**
	 * Setup function that initializes the plugin.
	 */
	override suspend fun onSetup() {
		extension(::AiHelp)
	}
}
