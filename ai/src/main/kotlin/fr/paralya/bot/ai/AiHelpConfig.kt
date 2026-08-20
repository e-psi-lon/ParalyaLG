package fr.paralya.bot.ai

import fr.paralya.bot.common.config.ValidatedConfig
import io.konform.validation.Validation
import io.konform.validation.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
enum class GeminiModel {
    @SerialName("gemini-3.7-flash")
    GEMINI_37_FLASH,
    @SerialName("gemini-3.1-pro-preview")
    GEMINI_31_PRO_PREVIEW,
    @SerialName("gemini-3.1-flash-lite")
    GEMINI_31_FLASH_LITE,
    @SerialName("gemini-2.5-flash")
    GEMINI_25_FLASH,
    @SerialName("gemini-2.5-pro")
    GEMINI_25_PRO,
}

@Serializable
enum class ResponseMode {
    MENTION,
    PREFIX,
    BOTH
}

@Serializable
enum class ChannelRestrictionMode {
    ALL,
    WHITELIST
}

@Serializable
internal data class AiHelpConfig(
    val geminiModel: GeminiModel,
    private val geminiToken: String,
    val responseMode: ResponseMode = ResponseMode.BOTH,
    val channelRestrictionMode: ChannelRestrictionMode = ChannelRestrictionMode.ALL,
    val allowDMs: Boolean = false,
    val rateLimitEnabled: Boolean = false,
    val cooldownSeconds: Int = 150,
    val bypassRoles: List<ULong>,
    val bypassUsers: List<ULong>,
    val notifyOnRateLimit: Boolean = false,
    ) : ValidatedConfig {

    @Transient
    private val validator = Validation {

    }

    override fun validate(): ValidationResult<AiHelpConfig> =
        validator(this)

    fun getGeminiClient() {

    }

}