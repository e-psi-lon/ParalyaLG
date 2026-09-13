package fr.paralya.bot.common.cache

import dev.kord.cache.redis.RedisConfiguration
import dev.kord.cache.redis.RedisEntryCache
import dev.kord.core.cache.Generator
import dev.kord.core.cache.KordCacheBuilder
import fr.paralya.bot.common.orUnknownClass
import kotlin.time.Duration

@Suppress("UnusedReceiverParameter") // used for scoping
fun <T : Any, I : Any> KordCacheBuilder.redisCache(config: RedisConfiguration = RedisConfiguration()): Generator<T, I> = { cache, description ->
    RedisEntryCache(cache, description, config, entryName = description.klass.qualifiedName.orUnknownClass())
}


@Suppress("UnusedReceiverParameter") // used for scoping
fun <T : Any, I : Any> KordCacheBuilder.redisCacheWithTtl(config: RedisConfiguration = RedisConfiguration(), ttl: Duration): Generator<T, I> = { cache, description ->
    DefaultTtlEntryCache(RedisEntryCache(cache, description, config, entryName = description.klass.qualifiedName.orUnknownClass()), ttl)
}