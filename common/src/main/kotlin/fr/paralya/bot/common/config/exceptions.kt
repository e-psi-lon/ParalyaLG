package fr.paralya.bot.common.config

import fr.paralya.bot.common.ParalyaBotException
import fr.paralya.bot.common.orUnknownClass
import io.konform.validation.ValidationError

open class BotConfigException(message: String) : ParalyaBotException(message)

class InvalidConfigException(className: String?, val errors: List<ValidationError>) : BotConfigException(
    "Configuration for ${className.orUnknownClass()} is invalid due to the following errors: " +
            errors.joinToString { it.message }
)
class MissingConfigEntryException(path: String) : BotConfigException("No configuration found for path: $path")

class MissingConfigException : BotConfigException("The configuration file is missing.")

class BotAlreadyBootstrappedException : BotConfigException("The bot has already been bootstrapped.")
