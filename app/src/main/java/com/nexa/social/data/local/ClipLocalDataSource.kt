package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.Clip

/** Persists local clips. Seeded once from clearly-labelled sample clips (no bundled video files). */
class ClipLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "clips.json"
    }

    fun seedIfEmpty() {
        if (!store.exists(FILE)) {
            store.writeList(FILE, SampleData.clips())
        }
    }

    fun readClips(): List<Clip> {
        seedIfEmpty()
        return store.readList(FILE)
    }

    fun writeClips(clips: List<Clip>) = store.writeList(FILE, clips)
}
