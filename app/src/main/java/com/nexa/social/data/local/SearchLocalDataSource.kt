package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore

/** Persists the local search history as an ordered list. */
class SearchLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "search_history.json"
        const val MAX_ENTRIES = 20
    }

    fun readHistory(): List<String> = store.readList<HistoryEntry>(FILE).map { it.query }

    fun writeHistory(queries: List<String>) {
        val trimmed = queries.filter { it.isNotBlank() }.distinct().take(MAX_ENTRIES)
        store.writeList(FILE, trimmed.map { HistoryEntry(it) })
    }

    data class HistoryEntry(val query: String)
}
