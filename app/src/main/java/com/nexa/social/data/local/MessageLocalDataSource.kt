package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.Conversation
import com.nexa.social.domain.model.Message

/** Persists conversations. Seeded once from local sample conversations. */
class MessageLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "conversations.json"
        const val OUTBOX_FILE = "message_outbox.json"
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

    fun readOutbox(): List<PendingMessage> = store.readList(OUTBOX_FILE)

    fun writeOutbox(messages: List<PendingMessage>) = store.writeList(OUTBOX_FILE, messages)
}


/** Durable text-message operation waiting for a Supabase connection. */
data class PendingMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val createdAt: Long
)
