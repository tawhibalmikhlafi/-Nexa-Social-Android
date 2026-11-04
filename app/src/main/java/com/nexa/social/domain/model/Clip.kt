package com.nexa.social.domain.model

/** A short-form video stored locally for offline playback or remotely in Supabase Storage. */
data class Clip(
    val id: String,
    val author: UserSummary,
    val videoUri: String?,
    val caption: String,
    val createdAt: Long,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean,
    val savedByMe: Boolean,
    val isLocal: Boolean,
    val viewCount: Long = 0L,
    val coverUrl: String? = null
)

data class ClipComment(
    val id: String,
    val clipId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val content: String,
    val createdAt: Long,
    val isAuthor: Boolean
)
