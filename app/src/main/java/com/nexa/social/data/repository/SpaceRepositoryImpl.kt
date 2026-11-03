package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.data.local.SpaceLocalDataSource
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.Space
import com.nexa.social.domain.model.SpaceCategory
import com.nexa.social.domain.model.SpaceChallenge
import com.nexa.social.domain.model.SpaceResource
import com.nexa.social.domain.repository.SpaceRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SpaceRepositoryImpl(
    private val local: SpaceLocalDataSource,
    private val userRepository: UserRepository,
    private val sessionFlow: Flow<SessionState>
) : SpaceRepository {

    private val _spaces = MutableStateFlow(local.readSpaces())

    override fun observeSpaces(communityId: String): Flow<List<Space>> =
        _spaces.asStateFlow().map { list ->
            list.filter { it.communityId == communityId }.sortedByDescending { it.createdAt }
        }

    override fun observeAllSpaces(): Flow<List<Space>> =
        _spaces.asStateFlow().map { list -> list.sortedByDescending { it.createdAt } }

    override fun observeSpace(id: String): Flow<Space?> =
        _spaces.asStateFlow().map { list -> list.firstOrNull { it.id == id } }

    override suspend fun createSpace(
        communityId: String,
        title: String,
        description: String,
        category: SpaceCategory
    ): Resource<Space> {
        if (title.trim().length < 3) return Resource.Failure(FailureReason.REQUIRED)
        val session = sessionFlow.first()
        if (session !is SessionState.LocalAccount && session !is SessionState.RemoteAccount) {
            return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        }
        val creator = userRepository.currentUser.first() ?: return Resource.Failure(FailureReason.GENERIC)
        val space = Space(
            id = Ids.newId(),
            communityId = communityId,
            title = title.trim(),
            description = description.trim(),
            category = category,
            creatorId = creator.id,
            creatorName = creator.displayName,
            createdAt = System.currentTimeMillis(),
            isMine = true,
            isLocal = true
        )
        _spaces.value = listOf(space) + _spaces.value
        persist()
        return Resource.Success(space)
    }

    override suspend fun updateSpace(
        spaceId: String,
        title: String,
        description: String,
        category: SpaceCategory
    ): Boolean {
        val existing = _spaces.value.firstOrNull { it.id == spaceId } ?: return false
        if (!existing.isMine) return false
        _spaces.value = _spaces.value.map {
            if (it.id == spaceId) it.copy(
                title = title.trim().ifBlank { it.title },
                description = description.trim(),
                category = category
            ) else it
        }
        persist()
        return true
    }

    override suspend fun deleteSpace(spaceId: String): Boolean {
        val existing = _spaces.value.firstOrNull { it.id == spaceId } ?: return false
        if (!existing.isMine) return false
        _spaces.value = _spaces.value.filterNot { it.id == spaceId }
        persist()
        return true
    }

    override suspend fun addResource(
        spaceId: String,
        title: String,
        description: String,
        reference: String?
    ): Boolean {
        if (title.isBlank()) return false
        val resource = SpaceResource(
            id = Ids.newId(),
            title = title.trim(),
            description = description.trim(),
            kind = com.nexa.social.domain.model.ResourceKind.NOTE,
            reference = reference?.trim()?.ifBlank { null }
        )
        updateSpaceField(spaceId) { it.copy(resources = it.resources + resource) }
        return true
    }

    override suspend fun removeResource(spaceId: String, resourceId: String): Boolean {
        val existing = _spaces.value.firstOrNull { it.id == spaceId } ?: return false
        if (!existing.isMine) return false
        updateSpaceField(spaceId) { it.copy(resources = it.resources.filterNot { r -> r.id == resourceId }) }
        return true
    }

    override suspend fun addChallenge(spaceId: String, prompt: String): Boolean {
        if (prompt.isBlank()) return false
        val challenge = SpaceChallenge(id = Ids.newId(), prompt = prompt.trim())
        updateSpaceField(spaceId) { it.copy(challenges = it.challenges + challenge) }
        return true
    }

    override suspend fun toggleChallenge(spaceId: String, challengeId: String): Boolean {
        val existing = _spaces.value.firstOrNull { it.id == spaceId } ?: return false
        updateSpaceField(spaceId) { space ->
            space.copy(
                challenges = space.challenges.map {
                    if (it.id == challengeId) it.copy(doneLocally = !it.doneLocally) else it
                }
            )
        }
        return existing.challenges.any { it.id == challengeId }
    }

    private suspend fun updateSpaceField(spaceId: String, transform: (Space) -> Space) {
        _spaces.value = _spaces.value.map { if (it.id == spaceId) transform(it) else it }
        persist()
    }

    private suspend fun persist() {
        val snapshot = _spaces.value
        withContext(Dispatchers.IO) { local.writeSpaces(snapshot) }
    }
}
