package com.nexa.social.domain.model

data class FollowRequest(
    val id: String,
    val requester: User,
    val createdAt: String
)
