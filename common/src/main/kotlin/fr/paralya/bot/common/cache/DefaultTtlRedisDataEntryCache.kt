package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataEntryCacheWithTTL
import dev.kord.cache.api.annotation.CacheExperimental
import dev.kord.cache.redis.RedisEntryCache
import kotlin.time.Duration

class DefaultTtlRedisEntryCache<T : Any, I>(
    private val delegate: RedisEntryCache<T, I>,
    private val defaultTtl: Duration,
) : DataEntryCacheWithTTL<T> by delegate {
    @CacheExperimental
    override suspend fun put(item: T) {
        delegate.put(item, defaultTtl)
    }
}
