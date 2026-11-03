package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.Conversation

/** Persists conversations. Seeded once from local sample conversations. */
class MessageLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "conversations.json"
    }

    fun seedIfEmpty() {
        if (!store.exists(FILE)) {
            store.writeList(FILE, SampleData.conversations)
        }
    }

    fun readConversations(): List<Conversation> {
        seedIfEmpty()
        return store.readList(FILE)
    }

    fun writeConversations(conversations: List<Conversation>) = store.writeList(FILE, conversations)
}
