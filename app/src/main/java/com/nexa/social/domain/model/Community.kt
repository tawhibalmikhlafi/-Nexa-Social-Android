package com.nexa.social.domain.model

data class Community(
    val id: String,
    val name: String,
    val description: String,
    val coverColorSeed: Long,
    val coverUri: String?,
    val category: CommunityCategory,
    val sampleMemberCount: Int,
    val isJoinedLocally: Boolean,
    val isNew: Boolean,
    val channels: List<Channel>,
    val rules: List<String>,
    val events: List<CommunityEvent>,
    val creatorId: String? = null,
    val createdByMe: Boolean = false,
    val privacy: WorldPrivacy = WorldPrivacy.PUBLIC_LISTED,
    val createdAt: Long = 0L,
    val pinnedPostIds: List<String> = emptyList(),
    val isHiddenLocally: Boolean = false
)

data class Channel(
    val id: String,
    val name: String,
    val topic: String
)

data class CommunityEvent(
    val id: String,
    val title: String,
    val startsAt: Long,
    val location: String
)

data class CommunityMember(
    val userId: String,
    val communityId: String,
    val username: String,
    val displayName: String,
    val avatarUri: String?,
    val role: CommunityRole,
    val joinedAt: Long,
    val isSample: Boolean = true
)

enum class CommunityRole {
    MEMBER,
    MODERATOR,
    OWNER
}

enum class CommunityCategory {
    GAMING,
    TECH,
    EDUCATION,
    CREATIVE,
    BUSINESS,
    LOCAL,
    OTHER
}

/** Visibility of a world. Private worlds are local-only until a server enforces membership. */
enum class WorldPrivacy {
    PUBLIC_LISTED,
    PUBLIC_UNLISTED,
    PRIVATE
}
