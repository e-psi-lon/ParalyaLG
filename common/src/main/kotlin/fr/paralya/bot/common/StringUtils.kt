package fr.paralya.bot.common

@Suppress("NOTHING_TO_INLINE")
inline fun String?.orUnknownClass() = this ?: "<unknown class>"

@Suppress("NOTHING_TO_INLINE")
inline fun String?.orNoMessage() = this ?: "No message"
