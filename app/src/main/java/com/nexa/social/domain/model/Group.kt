package com.nexa.social.domain.model

enum class GroupPrivacy(val wire: String) {
    PUBLIC("public"),
    PRIVATE("private");

    companion object {
        fun fromWire(value: String?): GroupPrivacy =
            entries.firstOrNull { it.wire == value } ?: PUBLIC
    }
}

enum class GroupRole(val wire: String) {
    OWNER("owner"),
    ADMIN("admin"),
    MEMBER("member");

    companion object {
        fun fromWire(value: String?): GroupRole =
            entries.firstOrNull { it.wire == value } ?: MEMBER
    }
}

enum class GroupMemberStatus(val wire: String) {
    PENDING("pending"),
    ACTIVE("active"),
    BANNED("banned");

    companion object {
        fun fromWire(value: String?): GroupMemberStatus =
            entries.firstOrNull { it.wire == value } ?: PENDING
    }
}

enum class JoinRequestStatus(val wire: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    companion object {
        fun fromWire(value: String?): JoinRequestStatus =
            entries.firstOrNull { it.wire == value } ?: PENDING
    }
}

enum class ReactionKind(val wire: String) {
    LIKE("like"),
    LOVE("love"),
    HAHA("haha"),
    SAD("sad"),
    ANGRY("angry");

    companion object {
        fun fromWire(value: String?): ReactionKind? =
            entries.firstOrNull { it.wire == value }
    }
}

data class Group(
    val id: String,
    val ownerId: String,
    val name: String,
    val description: String,
    val rules: String,
    val category: String?,
    val slug: String?,
    val avatarUrl: String?,
    val coverUrl: String?,
    val privacy: GroupPrivacy,
    val memberCount: Int,
    val createdAt: Long,
    val allowMemberPosts: Boolean,
    val allowMemberInvites: Boolean,
    val requireJoinApproval: Boolean,
    val isArchived: Boolean,
    val myRole: GroupRole? = null,
    val myStatus: GroupMemberStatus? = null,
) {
    val isMember: Boolean get() = myStatus == GroupMemberStatus.ACTIVE
    val canPost: Boolean
        get() = isMember && !isArchived &&
            (allowMemberPosts || myRole == GroupRole.OWNER || myRole == GroupRole.ADMIN)
    val canManage: Boolean get() = myRole == GroupRole.OWNER || myRole == GroupRole.ADMIN
    val isOwner: Boolean get() = myRole == GroupRole.OWNER
}

data class GroupMember(
    val userId: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String?,
    val role: GroupRole,
    val status: GroupMemberStatus,
    val joinedAt: Long,
)

data class GroupPost(
    val id: String,
    val groupId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val content: String,
    val mediaUrl: String?,
    val createdAt: Long,
    val editedAt: Long?,
    val reactionCount: Int,
    val commentCount: Int,
    val myReaction: ReactionKind?,
    val isAuthor: Boolean = false,
)

data class GroupComment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val content: String,
    val parentId: String?,
    val createdAt: Long,
    val editedAt: Long?,
    val isAuthor: Boolean = false,
)

data class GroupInvite(
    val id: String,
    val groupId: String,
    val groupName: String,
    val groupAvatarUrl: String?,
    val inviterId: String,
    val inviterName: String,
    val createdAt: Long,
)

data class JoinRequest(
    val id: String,
    val groupId: String,
    val userId: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String?,
    val message: String?,
    val status: JoinRequestStatus,
    val createdAt: Long,
)

data class GroupStats(
    val totalMembers: Int,
    val activeMembers: Int,
    val pendingMembers: Int,
    val bannedMembers: Int,
    val totalPosts: Int,
    val totalComments: Int,
    val postsLast7Days: Int,
)

data class GroupSearchResult(
    val id: String,
    val name: String,
    val description: String,
    val avatarUrl: String?,
    val coverUrl: String?,
    val privacy: GroupPrivacy,
    val memberCount: Int,
    val category: String?,
    val createdAt: Long,
)


data class GroupPostReport(
    val id: String,
    val postId: String,
    val reporterId: String,
    val reason: String,
    val note: String,
    val createdAt: Long,
    val status: String,
)
