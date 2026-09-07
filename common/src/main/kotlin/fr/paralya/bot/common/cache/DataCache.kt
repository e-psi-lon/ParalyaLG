package fr.paralya.bot.common.cache

import dev.kord.cache.api.DataEntryCache
import dev.kord.cache.api.data.DataDescription
import dev.kord.cache.api.delegate.DelegatingDataCache
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