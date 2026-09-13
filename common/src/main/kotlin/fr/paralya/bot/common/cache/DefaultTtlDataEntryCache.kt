package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataEntryCacheWithTTL
import dev.kord.cache.api.annotation.CacheExperimental
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

class DefaultTtlEntryCache<T : Any>(
    private val delegate: DataEntryCacheWithTTL<T>,
    private val defaultTtl: Duration,
) : DataEntryCacheWithTTL<T> by delegate {
    @CacheExperimental
    override suspend fun put(item: T) {
        delegate.put(item, defaultTtl)
    }

    @CacheExperimental
    override suspend fun put(items: Flow<T>) {
        delegate.put(items, defaultTtl)
    }

    @CacheExperimental
    override suspend fun put(items: Iterable<T>) {
        delegate.put(items, defaultTtl)
    }

    @CacheExperimental
    override suspend fun put(vararg items: T) {
        delegate.put(*items, ttl = defaultTtl)
    }
}
