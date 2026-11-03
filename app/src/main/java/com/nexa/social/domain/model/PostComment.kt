package com.nexa.social.domain.model

/**
 * A comment on a post. Remote comments live in Supabase and local comments are device-only
 * sample content; the repository decides which source to expose.
 */
data class PostComment(
    val id: String,
    val postId: String,
    val author: UserSummary,
    val text: String,
    val imageUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val parentId: String? = null,
    val likeCount: Int = 0,
    val likedByMe: Boolean = false,
    val isMine: Boolean = false,
    val canDelete: Boolean = false,
    val isLocal: Boolean = true
) {
    val isEdited: Boolean get() = updatedAt > createdAt
}
