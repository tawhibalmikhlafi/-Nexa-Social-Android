package com.nexa.social.domain.model

/**
 * Safety controls. Blocks and reports sync to Supabase for remote accounts. Local accounts keep
 * their safety state on-device; muting and hiding remain device-local preferences.
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
