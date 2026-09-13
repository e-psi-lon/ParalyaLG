package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.DataEntryCache
import dev.kord.cache.api.QueryBuilder
import dev.kord.cache.api.data.DataDescription
import dev.kord.cache.api.delegate.DelegatingDataCache
import dev.kord.cache.api.query
import dev.kord.cache.redis.RedisConfiguration
import fr.paralya.bot.common.InternalBotApi
import io.lettuce.core.RedisClient
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlin.reflect.KProperty1
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType


private val cachesField: Field by lazy {
    DelegatingDataCache::class.java.getDeclaredField("caches").apply { isAccessible = true }
}

internal fun <T: Any, I: Any>DelegatingDataCache.unregister(description: DataDescription<T, I>) {
    cachesField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val caches = cachesField.get(this) as ConcurrentHashMap<KType, DataEntryCache<Any>>
    caches.remove(description.type)
}

/**
 * Extension function for querying enum properties in the cache.
 *
 * @param property The enum property on [T] to filter by.
 * @param value The enum value to match against.
 */
@JvmName("enumEq")
fun <T : Any, E : Enum<E>> QueryBuilder<T>.idEq(property: KProperty1<T, E?>, value: E?) = property.eq(value)

@OptIn(ExperimentalSerializationApi::class)
private val cbor = Cbor {
    encodeDefaults = false
    ignoreUnknownKeys = true
    useDefiniteLengthEncoding = true
    alwaysUseByteString = true
}

@OptIn(ExperimentalSerializationApi::class)
@InternalBotApi
fun redisConfig(client: RedisClient): RedisConfiguration = RedisConfiguration {
    this.client = client
    this.binaryFormat = cbor
}

/**
 * Applies [transform] to all items of type [T] that match [block] and re-stores the results.
 *
 * @param block Optional predicate block to narrow which items are updated.
 * @param transform Transformation applied to each matching item to produce its replacement.
 */
suspend inline fun <reified T : Any> DataCache.update(
    block: QueryBuilder<T>.() -> Unit = {},
    crossinline transform: suspend (T) -> T
) = query(block).update { transform(it) }


@PublishedApi
internal val defaultCacheMutex = Mutex()

/**
 * Executes [operation] atomically under [mutex], preventing concurrent cache modifications.
 *
 * @param mutex The mutex to lock. Defaults to a shared bot-level mutex.
 * @param operation The cache operation to execute.
 */
suspend inline fun <T> DataCache.atomic(
    mutex: Mutex = defaultCacheMutex,
    operation: suspend DataCache.() -> T
): T = mutex.withLock { operation() }
