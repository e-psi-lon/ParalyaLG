package fr.paralya.bot.lg

import fr.paralya.bot.common.ApiVersion
import fr.paralya.bot.common.CommonModule
import fr.paralya.bot.common.plugins.Plugin
import fr.paralya.bot.lg.data.LgConfig

internal const val BOT_NICKNAME = "ParalyaLG"
internal const val PROFILE_PICTURE = "paralya_lg"
/**
 * The main plugin class for the Werewolf (Loup-Garou) game.
 *
 * This class is responsible for setting up the configuration [LgConfig] and initializing the [LG] extension.
 * It is currently not used because the plugin loader from KordEx is still in development.
 *
 */
@ApiVersion(CommonModule.API_VERSION)
class LgPlugin : Plugin() {
	override val name = "LG"
	override val key = I18n.GameMode.lg

	override fun defineConfig() = define<LgConfig>()

	/**
	 * Setup function that initializes the plugin.
	 */
	override suspend fun onSetup() {
		registerComponent(::VoteManager)
		registerComponent(::LgRelayService)
		extension(::LG)
	}
}
