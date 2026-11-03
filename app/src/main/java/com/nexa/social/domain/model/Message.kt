package com.nexa.social.domain.model

data class Conversation(
    val id: String,
    val participant: UserSummary,
    val lastMessagePreview: String,
    val lastTimestamp: Long,
    val unreadCount: Int,
    val messages: List<Message>,
    val isLocalSample: Boolean,
    val pinnedLocally: Boolean = false,
    val mutedLocally: Boolean = false,
    val archivedLocally: Boolean = false
)

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isRead: Boolean,
    val isMine: Boolean,
    val isLocal: Boolean,
    /** Supabase object path inside the private `message-media` bucket. */
    val attachmentPath: String? = null,
    val attachmentMime: String? = null,
    val attachmentName: String? = null,
    /** Set server-side when the sender edited the text. */
    val editedAt: Long? = null,
    /** True once the sender deleted the message for everyone. */
    val isDeleted: Boolean = false,
    /** Story this message replies to, when it was sent from the story viewer. */
    val replyToStoryId: String? = null,
    /** Null for rows deserialized from an older local cache. */
    val reactions: List<MessageReaction>? = null,
    val isStarred: Boolean = false
) {
    val canEdit: Boolean
        get() = isMine && !isDeleted && !isLocal &&
            System.currentTimeMillis() - timestamp < EDIT_WINDOW_MS

    val reactionList: List<MessageReaction> get() = reactions.orEmpty()

    companion object {
        const val EDIT_WINDOW_MS = 15 * 60_000L
    }
}

data class MessageReaction(
    val emoji: String,
    val userId: String,
    val userName: String? = null
)
