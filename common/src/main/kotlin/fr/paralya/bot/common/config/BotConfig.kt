package fr.paralya.bot.common.config

import io.konform.validation.Validation
import io.konform.validation.ValidationResult
import io.konform.validation.onEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Data class representing the core bot configuration.
 *
 * @property admins A list of admin user IDs.
 * @property dmLogChannelId A channel ID to copy direct messages to.
 * @property paralyaId The ID of the Paralya guild. (can be changed for testing purposes)
 */
data class BotConfig(
    val admins: List<ULong>,
    val dmLogChannelId: ULong,
    val paralyaId: ULong,
)


@Serializable
internal data class PrivateBotConfig(
    val token: String = "",
    val admins: List<ULong> = emptyList(),
    val dmLogChannelId: ULong = ULong.MIN_VALUE,
    val paralyaId: ULong = ULong.MIN_VALUE
) : ValidatedConfig {
    @Transient
    private val validator = Validation {
        PrivateBotConfig::token {
            defined() hint "Token must NOT be empty. Please provide it, it is a base requirement for the bot to work."
        }
        PrivateBotConfig::admins {
            defined() hint "Admins list must have at least one item"
            onEach {
                appearsToBeSnowflake("Admin ID") // Just to be sure that a real ID is provided
            }
        }
        PrivateBotConfig::dmLogChannelId {
            appearsToBeSnowflake("DM log channel ID")
        }
        PrivateBotConfig::paralyaId {
            appearsToBeSnowflake("Paralya guild ID")
        }
    }

    override fun validate(): ValidationResult<PrivateBotConfig> = validator(this)

    override fun toString(): String =
        "PrivateBotConfig(token=***, admins=$admins, dmLogChannelId=$dmLogChannelId, paralyaId=$paralyaId)"


    fun toPublic() = BotConfig(admins, dmLogChannelId, paralyaId)
}
