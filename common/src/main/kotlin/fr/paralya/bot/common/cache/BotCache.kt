package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.Query
import dev.kord.cache.api.QueryBuilder
import dev.kord.cache.api.put
import dev.kord.cache.redis.RedisConfiguration
import fr.paralya.bot.common.InternalBotApi
import io.lettuce.core.RedisClient
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer

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


@PublishedApi
internal const val UNKNOWN_TYPE_KEY = "unknown"

/**
 * Queries items of type [T] from the cache within [namespace].
 *
 * @param namespace The cache partition, typically the plugin ID,
 * used to scope entries and avoid collisions between modules.
 * @param itemIdProperty The property used as the item's unique identifier within the namespace.
 *   Required when multiple instances of [T] coexist under the same namespace, so that updates and
 *   removes target the right entry.
 * @param typeKey Differentiates between multiple types stored under the same namespace.
 * Defaults to the simple class name of [T].
 * @param block Optional predicate block to filter results.
 * @return A [Query] of deserialized [T] instances matching the given criteria.
 */
inline fun <reified T : Any> DataCache.querySerialized(
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = T::class.simpleName ?: UNKNOWN_TYPE_KEY,
    noinline block: QueryBuilder<T>.() -> Unit = {}
): Query<T> = querySerialized(T::class, namespace, serializer<T>(), itemIdProperty, typeKey, block)

/**
 * Queries items of type [T] from the cache within [namespace].
 * Prefer the reified overload unless [clazz] or [serializer] must be provided explicitly.
 *
 * @param clazz The class of the type to query.
 * @param namespace The cache partition, typically the plugin ID,
 * used to scope entries and avoid collisions between modules.
 * @param serializer The serializer for [T].
 * @param itemIdProperty The property used as the item's unique identifier within the namespace.
 * @param typeKey Differentiates between multiple types stored under the same namespace.
 * Defaults to the simple class name of [clazz].
 * @param block Optional predicate block to filter results.
 * @return A [Query] of deserialized [T] instances matching the given criteria.
 */
@OptIn(ExperimentalSerializationApi::class)
@Suppress("LongParameterList")
fun <T : Any> DataCache.querySerialized(
    clazz: KClass<T>,
    namespace: String,
    serializer: KSerializer<T>,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = clazz.simpleName ?: UNKNOWN_TYPE_KEY,
    block: QueryBuilder<T>.() -> Unit = {}
): Query<T> {
    val builder = DeserializedQueryBuilder(
        namespace,
        typeKey,
        clazz,
        this,
        itemIdProperty,
        serializer,
        cbor
    )
    builder.block()
    return builder.build()
}

/**
 * Stores [item] in the cache under [namespace].
 *
 * @param namespace The cache partition to store the item in, typically the plugin ID.
 * @param item The item to store.
 * @param itemId Property whose runtime value uniquely identifies this item within the namespace.
 *   Required when multiple instances of the same type are stored under the same namespace.
 */
suspend inline fun <reified T : Any> DataCache.putSerialized(
    namespace: String,
    item: T,
    itemId: KProperty1<T, Any>? = null
) {
    putSerialized(namespace, item, T::class, itemId, serializer<T>())
}

/**
 * Stores [item] in the cache under [namespace].
 * Prefer the reified overload unless [clazz] or [serializer] must be provided explicitly.
 *
 * @param namespace The cache partition to store the item in, typically the plugin ID.
 * @param item The item to store.
 * @param clazz The class of [item].
 * @param itemId Property whose runtime value uniquely identifies this item within the namespace.
 *   Required when multiple instances of the same type are stored under the same namespace.
 * @param serializer The serializer for [T].
 */
@OptIn(ExperimentalSerializationApi::class)
suspend fun <T : Any>DataCache.putSerialized(
    namespace: String,
    item: T,
    clazz: KClass<T>,
    itemId: KProperty1<T, Any>? = null,
    serializer: KSerializer<T>
) {
    val data = cbor.encodeToByteArray(serializer, item)
    val cachedData = CachedData(
        namespace = namespace,
        key = clazz.simpleName ?: UNKNOWN_TYPE_KEY,
        data = data,
        itemId = itemId?.get(item)?.toString()
    )
    put(cachedData)
}

/**
 * Stores all items from [items] in the cache under [namespace].
 *
 * @param namespace The cache partition to store the items in, typically the plugin ID.
 * @param items The items to store.
 * @param itemId Property whose runtime value uniquely identifies each item within the namespace.
 *   Required when multiple instances of the same type are stored under the same namespace.
 */
suspend inline fun <reified T : Any> DataCache.putSerializedAll(
    namespace: String,
    items: Flow<T>,
    itemId: KProperty1<T, Any>
) = putSerializedAll(namespace, items, T::class, serializer<T>(), itemId)

/**
 * Stores all items from [items] in the cache under [namespace].
 * Prefer the reified overload unless [clazz] or [serializer] must be provided explicitly.
 *
 * @param namespace The cache partition to store the items in, typically the plugin ID.
 * @param items The items to store.
 * @param clazz The class of the item type.
 * @param serializer The serializer for [T].
 * @param itemId Property whose runtime value uniquely identifies each item within the namespace.
 */
suspend fun <T : Any>DataCache.putSerializedAll(
    namespace: String,
    items: Flow<T>,
    clazz: KClass<T>,
    serializer: KSerializer<T>,
    itemId: KProperty1<T, Any>
) = items.collect { putSerialized(namespace, it, clazz, itemId, serializer) }

/**
 * Removes all items of type [T] from the cache within [namespace] that match [block].
 *
 * @param namespace The cache partition to remove items from, typically the plugin ID.
 * @param itemIdProperty The property used as the item's unique identifier within the namespace.
 * @param typeKey Differentiates between multiple types stored under the same namespace.
 * Defaults to the simple class name of [T].
 * @param block Optional predicate block to narrow which items are removed.
 */
suspend inline fun <reified T : Any> DataCache.removeSerialized(
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = T::class.simpleName ?: UNKNOWN_TYPE_KEY,
    noinline block: QueryBuilder<T>.() -> Unit = {}
) = removeSerialized(T::class, namespace, serializer<T>(), itemIdProperty, typeKey, block)

/**
 * Removes all items of type [T] from the cache within [namespace] that match [block].
 * Prefer the reified overload unless [clazz] or [serializer] must be provided explicitly.
 *
 * @param clazz The class of the type to remove.
 * @param namespace The cache partition to remove items from, typically the plugin ID.
 * @param serializer The serializer for [T], required to deserialize entries before predicates can be applied.
 * @param itemIdProperty The property used as the item's unique identifier within the namespace.
 * @param typeKey Differentiates between multiple types stored under the same namespace.
 * Defaults to the simple class name of [clazz].
 * @param block Optional predicate block to narrow which items are removed.
 */
@Suppress("LongParameterList")
suspend fun <T : Any>DataCache.removeSerialized(
    clazz: KClass<T>,
    namespace: String,
    serializer: KSerializer<T>,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = clazz.simpleName ?: UNKNOWN_TYPE_KEY,
    block: QueryBuilder<T>.() -> Unit = {}
) = querySerialized(clazz, namespace, serializer, itemIdProperty, typeKey, block).remove()

/**
 * Applies [transform] to all items of type [T] within [namespace] that match [block] and re-stores the results.
 *
 * @param namespace The cache partition containing the items to update, typically the plugin ID.
 * @param itemIdProperty The property used as the item's unique identifier within the namespace.
 *   Required so that each updated item is re-stored under the same key.
 * @param typeKey Differentiates between multiple types stored under the same namespace.
 * Defaults to the simple class name of [T].
 * @param block Optional predicate block to narrow which items are updated.
 * @param transform Transformation applied to each matching item to produce its replacement.
 */
suspend inline fun <reified T : Any> DataCache.updateSerialized(
    namespace: String,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = T::class.simpleName ?: UNKNOWN_TYPE_KEY,
    noinline block: QueryBuilder<T>.() -> Unit = {},
    noinline transform: suspend (T) -> T
) = updateSerialized(T::class, namespace, serializer<T>(), itemIdProperty, typeKey, block, transform)

/**
 * Applies [transform] to all items of type [T] within [namespace] that match [block] and re-stores the results.
 * Prefer the reified overload unless [clazz] or [serializer] must be provided explicitly.
 *
 * @param clazz The class of the type to update.
 * @param namespace The cache partition containing the items to update, typically the plugin ID.
 * @param serializer The serializer for [T].
 * @param itemIdProperty The property used as the item's unique identifier within the namespace.
 *   Required so that each updated item is re-stored under the same key.
 * @param typeKey Differentiates between multiple types stored under the same namespace.
 * Defaults to the simple class name of [clazz].
 * @param block Optional predicate block to narrow which items are updated.
 * @param transform Transformation applied to each matching item to produce its replacement.
 */
@Suppress("LongParameterList")
suspend fun <T : Any> DataCache.updateSerialized(
    clazz: KClass<T>,
    namespace: String,
    serializer: KSerializer<T>,
    itemIdProperty: KProperty1<T, Any>? = null,
    typeKey: String = clazz.simpleName ?: UNKNOWN_TYPE_KEY,
    block: QueryBuilder<T>.() -> Unit = {},
    transform: suspend (T) -> T
) = querySerialized(clazz, namespace, serializer, itemIdProperty, typeKey, block).update { transform(it) }

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
