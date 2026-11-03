package com.nexa.social.domain.model

/**
 * A story shown in the tray. Remote stories are published to Supabase and visible to every
 * signed-in account; local/sample stories remain on this device.
 */
data class Story(
    val id: String,
    val author: UserSummary,
    val imageUri: String?,
    val text: String?,
    val createdAt: Long,
    val expiresAt: Long,
    val viewedLocally: Boolean = false,
    val isMine: Boolean = false,
    val isLocal: Boolean = true
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now >= expiresAt
}

/** Stories grouped by author for the tray at the top of the home feed. */
data class StoryGroup(
    val author: UserSummary,
    val stories: List<Story>
) {
    val hasUnseen: Boolean get() = stories.any { !it.viewedLocally }
}

/**
 * One person in the owner-only audience of a story. A member may have viewed the story,
 * replied to it, or both.
 */
data class StoryAudienceMember(
    val user: UserSummary,
    val viewedAt: Long?,
    val interactedAt: Long?,
    val interactionText: String?,
    val interactionCount: Int = 0
) {
    val hasInteracted: Boolean get() = interactionCount > 0 || interactedAt != null
    val lastActivityAt: Long get() = interactedAt ?: viewedAt ?: 0L
}
