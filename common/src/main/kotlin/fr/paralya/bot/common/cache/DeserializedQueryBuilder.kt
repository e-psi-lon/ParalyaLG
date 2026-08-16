package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.Query
import dev.kord.cache.api.QueryBuilder
import dev.kord.cache.api.query
import dev.kord.core.cache.idEq
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

@OptIn(ExperimentalSerializationApi::class)
@Suppress("LongParameterList")
internal class DeserializedQueryBuilder<T : Any>(
    private val namespace: String,
    private val typeKey: String,
    private val clazz: KClass<T>,
    private val cache: DataCache,
    private val itemIdProperty: KProperty1<T, Any>? = null,
    private val serializer: KSerializer<T>,
    private val cbor: Cbor
) : QueryBuilder<T> {
    private val predicates = mutableListOf<(T) -> Boolean>()

    override fun <R> KProperty1<T, R>.predicate(predicate: (R) -> Boolean) {
        predicates.add { predicate(get(it)) }
    }

    override fun build(): Query<T> {
        val predicateCopy = predicates.toList()

        val cachedDataFlow = cache.query<CachedData> {
            idEq(CachedData::namespace, namespace)
            idEq(CachedData::key, typeKey)
        }.asFlow()
        val items = cachedDataFlow
            .map { cachedData ->
                val item = cbor.decodeFromByteArray(serializer, cachedData.data)
                cachedData to item
            }
            .filter { (_, item) -> predicateCopy.all { it(item) } }
        return object : Query<T> {
            override fun asFlow(): Flow<T> = items.map { it.second }

            override suspend fun remove() {
                items.collect { (cachedData, _) ->
                    cache.query<CachedData> {
                        idEq(CachedData::id, cachedData.id)
                    }.remove()
                }
            }

            override suspend fun update(mapper: suspend (T) -> T) {
                items.collect { (_, item) ->
                    cache.putSerialized(
                        namespace,
                        mapper(item),
                        clazz,
                        itemIdProperty,
                        serializer
                    )
                }
            }
        }
    }
}
