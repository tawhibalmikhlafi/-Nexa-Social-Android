package com.nexa.social.domain.model

data class NexaNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timestamp: Long,
    val isRead: Boolean,
    val actorName: String?,
    val actorAvatarUri: String?,
    val targetKind: NotificationTargetKind = NotificationTargetKind.NONE,
    val targetId: String? = null
)

enum class NotificationType {
    LIKE,
    COMMENT_LIKE,
    COMMENT,
    REPLY,
    FOLLOW,
    FOLLOW_REQUEST_ACCEPTED,
    COMMUNITY,
    SPACE,
    WELCOME,
    MENTION,
    STORY_REPLY,
    MESSAGE,
    GROUP_JOIN_REQUEST,
    GROUP_JOIN_APPROVED,
    GROUP_JOIN_REJECTED,
    GROUP_POST_REPORT,
    SYSTEM;

    companion object {
        /** Maps a `notifications.type` value to the UI type; null for unknown rows. */
        fun fromWire(value: String?): NotificationType? = when (value) {
            "post_like" -> LIKE
            "comment_like" -> COMMENT_LIKE
            "post_comment" -> COMMENT
            "comment_reply" -> REPLY
            "follow" -> FOLLOW
            "follow_request_accepted" -> FOLLOW_REQUEST_ACCEPTED
            "mention" -> MENTION
            "message" -> MESSAGE
            "story_reply" -> STORY_REPLY
            "group_join_request" -> GROUP_JOIN_REQUEST
            "group_join_approved" -> GROUP_JOIN_APPROVED
            "group_join_rejected" -> GROUP_JOIN_REJECTED
            "group_post_report" -> GROUP_POST_REPORT
            "system" -> SYSTEM
            else -> null
        }
    }
}

/** What tapping a notification should open, if the target still exists. */
enum class NotificationTargetKind {
    NONE,
    POST,
    USER,
    COMMUNITY,
    SPACE,
    CLIP,
    GROUP
}
