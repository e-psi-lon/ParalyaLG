package fr.paralya.bot.lg

import dev.kord.core.entity.User
import dev.kord.rest.builder.message.embed
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.EphemeralSlashCommandContext
import dev.kordex.core.commands.application.slash.PublicSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.application.slash.group
import dev.kordex.core.commands.converters.impl.optionalInt
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.types.TranslatableContext
import dev.kordex.core.utils.dm
import dev.kordex.i18n.Key
import fr.paralya.bot.common.adminOnly
import fr.paralya.bot.common.contextTranslate
import fr.paralya.bot.common.sendAsWebhook
import fr.paralya.bot.common.snowflake
import fr.paralya.bot.lg.data.GamePhase.PhaseType
import fr.paralya.bot.lg.data.LgChannelType
import fr.paralya.bot.lg.I18n as Lg
import fr.paralya.bot.lg.data.getGameData
import org.koin.core.component.inject

/**
 * Registers the commands related to the voting mechanism in the game
 * This includes commands for voting during the day and night phases.
 * But also the command to retract a vote, shows the vote list and some admin commands.
 *
 * @receiver The instance of the [LG] extension that will handle the commands.
 */
@Suppress("CognitiveComplexMethod", "CyclomaticComplexMethod", "LongMethod")
context(lg: LG)
suspend fun <A : Arguments, M : ModalForm> PublicSlashCommand<A, M>.registerVotingCommands() {
	val voteManager by this.inject<VoteManager>()
	group(Lg.Vote.Command.name) {
		description = Lg.Vote.Command.description
		ephemeralSubCommand(::VoteArguments) {
			name = Lg.Vote.Village.Command.name
			description = Lg.Vote.Village.Command.description
			action {
				handleVote(
					PhaseType.DAY,
					LgChannelType.VOTES,
					arguments.target,
					arguments.reason,
					voteManager,
					true
				)
			}
		}
		ephemeralSubCommand(::VoteArguments) {
			name = Lg.Vote.Werewolf.Command.name
			description = Lg.Vote.Werewolf.Command.description
			action {
				handleVote(
					PhaseType.NIGHT,
					LgChannelType.LOUPS_VOTE,
					arguments.target,
					arguments.reason,
					voteManager
				)
			}
		}
		ephemeralSubCommand {
			name = Lg.Vote.List.Command.name
			description = Lg.Vote.List.Command.description

			action {
				val guild = guild ?: return@action
				val phase = validateVoteChannel(Lg.Vote.Response.Error.cantVoteHere) ?: return@action
				val vote = voteManager.getCurrentVote(phase)
				val isCorrectPhase = lg.botCache.getGameData().phase.type == phase
				if (vote?.votes.isNullOrEmpty() || !isCorrectPhase) {
					respond { content = Lg.Vote.List.Response.Error.noVotes.contextTranslate() }
					return@action
				}
				val voteCount = voteManager.getVoteCount(vote)
				val votersByTarget = vote.votes.entries.groupBy(
					keySelector = { entry -> entry.value },
					valueTransform = { entry -> entry.key }
				)
				respond {
					embed {
						title = Lg.Vote.List.Response.Success.Embed.title.contextTranslate()
						description = if (phase == PhaseType.DAY)
							Lg.Vote.List.Response.Success.Embed.Description.day.contextTranslate()
						else Lg.Vote.List.Response.Success.Embed.Description.night.contextTranslate()
						voteCount.entries.sortedByDescending { it.value }.forEach { (target, count) ->
							field(guild.getMember(target).mention, inline = false) {
								val playerNameList = votersByTarget[target]?.map {
									guild.getMember(it).mention
								}?.sorted()?.joinToString(", ", prefix = "(", postfix = ")").orEmpty()

								if (target == vote.corbeau) {
									Lg.Vote.List.Response.Success.Embed.Field.WithCorbeau.description
										.contextTranslate(count, playerNameList)
								} else Lg.Vote.List.Response.Success.Embed.Field.description
									.contextTranslate(count, playerNameList)
							}
						}
					}
				}
			}
		}
		ephemeralSubCommand {
			name = Lg.Vote.Reset.Command.name
			description = Lg.Vote.Reset.Command.description
			adminOnly {
				val phase = validateVoteChannel(Lg.Vote.Response.Error.cantVoteHere) ?: return@adminOnly
				val vote = voteManager.getCurrentVote(phase)
				val votes = vote?.votes
				val isCorrectPhase = lg.botCache.getGameData().phase.type == phase
				if (votes.isNullOrEmpty() || !isCorrectPhase) {
					respond { content = Lg.Vote.Reset.Response.Error.noVotes.contextTranslate() }
					return@adminOnly
				}
				voteManager.resetVotes(phase)
				respond { content = Lg.Vote.Reset.Response.success.contextTranslate() }
			}
		}
	}
	ephemeralSubCommand(::UnvoteArguments) {
		name = Lg.Unvote.Command.name
		description = Lg.Unvote.Command.description

		action {
			val previousId = arguments.previousId?.snowflake
			val channelId = channel.id
			val votes = LgChannelType.VOTES.toId()
			val voteLoups = LgChannelType.LOUPS_VOTE.toId()
			val voteCorbeau = LgChannelType.CORBEAU.toId()
			val voteChannels = listOf(votes, voteLoups, voteCorbeau)

			if (channelId !in voteChannels) {
				respond { content = Lg.Vote.Response.Error.cantVoteHere.contextTranslate() }
				return@action
			} else {
				when (channelId) {
					votes, voteLoups -> {
						val phase = if (channelId == votes) PhaseType.DAY else PhaseType.NIGHT
						val currentVote = voteManager.getCurrentVote(phase)
						val isCorrectPhase = lg.botCache.getGameData().phase.type == phase
						if (currentVote == null || !currentVote.votes.containsKey(user.id) || !isCorrectPhase) {
							respond { content = Lg.Unvote.Response.Error.noVote.contextTranslate() }
							return@action
						}
						voteManager.unvote(user.id)
						respond { content = Lg.Unvote.Response.success.contextTranslate() }
					}
					voteCorbeau -> {
						val currentVote = voteManager.getCurrentVote(PhaseType.DAY)
						val isCorrectPhase = lg.botCache.getGameData().phase.type == PhaseType.NIGHT
						if (currentVote == null || currentVote.corbeau == null || !isCorrectPhase) {
							respond { content = Lg.Unvote.Response.Error.noVote.contextTranslate() }
							return@action
						}
						voteManager.unvoteCorbeau()
						respond { content = Lg.Unvote.Response.success.contextTranslate() }
					}
				}
			}
			if (previousId != null)
				channel.getMessage(previousId).delete(Lg.System.MessageDelete.VoteMessage.reason.contextTranslate())
		}
	}
	ephemeralSubCommand {
		name = Lg.MostVoted.Command.name
		description = Lg.MostVoted.Command.description

		adminOnly {
			val guild = guild ?: return@adminOnly
			val phase = validateVoteChannel(Lg.MostVoted.Response.Error.cantUseHere)
				?: return@adminOnly
			if (phase != PhaseType.DAY) {
				respond { content = Lg.MostVoted.Response.Error.onlyVillageVotes.contextTranslate() }
				return@adminOnly
			}
			val currentVote = voteManager.getCurrentVote(phase)
			if (currentVote?.votes.isNullOrEmpty()) {
				respond { content = Lg.MostVoted.Response.Error.noVotes.contextTranslate() }
				return@adminOnly
			}
			val voteCount = voteManager.getVoteCount(currentVote)
			val maxVotes = voteCount.values.maxOrNull() ?: 0
			val mostVotedPlayers = voteCount.filter { voteEntry -> voteEntry.value == maxVotes }.keys
			if (mostVotedPlayers.size > 1) {
				val members = mostVotedPlayers.map { playerId ->
					val member = guild.getMember(playerId)
					member.dm {
						content = Lg.MostVoted.Response.Success.tie.contextTranslate(maxVotes)
					}
					member
				}
				respond {
					content = Lg.MostVoted.Response.Success.sentTie.contextTranslate(
						members.joinToString(", ") { it.mention },
						maxVotes
					)
				}
			} else {
				val member = guild.getMember(mostVotedPlayers.first())
				member.dm {
					content = Lg.MostVoted.Response.Success.single.contextTranslate(maxVotes)
				}
				respond {
					content = Lg.MostVoted.Response.Success.sentSingle.contextTranslate(member.mention, maxVotes)
				}
			}
		}
	}
}

context(lg: LG)
suspend fun <C : EphemeralSlashCommandContext<*, *>> C.validateVoteChannel(errorMessage: Key): PhaseType? {
	val village = LgChannelType.VILLAGE.toId()
	val votes = LgChannelType.VOTES.toId()
	val loups = LgChannelType.LOUPS_CHAT.toId()
	val loupsVotes = LgChannelType.LOUPS_VOTE.toId()
	val dayChannels = setOf(village, votes)
	val nightChannels = setOf(loups, loupsVotes)

	return when (channel.id) {
		in dayChannels -> PhaseType.DAY
		in nightChannels -> PhaseType.NIGHT
		else -> {
			respond { content = errorMessage.contextTranslate() }
			null
		}
	}
}

context(lg: LG)
private suspend fun <A : Arguments, M : ModalForm> EphemeralSlashCommandContext<A, M>.handleVote(
	phase: PhaseType,
	voteChannelType: LgChannelType,
	target: User,
	reason: String?,
	voteManager: VoteManager,
	handleCorbeau: Boolean = false
) {
	val currentVote = voteManager.getCurrentVote(phase)
	if (handleCorbeau && channel.id == LgChannelType.CORBEAU.toId()) {
		if (currentVote?.corbeau != null) {
			respond { content = Lg.Vote.Response.Error.Corbeau.alreadyVoted.contextTranslate() }
			return
		}
		voteManager.voteCorbeau(target.id)
		respond { content = Lg.Vote.Response.Success.Corbeau.vote.contextTranslate(target.mention) }
		return
	}
	if (channel.id != voteChannelType.toId()) {
		respond { content = Lg.Vote.Response.Error.cantVoteHere.contextTranslate() }
		return
	}
	if (currentVote == null) {
		respond { content = Lg.Vote.Response.Error.noCurrentVote.contextTranslate() }
		return
	}
	if (currentVote.choices.isNotEmpty() && target.id !in currentVote.choices) {
		respond { content = Lg.Vote.Response.Error.notInChoices.contextTranslate() }
		return
	}
	val hasAlreadyVoted = user.id in currentVote.votes
	voteManager.vote(user.id, target)
	respond { content = Lg.Vote.Response.Success.vote.contextTranslate(target.mention) }
	voteChannelType.toId().sendAsWebhook(
		lg.bot,
		member?.asMember()?.effectiveName ?: Lg.Vote.Response.Success.Public.unknownEffectiveName.contextTranslate(),
		member?.asMember()?.avatar?.cdnUrl?.toUrl(),
		"votes"
	) {
		content = getVotePublicResponse(target, reason, hasAlreadyVoted)
	}
}


/**
 * Gets the public response message for a vote.
 *
 * @param target The user being voted for.
 * @param reason An optional reason for the vote.
 * @param alreadyVoted Indicates if the user has already voted.
 * @return The translated message for the vote response.
 */
suspend fun TranslatableContext.getVotePublicResponse(
	target: User,
	reason: String? = null,
	hasAlreadyVoted: Boolean = false,
) = when {
	hasAlreadyVoted && reason != null ->
		Lg.Vote.Response.Success.Public.changeReason.contextTranslate(target.mention, reason)
	hasAlreadyVoted -> Lg.Vote.Response.Success.Public.change.contextTranslate(target.mention)
	reason != null -> Lg.Vote.Response.Success.Public.voteReason.contextTranslate(target.mention, reason)
	else -> Lg.Vote.Response.Success.Public.vote.contextTranslate(target.mention)
}

/**
 * Arguments for the vote command.
 * This class defines the arguments that can be passed to the vote command.
 *
 * @property target The user to vote for.
 * @property reason An optional reason for the vote.
 */
private class VoteArguments : Arguments() {
	val target by user {
		name = Lg.Vote.Argument.Target.name
		description = Lg.Vote.Argument.Target.description
	}
	val reason by optionalString {
		name = Lg.Vote.Argument.Reason.name
		description = Lg.Vote.Argument.Reason.description
	}
}

private class UnvoteArguments : Arguments() {
	val previousId by optionalInt {
		name = Lg.Unvote.Argument.PreviousId.name
		description = Lg.Unvote.Argument.PreviousId.description
	}
}
