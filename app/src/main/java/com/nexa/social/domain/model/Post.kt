package com.nexa.social.domain.model

data class Post(
    val id: String,
    val author: UserSummary,
    val text: String,
    val imageUri: String?,
    val imageUris: List<String> = emptyList(),
    val title: String? = null,
    val tags: List<String> = emptyList(),
    val audience: Audience,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val likeCount: Int,
    val commentCount: Int,
    val shareCount: Int = 0,
    val likedByMe: Boolean,
    val myReaction: String? = null,
    val savedByMe: Boolean,
    val communityId: String?,
    val isLocal: Boolean,
    val isHiddenLocally: Boolean = false,
    val pinned: Boolean = false
) {
    /** All attached images, tolerating the legacy single-image field. */
    fun allImages(): List<String> = imageUris.ifEmpty { listOfNotNull(imageUri) }

    val isEdited: Boolean get() = updatedAt > createdAt
}

enum class Audience {
    PUBLIC_LOCAL,
    FOLLOWERS,
    ONLY_ME
}

/** A published post inside a community (world). */
data class CommunityPost(
    val id: String,
    val communityId: String,
    val author: UserSummary,
    val text: String,
    val imageUri: String?,
    val imageUris: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean,
    val myReaction: String? = null,
    val isLocal: Boolean
)

data class PostReaction(
    val user: UserSummary,
    val emoji: String = "❤️",
    val createdAt: Long = System.currentTimeMillis()
)
