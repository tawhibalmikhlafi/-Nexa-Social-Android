package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.Space

/** Persists spaces (both sample and locally created) in a single file. */
class SpaceLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "spaces.json"
    }

    fun seedIfEmpty() {
        if (!store.exists(FILE)) {
            store.writeList(FILE, SampleData.spaces())
        }
    }

    fun readSpaces(): List<Space> {
        seedIfEmpty()
        return store.readList(FILE)
    }

    fun writeSpaces(spaces: List<Space>) = store.writeList(FILE, spaces)
}
