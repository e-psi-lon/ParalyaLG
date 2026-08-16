@file:Suppress("TooManyFunctions")
package fr.paralya.bot.lg.data

import dev.kord.cache.api.DataCache
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.TextChannel
import dev.kordex.core.commands.application.ApplicationCommandContext
import fr.paralya.bot.common.cache.atomic
import fr.paralya.bot.common.cache.putSerialized
import fr.paralya.bot.common.cache.querySerialized
import fr.paralya.bot.common.cache.removeSerialized
import fr.paralya.bot.common.plugins.getPluginInstance
import fr.paralya.bot.lg.LgPlugin
import kotlinx.serialization.Serializable
import kotlin.collections.plus

/**
 * Represents the game data for the Werewolf game.
 *
 * @property phase The current phase of the game (day or night).
 * @property lastWerewolfMessageSender ID of the last player who sent a message in the werewolf channel.
 * @property isProfilePictureFlipped Used to select which variant of the profile picture to use.
 * @property channels Map of channel types/name to their respective [Snowflake] IDs.
 * @property interviews List of interview channel IDs.
 */
@Serializable
data class GameData(
	val phase: GamePhase = GamePhase.Night(0),
	val lastWerewolfMessageSender: Snowflake? = null,
	val isProfilePictureFlipped: Boolean = false,
	val channels: Map<String, Snowflake> = mapOf(),
	val interviews: List<Snowflake> = listOf()
)

/**
 * Creates a copy of the current game data, advancing to the next day.
 * @return A new [GameData] instance with a phase set to DAY and dayCount incremented.
 */
fun GameData.nextPhase() = copy(phase = phase.next())

/**
 * Registers a channel in the game.
 * @param type The type of channel to register.
 * @param channelId The [Snowflake] ID of the channel.
 * @return A new [GameData] instance with the channel added to the channels map.
 */
fun GameData.registerChannel(type: String, channelId: Snowflake) = copy(channels = channels + (type to channelId))

/**
 * Adds an interview channel to the game.
 * @param interviewId The [Snowflake] ID of the interview channel.
 * @return A new [GameData] instance with the interview added to the interview list.
 */
fun GameData.addInterview(interviewId: Snowflake) = copy(interviews = interviews + interviewId)

/**
 * Removes an interview channel from the game.
 * @param interviewId The [Snowflake] ID of the interview channel to remove.
 * @return A new [GameData] instance with the specified interview removed from the interview list.
 */
fun GameData.removeInterview(interviewId: Snowflake) = copy(interviews = interviews.filter { it != interviewId })

// Cache extension functions

private val pluginNamespace: String by lazy {
	getPluginInstance<LgPlugin>().pluginId
}

// Simpler - just one lazy initialization
/**
 * Retrieves the current game data from the cache or creates a new one if none exists.
 * @return The current [GameData] instance.
 */
suspend fun DataCache.getGameData(): GameData = atomic {
	querySerialized<GameData>(pluginNamespace).singleOrNull() ?: GameData().also {
		putSerialized(pluginNamespace, it)
	}
}

/**
 * Resets the game data by removing existing data and adding a fresh [GameData] instance.
 */
suspend fun DataCache.resetGameData() = atomic {
	removeSerialized<GameData>(pluginNamespace)
	putSerialized(pluginNamespace, GameData())
}

/**
 * Updates the game data using the provided modifier function.
 * Automatically creates new GameData if none exists, or updates existing data.
 * @param modifier A function that transforms the current [GameData] to a new [GameData].
 */
internal suspend inline fun DataCache.updateGameData(modifier: (GameData) -> GameData) = atomic {
	putSerialized(
		pluginNamespace,
		modifier(querySerialized<GameData>(pluginNamespace).singleOrNull() ?: GameData())
	)
}

/**
 * Advances the game to the next phase.
 */
suspend fun DataCache.nextPhase() = updateGameData { it.nextPhase() }

/**
 * Registers a channel in the game data.
 * @param type The type of channel to register.
 * @param channelId The [Snowflake] ID of the channel.
 */
suspend fun DataCache.registerChannel(type: String, channelId: Snowflake) =
	updateGameData { it.registerChannel(type, channelId) }

/**
 * Gets the channel id based on its [type] which is his role in the game.
 */
suspend fun DataCache.getChannelId(type: LgChannelType) = getGameData().channels[type.name]
suspend fun DataCache.getChannelId(type: String) = getGameData().channels[type]

/**
 * Gets the [TextChannel] for a specific channel [type] within an application command context
 * allowing to access to the channel itself.
 */
@Suppress("UnsafeCallOnNullableType") // Is called from guild-command-only context
context(ctx: ApplicationCommandContext)
suspend fun DataCache.getChannel(type: LgChannelType) =
	getChannelId(type)?.let { ctx.guild!!.getChannel(it) as TextChannel }

/**
 * Adds an interview channel to the game data.
 * @param interviewId The [Snowflake] ID of the interview channel.
 */
suspend fun DataCache.addInterview(interviewId: Snowflake) =
	updateGameData { it.addInterview(interviewId) }

/**
 * Gets the list of all interview channel IDs.
 * @return A list of [Snowflake] IDs for interview channels.
 */
suspend fun DataCache.getInterviews() = getGameData().interviews

/**
 * Removes an interview channel from the game data.
 * @param interviewId The [Snowflake] ID of the interview channel to remove.
 */
suspend fun DataCache.removeInterview(interviewId: Snowflake) =
	updateGameData { it.removeInterview(interviewId) }

/**
 * Gets the ID of the last player who sent a message in the werewolf channel.
 * @return [Snowflake] ID of the last werewolf message sender.
 */
suspend fun DataCache.getLastWerewolfMessageSender() =
	getGameData().lastWerewolfMessageSender

/**
 * Sets the ID of the last player who sent a message in the werewolf channel.
 * @param senderId The [Snowflake] ID of the sender.
 */
suspend fun DataCache.setLastWerewolfMessageSender(senderId: Snowflake) =
	updateGameData { it.copy(lastWerewolfMessageSender = senderId) }

/**
 * Toggles the profile picture state in the game data.
 */
suspend fun DataCache.toggleProfilePicture() =
	updateGameData { it.copy(isProfilePictureFlipped = !it.isProfilePictureFlipped) }

/**
 * Gets the current profile picture state.
 * @return Current profile picture boolean state.
 */
suspend fun DataCache.getProfilePictureState() =
	getGameData().isProfilePictureFlipped
