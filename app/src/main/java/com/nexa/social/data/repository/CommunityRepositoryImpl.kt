package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.data.local.CommunityLocalDataSource
import com.nexa.social.data.local.CommunityMembershipState
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.CommunityCategory
import com.nexa.social.domain.model.CommunityMember
import com.nexa.social.domain.model.CommunityPost
import com.nexa.social.domain.model.CommunityRole
import com.nexa.social.domain.model.CounterKind
import com.nexa.social.domain.model.GuestIdentity
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.model.WorldPrivacy
import com.nexa.social.domain.repository.AchievementRepository
import com.nexa.social.domain.repository.CommunityRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CommunityRepositoryImpl(
    private val local: CommunityLocalDataSource,
    private val userRepository: UserRepository,
    private val sessionFlow: Flow<SessionState>,
    private val achievements: AchievementRepository
) : CommunityRepository {

    private val _membership = MutableStateFlow(local.readMembership())
    private val _posts = MutableStateFlow(local.readPosts())
    private val _localCommunities = MutableStateFlow(local.readLocalCommunities())

    private fun allCommunities(): List<Community> {
        val joined = _membership.value.associate { it.communityId to it.isJoined }
        val deleted = local.readDeletedIds().toSet()
        return (SampleData.communities + _localCommunities.value)
            .filterNot { it.id in deleted }
            .map { community ->
                community.copy(isJoinedLocally = joined[community.id] ?: community.isJoinedLocally)
            }
    }

    override fun observeCommunities(): Flow<List<Community>> =
        combineFlows { allCommunities() }

    override fun observeJoinedCommunities(): Flow<List<Community>> =
        combineFlows { allCommunities().filter { it.isJoinedLocally } }

    override fun observeCommunity(id: String): Flow<Community?> =
        combineFlows { allCommunities().firstOrNull { it.id == id } }

    private fun <T> combineFlows(transform: () -> T): Flow<T> =
        kotlinx.coroutines.flow.combine(_membership.asStateFlow(), _localCommunities.asStateFlow()) { _, _ ->
            transform()
        }

    override suspend fun toggleMembership(id: String) {
        val current = _membership.value.firstOrNull { it.communityId == id }
            ?: CommunityMembershipState(id, false)
        val updated = current.copy(isJoined = !current.isJoined)
        _membership.value = _membership.value.filterNot { it.communityId == id } + updated
        val snapshot = _membership.value
        withContext(Dispatchers.IO) { local.writeMembership(snapshot) }
        if (updated.isJoined) achievements.record(CounterKind.WORLDS_JOINED)
    }

    override fun observeCommunityMembers(communityId: String): Flow<List<CommunityMember>> =
        combineFlows { membersOf(communityId) }

    private fun membersOf(communityId: String): List<CommunityMember> {
        val community = allCommunities().firstOrNull { it.id == communityId } ?: return emptyList()
        val owner = community.creatorId?.let { id ->
            SampleData.userById(id)?.let { user ->
                CommunityMember(
                    userId = user.id,
                    communityId = communityId,
                    username = user.username,
                    displayName = user.displayName,
                    avatarUri = user.avatarUri,
                    role = CommunityRole.OWNER,
                    joinedAt = community.createdAt,
                    isSample = true
                )
            }
        }
        // Sample members are clearly marked; they are not real connected users.
        val sampleMembers = SampleData.users.take(3).map { user ->
            CommunityMember(
                userId = user.id,
                communityId = communityId,
                username = user.username,
                displayName = user.displayName,
                avatarUri = user.avatarUri,
                role = CommunityRole.MEMBER,
                joinedAt = community.createdAt,
                isSample = true
            )
        }
        return listOfNotNull(owner) + sampleMembers
    }

    override fun observeCommunityPosts(communityId: String): Flow<List<CommunityPost>> =
        _posts.asStateFlow().map { posts ->
            posts.filter { it.communityId == communityId }
                .sortedWith(compareByDescending<CommunityPost> { it.pinned }.thenByDescending { it.createdAt })
        }

    override suspend fun createCommunityPost(
        communityId: String,
        text: String,
        imageUris: List<String>
    ): CommunityPost {
        val author = currentIdentity()
        val post = CommunityPost(
            id = Ids.newId(),
            communityId = communityId,
            author = author,
            text = text.trim(),
            imageUri = imageUris.firstOrNull(),
            imageUris = imageUris,
            createdAt = System.currentTimeMillis(),
            likeCount = 0,
            commentCount = 0,
            likedByMe = false,
            isLocal = true
        )
        _posts.value = listOf(post) + _posts.value
        persistPosts()
        return post
    }

    override suspend fun deleteCommunityPost(postId: String): Boolean {
        val existing = _posts.value.firstOrNull { it.id == postId } ?: return false
        if (existing.author.id != currentIdentity().id) return false
        _posts.value = _posts.value.filterNot { it.id == postId }
        persistPosts()
        return true
    }

    override suspend fun toggleCommunityPostLike(communityId: String, postId: String, reaction: String?) {
        _posts.value = _posts.value.map { post ->
            if (post.id != postId) post else {
                val currentReaction = post.myReaction
                val newReaction: String? = when {
                    reaction != null && currentReaction != reaction -> reaction
                    reaction != null && currentReaction == reaction -> null
                    else -> if (currentReaction != null) null else "👍"
                }
                val liked = newReaction != null
                val delta = when {
                    currentReaction == null && newReaction != null -> 1
                    currentReaction != null && newReaction == null -> -1
                    else -> 0
                }
                post.copy(
                    likedByMe = liked,
                    myReaction = newReaction,
                    likeCount = (post.likeCount + delta).coerceAtLeast(0)
                )
            }
        }
        persistPosts()
    }

    override suspend fun createCommunity(
        name: String,
        description: String,
        category: CommunityCategory,
        privacy: WorldPrivacy,
        rules: List<String>
    ): Resource<Community> {
        if (name.trim().length < 3) return Resource.Failure(FailureReason.REQUIRED)
        val session = sessionFlow.first()
        if (session !is SessionState.LocalAccount && session !is SessionState.RemoteAccount) {
            return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        }
        val creator = userRepository.currentUser.first() ?: return Resource.Failure(FailureReason.GENERIC)
        val now = System.currentTimeMillis()
        val community = Community(
            id = Ids.newId(),
            name = name.trim(),
            description = description.trim(),
            coverColorSeed = categoryColor(category),
            coverUri = null,
            category = category,
            sampleMemberCount = 1,
            isJoinedLocally = true,
            isNew = true,
            channels = emptyList(),
            rules = rules.map { it.trim() }.filter { it.isNotBlank() },
            events = emptyList(),
            creatorId = creator.id,
            createdByMe = true,
            privacy = privacy,
            createdAt = now
        )
        _localCommunities.value = _localCommunities.value + community
        _membership.value = _membership.value + CommunityMembershipState(community.id, true)
        withContext(Dispatchers.IO) {
            local.writeLocalCommunities(_localCommunities.value)
            local.writeMembership(_membership.value)
        }
        achievements.record(CounterKind.WORLDS_CREATED)
        return Resource.Success(community)
    }

    override suspend fun updateCommunity(
        id: String,
        name: String,
        description: String,
        category: CommunityCategory,
        privacy: WorldPrivacy
    ): Community? {
        val existing = _localCommunities.value.firstOrNull { it.id == id } ?: return null
        if (!existing.createdByMe) return null
        val updated = existing.copy(
            name = name.trim().ifBlank { existing.name },
            description = description.trim(),
            category = category,
            privacy = privacy
        )
        _localCommunities.value = _localCommunities.value.map { if (it.id == id) updated else it }
        withContext(Dispatchers.IO) { local.writeLocalCommunities(_localCommunities.value) }
        return updated
    }

    override suspend fun deleteCommunity(id: String): Boolean {
        val existing = _localCommunities.value.firstOrNull { it.id == id } ?: return false
        if (!existing.createdByMe) return false
        _localCommunities.value = _localCommunities.value.filterNot { it.id == id }
        withContext(Dispatchers.IO) { local.writeLocalCommunities(_localCommunities.value) }
        return true
    }

    override suspend fun searchCommunities(query: String): List<Community> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        return allCommunities().filter {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
    }

    private suspend fun currentIdentity(): UserSummary {
        val session = sessionFlow.first()
        return when (session) {
            is SessionState.LocalAccount, is SessionState.RemoteAccount ->
                userRepository.currentUser.first()?.let { UserSummary.from(it) } ?: GuestIdentity.summary
            else -> GuestIdentity.summary
        }
    }

    private suspend fun persistPosts() {
        val snapshot = _posts.value
        withContext(Dispatchers.IO) { local.writePosts(snapshot) }
    }

    private fun categoryColor(category: CommunityCategory): Long = when (category) {
        CommunityCategory.GAMING -> 0xFF7C4DFF
        CommunityCategory.TECH -> 0xFF3D7BFF
        CommunityCategory.EDUCATION -> 0xFF00D0E0
        CommunityCategory.CREATIVE -> 0xFFB388FF
        CommunityCategory.BUSINESS -> 0xFF27C08A
        CommunityCategory.LOCAL -> 0xFFFFA53D
        CommunityCategory.OTHER -> 0xFF6C7A94
    }
}
