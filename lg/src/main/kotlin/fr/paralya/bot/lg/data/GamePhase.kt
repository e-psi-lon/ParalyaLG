package fr.paralya.bot.lg.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class GamePhase {
    abstract val number: Int
    abstract val type: PhaseType

    @Serializable
    @SerialName("day")
    data class Day(override val number: Int) : GamePhase() {
        override val type: PhaseType get() = PhaseType.DAY
    }

    @Serializable
    @SerialName("night")
    data class Night(override val number: Int) : GamePhase() {
        override val type: PhaseType get() = PhaseType.NIGHT
    }

    val isDay: Boolean get() = this is Day
    val isNight: Boolean get() = this is Night

    fun next(): GamePhase = when (this) {
        is Day -> Night(number)
        is Night -> Day(number + 1)
    }

    enum class PhaseType {
        DAY, NIGHT
    }
}
