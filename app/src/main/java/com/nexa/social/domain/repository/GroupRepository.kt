package com.nexa.social.domain.repository

import com.nexa.social.core.common.Resource
import com.nexa.social.domain.model.Group
import com.nexa.social.domain.model.GroupComment
import com.nexa.social.domain.model.GroupInvite
import com.nexa.social.domain.model.GroupMember
import com.nexa.social.domain.model.GroupPost
import com.nexa.social.domain.model.GroupPostReport
import com.nexa.social.domain.model.GroupSearchResult
import com.nexa.social.domain.model.GroupStats
import com.nexa.social.domain.model.JoinRequest
import com.nexa.social.domain.model.ReactionKind
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun observeMyGroups(): Flow<List<Group>>
    suspend fun refreshMyGroups(): Resource<Unit>
    suspend fun searchGroups(query: String, category: String? = null): Resource<List<GroupSearchResult>>
    suspend fun getGroup(groupId: String): Resource<Group>
    suspend fun getGroupStats(groupId: String): Resource<GroupStats>

    suspend fun createGroup(
        name: String,
        description: String,
        privacy: String,
        category: String?,
    ): Resource<String>

    suspend fun updateGroup(
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
    ): Resource<Unit>

    suspend fun deleteGroup(groupId: String): Resource<Unit>
    suspend fun setGroupArchived(groupId: String, archived: Boolean): Resource<Unit>

    /**
     * Uploads a local image to the group-media bucket and returns its public URL.
     * The caller is responsible for persisting it via [updateGroup].
     */
    suspend fun uploadGroupImage(groupId: String, localUri: String, cover: Boolean): Resource<String>

    suspend fun joinOrRequest(groupId: String, message: String?): Resource<String>
    suspend fun leaveGroup(groupId: String): Resource<Unit>
    suspend fun getJoinRequests(groupId: String): Resource<List<JoinRequest>>
    suspend fun approveJoinRequest(requestId: String, approve: Boolean): Resource<Unit>

    suspend fun getMembers(groupId: String): Resource<List<GroupMember>>
    suspend fun setMemberRole(groupId: String, userId: String, role: String): Resource<Unit>
    suspend fun removeMember(groupId: String, userId: String, ban: Boolean): Resource<Unit>
    suspend fun unbanMember(groupId: String, userId: String): Resource<Unit>

    suspend fun getGroupPosts(
        groupId: String,
        offset: Int = 0,
        limit: Int = 20,
    ): Resource<List<GroupPost>>

    suspend fun createPost(groupId: String, content: String, mediaUrl: String?): Resource<String>
    suspend fun deletePost(postId: String): Resource<Unit>
    suspend fun submitGroupPostReport(groupId: String, postId: String, postPreview: String, reason: String, note: String): Resource<Unit>
    suspend fun getGroupPostReports(groupId: String): Resource<List<GroupPostReport>>
    suspend fun reviewGroupPostReport(reportId: String): Resource<Unit>
    suspend fun reactToPost(postId: String, kind: ReactionKind?): Resource<Unit>

    suspend fun getPostComments(postId: String): Resource<List<GroupComment>>
    suspend fun addComment(postId: String, content: String, parentId: String?): Resource<String>
    suspend fun deleteComment(commentId: String): Resource<Unit>

    suspend fun inviteUser(groupId: String, userId: String): Resource<Unit>
    suspend fun getMyInvites(): Resource<List<GroupInvite>>
    suspend fun respondToInvite(inviteId: String, accept: Boolean): Resource<Unit>
}
