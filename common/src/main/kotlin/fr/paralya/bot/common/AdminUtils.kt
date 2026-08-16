package fr.paralya.bot.common

import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kordex.core.checks.memberFor
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.SlashCommand
import dev.kordex.core.commands.application.slash.SlashCommandContext
import dev.kordex.core.components.forms.ModalForm
import fr.paralya.bot.common.config.BotConfig
import fr.paralya.bot.common.config.ConfigManager
import org.koin.core.component.inject

/**
 * Extension function to register a command that admins can only execute.
 *
 * This function takes a lambda action that will be executed
 * only if the user is an admin.
 * It works as a wrapper around the existing command registration
 *
 * @param C The type of the [SlashCommandContext].
 * @param A The type of the [Arguments].
 * @param M The type of the [ModalForm].
 * @param action The action to be executed if the user is an admin.
 */
fun <C : SlashCommandContext<*, A, M>, A : Arguments, M : ModalForm> SlashCommand<C, A, M>.adminOnly(
	extraAllowList: List<ULong> = emptyList(),
	action: suspend C.(M?) -> Unit,
) {
	val configManager by inject<ConfigManager>()
	check {
		errorResponseKey = I18n.System.Permissions.notAdmin
		failIfNot {
			(configManager.botConfig.admins + extraAllowList).contains(memberFor(event)?.id?.value)
		}
	}
	action(action)
}

/**
 * Extension function to check if a user is an admin based on the given [config]
 * @return true if the user is an admin, false otherwise.
 */
fun User?.isAdmin(config: BotConfig): Boolean = this != null && config.admins.contains(id.value)


/**
 * Extension function to check if a member is an admin based on the given [config]
 * @return true if the member is an admin, false otherwise.
 */
fun Member?.isAdmin(config: BotConfig): Boolean =  this != null && config.admins.contains(id.value)
