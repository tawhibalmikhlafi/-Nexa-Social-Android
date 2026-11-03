package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.domain.model.Group
import com.nexa.social.domain.model.GroupComment
import com.nexa.social.domain.model.GroupInvite
import com.nexa.social.domain.model.GroupMember
import com.nexa.social.domain.model.GroupPost
import com.nexa.social.domain.model.GroupSearchResult
import com.nexa.social.domain.model.GroupStats
import com.nexa.social.domain.model.JoinRequest
import com.nexa.social.domain.model.ReactionKind
import com.nexa.social.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal object UnconfiguredGroupRepository : GroupRepository {
    override fun observeMyGroups(): Flow<List<Group>> = flowOf(emptyList())

    override suspend fun refreshMyGroups(): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun searchGroups(
        query: String,
        category: String?,
    ): Resource<List<GroupSearchResult>> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun getGroup(groupId: String): Resource<Group> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun getGroupStats(groupId: String): Resource<GroupStats> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun createGroup(
        name: String,
        description: String,
        privacy: String,
        category: String?,
    ): Resource<String> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun updateGroup(
        groupId: String,
        name: String?,
        description: String?,
        rules: String?,
        category: String?,
        privacy: String?,
        avatarUrl: String?,
        coverUrl: String?,
        allowMemberPosts: Boolean?,
        allowMemberInvites: Boolean?,
        requireJoinApproval: Boolean?,
    ): Resource<Unit> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun deleteGroup(groupId: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun setGroupArchived(groupId: String, archived: Boolean): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun uploadGroupImage(
        groupId: String,
        localUri: String,
        cover: Boolean,
    ): Resource<String> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun joinOrRequest(groupId: String, message: String?): Resource<String> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun leaveGroup(groupId: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun getJoinRequests(groupId: String): Resource<List<JoinRequest>> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun approveJoinRequest(requestId: String, approve: Boolean): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun getMembers(groupId: String): Resource<List<GroupMember>> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun setMemberRole(
        groupId: String,
        userId: String,
        role: String,
    ): Resource<Unit> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun removeMember(
        groupId: String,
        userId: String,
        ban: Boolean,
    ): Resource<Unit> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun unbanMember(groupId: String, userId: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun getGroupPosts(
        groupId: String,
        offset: Int,
        limit: Int,
    ): Resource<List<GroupPost>> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun createPost(
        groupId: String,
        content: String,
        mediaUrl: String?,
    ): Resource<String> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun deletePost(postId: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun reactToPost(postId: String, kind: ReactionKind?): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun getPostComments(postId: String): Resource<List<GroupComment>> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun addComment(
        postId: String,
        content: String,
        parentId: String?,
    ): Resource<String> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun deleteComment(commentId: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun inviteUser(groupId: String, userId: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun getMyInvites(): Resource<List<GroupInvite>> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun respondToInvite(inviteId: String, accept: Boolean): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)
}
