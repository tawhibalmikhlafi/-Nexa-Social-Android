package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.data.local.GatheringLocalDataSource
import com.nexa.social.data.remote.SupabaseLifeDataSource
import com.nexa.social.domain.model.Gathering
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.accountUserId
import com.nexa.social.domain.repository.GatheringRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GatheringRepositoryImpl(
    private val local: GatheringLocalDataSource,
    private val userRepository: UserRepository,
    private val sessionFlow: Flow<SessionState>,
    private val remote: SupabaseLifeDataSource? = null
) : GatheringRepository {

    private val _gatherings = MutableStateFlow(local.readGatherings().sortedBy { it.startsAt })
    // Keep other accounts' local-only records on disk while exposing the active account's
    // cloud view. They can be migrated if that account signs in again.
    private var archivedGatherings: List<Gathering> = emptyList()

    override fun observeGatherings(): Flow<List<Gathering>> =
        _gatherings.asStateFlow().map { list -> list.sortedBy { it.startsAt } }

    override fun observeGathering(id: String): Flow<Gathering?> =
        _gatherings.asStateFlow().map { list -> list.firstOrNull { it.id == id } }

    override fun observeUpcoming(): Flow<List<Gathering>> =
        _gatherings.asStateFlow().map { list -> list.filterNot { it.isPast() }.sortedBy { it.startsAt } }

    override suspend fun createGathering(
        title: String, description: String, location: String, isVirtual: Boolean,
        startsAt: Long, durationMinutes: Int, capacity: Int, tags: List<String>
    ): Resource<Gathering> {
        if (title.trim().length < 3) return Resource.Failure(FailureReason.REQUIRED)
        if (startsAt <= System.currentTimeMillis()) return Resource.Failure(FailureReason.OPERATION_NOT_ALLOWED)
        val userId = sessionFlow.first().accountUserId
            ?: return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        val user = userRepository.currentUser.first()
            ?: return Resource.Failure(FailureReason.GENERIC)
        val ownerId = user.id.ifBlank { userId }
        val gathering = Gathering(
            id = Ids.newId(), title = title.trim(), description = description.trim(),
            hostId = ownerId, hostName = user.displayName,
            location = location.trim().ifBlank { if (isVirtual) "Nexa Virtual Room" else "Nexa" },
            isVirtual = isVirtual, startsAt = startsAt,
            durationMinutes = durationMinutes.coerceIn(15, 24 * 60),
            capacity = capacity.coerceAtLeast(0), attendeeIds = listOf(ownerId),
            tags = tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct().take(5),
            createdAt = System.currentTimeMillis(), isMine = true
        )
        _gatherings.value = (_gatherings.value + gathering).sortedBy { it.startsAt }
        persist()
        // Local-first: the record remains queued in the local file if the network is unavailable.
        remote?.saveGathering(gathering)
        return Resource.Success(gathering)
    }

    override suspend fun toggleAttendance(id: String): Resource<Gathering> {
        val userId = sessionFlow.first().accountUserId
            ?: return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        val existing = _gatherings.value.firstOrNull { it.id == id }
            ?: return Resource.Failure(FailureReason.GENERIC)
        if (existing.isPast()) return Resource.Failure(FailureReason.OPERATION_NOT_ALLOWED)
        val attending = existing.isAttending(userId)
        if (!attending && existing.isFull()) return Resource.Failure(FailureReason.OPERATION_NOT_ALLOWED)
        val updated = existing.copy(
            attendeeIds = if (attending) existing.attendeeIds - userId else existing.attendeeIds + userId
        )
        if (remote != null && !remote.setAttendance(id, !attending)) {
            return Resource.Failure(FailureReason.NETWORK)
        }
        _gatherings.value = _gatherings.value.map { if (it.id == id) updated else it }
        persist()
        return Resource.Success(updated)
    }

    override suspend fun deleteGathering(id: String): Boolean {
        val userId = sessionFlow.first().accountUserId ?: return false
        val existing = _gatherings.value.firstOrNull { it.id == id } ?: return false
        if (existing.hostId != userId) return false
        if (remote != null && !remote.deleteGathering(id)) return false
        _gatherings.value = _gatherings.value.filterNot { it.id == id }
        persist()
        return true
    }

    override suspend fun refresh() {
        val source = remote ?: return
        val uid = sessionFlow.first().accountUserId ?: return
        val allLocal = withContext(Dispatchers.IO) { local.readGatherings() }
        val localOwned = allLocal.filter { it.hostId == uid && !it.id.startsWith("sample_") }
        archivedGatherings = allLocal.filterNot { it.hostId == uid && !it.id.startsWith("sample_") }
        // Idempotent upsert is the migration checkpoint: do not replace local data until it succeeds.
        localOwned.forEach { source.saveGathering(it) }
        val cloud = source.loadGatherings()
        if (cloud.isEmpty() && allLocal.isNotEmpty()) return
        // Prefer the local copy for matching IDs: if an upsert failed, an older cloud
        // version must not overwrite the still-pending local edit.
        val merged = (localOwned + cloud).distinctBy { it.id }.sortedBy { it.startsAt }
        _gatherings.value = merged
        persist()
    }

    private suspend fun persist() {
        val snapshot = _gatherings.value
        val durableSnapshot = (snapshot + archivedGatherings).distinctBy { it.id }
        withContext(Dispatchers.IO) { local.writeGatherings(durableSnapshot) }
    }
}
