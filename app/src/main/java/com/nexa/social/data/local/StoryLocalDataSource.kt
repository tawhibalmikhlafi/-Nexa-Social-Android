package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.Story

/** Persists local stories. Seeded once from clearly-labelled sample stories. */
class StoryLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "stories.json"
    }

    fun seedIfEmpty() {
        if (!store.exists(FILE)) {
            store.writeList(FILE, SampleData.stories())
        }
    }

    fun readStories(): List<Story> {
        seedIfEmpty()
        return store.readList(FILE)
    }

    fun writeStories(stories: List<Story>) = store.writeList(FILE, stories)
}
