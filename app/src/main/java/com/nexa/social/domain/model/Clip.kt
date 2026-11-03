package com.nexa.social.domain.model

/**
 * A short vertical video. The video file is chosen from the device by the user or provided as a
 * clearly-labelled sample; there is no streaming library behind this yet.
 */
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
