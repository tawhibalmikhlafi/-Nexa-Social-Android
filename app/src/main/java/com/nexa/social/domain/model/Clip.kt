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
    val isLocal: Boolean
)
