package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataEntryCache
import dev.kord.cache.api.QueryBuilder
import dev.kord.cache.api.data.DataDescription
import dev.kord.cache.redis.RedisEntryCache
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger("RedisFallbackEntryCache")

class RedisFallbackEntryCache<T : Any, I: Any>(
    private val redis: RedisEntryCache<T, I>,
    private val fallback: DataEntryCache<T>,
    private val description: DataDescription<T, I>,
    private val incompatibleTypes: MutableSet<KClass<*>>
) : DataEntryCache<T> {

    override suspend fun put(item: T) {
        val klass = description.klass
        if (klass in incompatibleTypes) {
            fallback.put(item)
            return
        }
        try {
            redis.put(item)
        } catch (e: SerializationException) {
            logger.warn { "Type ${klass.qualifiedName} is not Redis-compatible (${e::class.simpleName}: ${e.message}), falling back to in-memory cache" }
            incompatibleTypes += klass
            fallback.put(item)
        } catch (e: IllegalStateException) {
            logger.warn { "Type ${klass.qualifiedName} is not Redis-compatible (${e::class.simpleName}: ${e.message}), falling back to in-memory cache" }
            incompatibleTypes += klass
            fallback.put(item)
        }
    }

    override fun query(): QueryBuilder<T> =
        if (description.klass in incompatibleTypes) fallback.query() else redis.query()
}