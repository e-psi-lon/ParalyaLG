package fr.paralya.bot.lg.data

import dev.kord.cache.api.data.description
import dev.kord.common.entity.Snowflake
import fr.paralya.bot.lg.data.GamePhase.PhaseType
import kotlinx.serialization.Serializable
import kotlin.collections.plus

typealias Voter = Snowflake
typealias Target = Snowflake

/**
 * Represents voting data for a Loup-Garou (Werewolf) game.
 *
 * @property id The unique identifier for this vote session
 * @property type The type of vote, corresponding to a game phase (e.g., DAY or NIGHT)
 * @property isCurrent Flag indicating if this vote is the current active vote
 * @property votes Map of voter IDs to their voted target IDs
 * @property choices List of possible choices/targets for this vote
 * @property corbeau Special "Raven" vote, used for the Corbeau role's additional vote
 */
@Serializable
data class VoteData(
	val id: Snowflake,
	val type: PhaseType,
	val isCurrent: Boolean = false,
	val votes: Map<Voter, Target> = emptyMap(),
	val choices: List<Target> = listOf(),
	val corbeau: Target? = null,
) {
	companion object {
		/**
		 * Creates a new vote for the specified phase type.
		 *
		 * @param id The unique identifier for this vote
		 * @param isCurrent Whether this vote should be marked as current
		 * @param type The type of vote to create
		 * @return A new [VoteData] instance for the specified phase type
		 */
		fun createVote(type: PhaseType, id: Snowflake, isCurrent: Boolean = true) = VoteData(id, type, isCurrent)

		val description = description<VoteData, Snowflake>(VoteData::id)
	}
}


/**
 * Records a vote from a voter targeting another player.
 *
 * @param voterId The ID of the player casting the vote
 * @param targetId The ID of the player being voted for
 * @return A new [VoteData] with the updated votes map
 */
fun VoteData.vote(voterId: Voter, targetId: Target) =
	copy(votes = votes + (voterId to targetId))

/**
 * Remove the vote of a voter.
 *
 * @param voterId The ID of the player removing their vote
 * @return A new [VoteData] with the updated votes map
 */
fun VoteData.unvote(voterId: Voter) = copy(votes = votes - voterId)

/**
 * Records a special Corbeau (Raven) vote if this is a day vote.
 * The Corbeau role can place an additional vote on a player.
 *
 * @param targetId The ID of the player receiving the Corbeau's vote
 * @return A new [VoteData] with the updated Corbeau vote, or unchanged if not a day vote
 */
fun VoteData.voteCorbeau(targetId: Target) = if (type == PhaseType.DAY) copy(corbeau = targetId) else this


/**
 * Remove the Corbeau vote.
 *
 * @return A new [VoteData] with the Corbeau vote removed
 */
fun VoteData.unvoteCorbeau() = copy(corbeau = null)

/**
 * Updates the list of valid voting choices/targets.
 *
 * @param choices The list of player IDs that can be voted for
 * @return A new [VoteData] with the updated choices
 */
fun VoteData.setChoices(choices: List<Target>) = copy(choices = choices)

/**
 * Updates the current status of this vote.
 *
 * @param isCurrent Whether this vote is the current active vote
 * @return A new [VoteData] with the updated current status
 */
fun VoteData.setCurrent(isCurrent: Boolean) = copy(isCurrent = isCurrent)
