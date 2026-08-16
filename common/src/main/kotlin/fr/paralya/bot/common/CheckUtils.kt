package fr.paralya.bot.common

import dev.kord.common.entity.MessageFlag
import dev.kordex.core.checks.failed
import dev.kordex.core.checks.messageFor
import dev.kordex.core.checks.passed
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.core.checks.userFor
import io.github.oshai.kotlinlogging.KotlinLogging

suspend fun CheckContext<*>.isUser() {
    if (!passed) {
        return
    }

    val logger = KotlinLogging.logger("fr.paralya.bot.common.isUser")
    val user = userFor(event)?.asUserOrNull()

    if (user == null) {
        logger.failed("Event did not concern a user.")

        fail()
    } else if (!user.isBot) {
        logger.passed()

        pass()
    }
}


suspend fun CheckContext<*>.isNotEphemeral() {
    if (!passed) return

    val message = messageFor(event)?.asMessageOrNull() ?: return
    failIf(message.flags?.contains(MessageFlag.Ephemeral) == true)
}
