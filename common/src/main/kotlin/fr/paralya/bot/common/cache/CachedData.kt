package fr.paralya.bot.common.cache

import dev.kord.cache.api.data.description
import kotlinx.serialization.Serializable

@Serializable
data class CachedData(
    val namespace: String,
    val key: String,
    val data: ByteArray,
    val itemId: String? = null
) {
    val id
        get() = buildString {
            append(namespace)
            append(":")
            append(key)
            itemId?.let { id ->
                append(":")
                append(id)
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CachedData

        if (namespace != other.namespace) return false
        if (key != other.key) return false
        if (itemId != other.itemId) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = namespace.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (itemId?.hashCode() ?: 0)
        return result
    }
    companion object {
        /** Description for caching [CachedData] instances by their id */
        val description = description<CachedData, String>(CachedData::id)
    }
}
