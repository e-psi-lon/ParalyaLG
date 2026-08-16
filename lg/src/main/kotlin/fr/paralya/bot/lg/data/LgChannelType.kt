package fr.paralya.bot.lg.data

import dev.kordex.core.extensions.Extension
import fr.paralya.bot.common.cache.CacheException

/**
 * Enum class for a typesafe accessor of channels in the game.
 */
enum class LgChannelType {
	// Village channels
	ANNONCES_VILLAGE,
	SUJETS,
	VILLAGE,
	VOTES,

	// Special roles channels
	CORBEAU,
	INTERVIEW,
	PETITE_FILLE,
	CUPIDON,
	DATE_MYSTERE,

	// Werewolf channels
	LOUPS_CHAT,
	LOUPS_VOTE;

	context(extension: Extension)
    internal suspend fun toId() = extension.kord.cache.getChannelId(this) ?:
		throw CacheException("Channel ID for $this not found in cache", element  = this.name)
}
