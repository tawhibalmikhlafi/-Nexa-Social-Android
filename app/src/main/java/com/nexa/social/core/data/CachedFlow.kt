package com.nexa.social.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface UiData<out T> {
    data class Fresh<T>(val value: T) : UiData<T>
    data class Stale<T>(val value: T, val cachedAt: Long) : UiData<T>
    data object Empty : UiData<Nothing>
}

interface SyncCache<T> {
    fun read(key: String): CacheEntry<T>?
    fun write(key: String, value: T, at: Long)

    data class CacheEntry<T>(val value: T, val at: Long)
}

/** In-memory cache — enough for a session. Swap for DataStore/JSON to persist. */
class InMemorySyncCache<T> : SyncCache<T> {
    private val map = mutableMapOf<String, SyncCache.CacheEntry<T>>()

    override fun read(key: String) = map[key]

    override fun write(key: String, value: T, at: Long) {
        map[key] = SyncCache.CacheEntry(value, at)
    }
}

/**
 * Emits the cached value immediately, then tries the network:
 *  - success → cache is refreshed and [UiData.Fresh] is emitted
 *  - failure with cache → [UiData.Stale] only (no error, the user keeps their data)
 *  - failure without cache → [UiData.Empty]
 */
fun <T> cachedFlow(
    cacheKey: String,
    storage: SyncCache<T>,
    loader: suspend () -> T?,
): Flow<UiData<T>> = flow {
    val cached = storage.read(cacheKey)
    if (cached != null) emit(UiData.Stale(cached.value, cached.at))
    val fresh = loader()
    if (fresh != null) {
        storage.write(cacheKey, fresh, System.currentTimeMillis())
        emit(UiData.Fresh(fresh))
    } else if (cached == null) {
        emit(UiData.Empty)
    }
}
