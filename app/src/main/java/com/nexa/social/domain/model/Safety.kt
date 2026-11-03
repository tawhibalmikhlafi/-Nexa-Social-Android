package com.nexa.social.domain.model

/**
 * Safety controls. Blocks are mirrored to Supabase when signed in and kept on this device
 * otherwise. Muting, hiding and reporting stay on this device; a report does NOT reach any
 * moderation team until a report backend exists.
 */
data class BlockedUser(
    val userId: String,
    val username: String,
    val displayName: String,
    val blockedAt: Long
)

data class MutedUser(
    val userId: String,
    val username: String,
    val displayName: String,
    val mutedAt: Long
)

enum class ContentKind {
    POST,
    COMMUNITY_POST,
    CLIP,
    COMMENT
}

data class HiddenContent(
    val contentId: String,
    val kind: ContentKind,
    val hiddenAt: Long
)

enum class ReportTargetType {
    POST,
    COMMENT,
    USER,
    CLIP,
    COMMUNITY
}

enum class ReportReason {
    SPAM,
    HARASSMENT,
    HATE_SPEECH,
    MISINFORMATION,
    SEXUAL_CONTENT,
    VIOLENCE,
    SELF_HARM,
    IMPERSONATION,
    OTHER
}

data class ReportRecord(
    val id: String,
    val targetType: ReportTargetType,
    val targetId: String,
    val reason: ReportReason,
    val note: String,
    val createdAt: Long
)
