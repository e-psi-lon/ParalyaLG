package fr.paralya.bot.common

import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.builder.PresenceBuilder
import dev.kordex.i18n.Key
import dev.kordex.i18n.I18n as KI18n
import dev.kordex.core.koin.KordExKoinComponent

/**
 * [GameRegistry] is a singleton class that manages game modes for the bot.
 * It allows for the registration, retrieval, and unloading of game modes.
 *
 * @property gameModes A mutable map that stores game modes with their associated keys.
 */
class GameRegistry : KordExKoinComponent {
	private val gameModes = mutableMapOf<String, Key>()

	/**
	 * Registers a new game mode with the given key and value.
	 *
	 * @param key The translation key for the game mode.
	 * @param gameMode The name of the game mode.
	 */
	@PublishedApi
	internal fun registerGameMode(key: Key, gameMode: String) {
		gameModes.entries.removeIf { it.value == key }
		gameModes[gameMode] = key
	}

	/**
	 * Retrieves the game mode associated with the given value.
	 *
	 * @param value The name of the game mode.
	 * @return A pair containing the key and value of the game mode, or NONE if not found.
	 */
	fun getGameMode(value: String) = gameModes[value]?.let { it to value }

	/**
	 * Retrieves all registered game modes.
	 *
	 * @return A map containing all game modes with their associated keys.
	 */
	fun toChoices(): MutableMap<Key, String> = gameModes.entries.associate { it.value to it.key }.toMutableMap()

	/**
	 * Unloads a game mode by removing it from the registry.
	 *
	 * @param value The name of the game mode to unload.
	 */
	@PublishedApi
	internal fun unloadGameMode(value: String) {
		gameModes.remove(value)
	}

	fun hasGameMode(value: String) = gameModes.containsKey(value)
}

/**
 * Extension function to set the game mode in a PresenceBuilder.
 *
 * @param gameMode A pair containing the key and value of the game mode.
 */
fun PresenceBuilder.gameMode(gameMode: Pair<Key, String>?) {
	if (gameMode == null) {
		status = PresenceStatus.Idle
		watching(I18n.Presence.notPlaying.translateLocale(KI18n.defaultLocale))
	} else {
		status = PresenceStatus.Online
		playing(I18n.Presence.playing.translateLocale(KI18n.defaultLocale, gameMode.first))
	}
}
