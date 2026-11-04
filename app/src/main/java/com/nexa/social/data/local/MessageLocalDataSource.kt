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
        const val ACTION_OUTBOX_FILE = "message_action_outbox.json"
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

    fun readActionOutbox(): List<PendingMessageAction> = store.readList(ACTION_OUTBOX_FILE)

    fun writeActionOutbox(actions: List<PendingMessageAction>) = store.writeList(ACTION_OUTBOX_FILE, actions)
}


/** Durable text-message operation waiting for a Supabase connection. */
data class PendingMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val createdAt: Long,
    val attachmentPath: String? = null,
    val localAttachmentUri: String? = null,
    val attachmentMime: String? = null,
    val attachmentName: String? = null
)


/** Durable, idempotent message mutation waiting for Supabase. */
data class PendingMessageAction(
    val id: String,
    val userId: String,
    val conversationId: String,
    val messageId: String,
    val action: String,
    val value: String?,
    val createdAt: Long
)
