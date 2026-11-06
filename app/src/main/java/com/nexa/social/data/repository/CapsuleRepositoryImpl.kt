package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.core.security.CapsuleCipher
import com.nexa.social.data.local.CapsuleLocalDataSource
import com.nexa.social.data.local.StoredCapsule
import com.nexa.social.data.remote.CloudCapsule
import com.nexa.social.data.remote.SupabaseLifeDataSource
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.TimeCapsule
import com.nexa.social.domain.model.accountUserId
import com.nexa.social.domain.repository.CapsuleRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CapsuleRepositoryImpl(
    private val local: CapsuleLocalDataSource,
    private val userRepository: UserRepository,
    private val sessionFlow: Flow<SessionState>,
    private val remote: SupabaseLifeDataSource? = null
) : CapsuleRepository {

    private val stored = local.readCapsules().toMutableList()
    // Capsules are private: never expose cached records until the active account is known.
    private var visibleOwnerId: String? = null
    private val _capsules = MutableStateFlow<List<TimeCapsule>>(emptyList())

    override fun observeCapsules(): Flow<List<TimeCapsule>> =
        _capsules.asStateFlow().map { list ->
            list.sortedWith(compareBy<TimeCapsule> { it.isLocked() }.thenBy { it.unlockAt })
        }

    override suspend fun createCapsule(title: String, message: String, unlockAt: Long): Resource<TimeCapsule> {
        if (title.trim().length < 3 || message.trim().length < 3) {
            return Resource.Failure(FailureReason.REQUIRED)
        }
        if (unlockAt <= System.currentTimeMillis()) {
            return Resource.Failure(FailureReason.OPERATION_NOT_ALLOWED)
        }
        val userId = sessionFlow.first().accountUserId
            ?: return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        val user = userRepository.currentUser.first()
            ?: return Resource.Failure(FailureReason.GENERIC)
        val payload = CapsuleCipher.encrypt(message.trim())
            ?: return Resource.Failure(FailureReason.GENERIC)
        val record = StoredCapsule(
            id = Ids.newId(), title = title.trim(), cipherText = payload.cipherText, iv = payload.iv,
            createdAt = System.currentTimeMillis(), unlockAt = unlockAt,
            authorId = user.id.ifBlank { userId }, authorName = user.displayName
        )
        replace(record)
        remote?.saveCapsule(record.toCloud(userId))
        return Resource.Success(record.toDomain())
    }

    override suspend fun openCapsule(id: String): Resource<TimeCapsule> {
        val capsule = _capsules.value.firstOrNull { it.id == id }
            ?: return Resource.Failure(FailureReason.GENERIC)
        if (capsule.isLocked()) return Resource.Failure(FailureReason.OPERATION_NOT_ALLOWED)
        if (capsule.openedAt == null) {
            val record = stored.firstOrNull { it.id == id }
                ?: return Resource.Failure(FailureReason.GENERIC)
            val updated = record.copy(openedAt = System.currentTimeMillis())
            replace(updated)
            val uid = sessionFlow.first().accountUserId
            if (uid != null) remote?.saveCapsule(updated.toCloud(uid))
        }
        return Resource.Success(_capsules.value.first { it.id == id })
    }

    override suspend fun deleteCapsule(id: String): Boolean {
        val userId = sessionFlow.first().accountUserId ?: return false
        val capsule = _capsules.value.firstOrNull { it.id == id } ?: return false
        if (capsule.authorId != userId) return false
        if (remote != null && !remote.deleteCapsule(id)) return false
        remove(id)
        return true
    }

    override suspend fun refresh() {
        val uid = sessionFlow.first().accountUserId
        visibleOwnerId = uid
        _capsules.value = if (uid == null) emptyList() else stored
            .filter { it.authorId == uid }
            .map { it.toDomain() }
        val source = remote ?: return
        if (uid == null) return
        val mine = stored.filter { it.authorId == uid }
        // Upload local encrypted payloads first; local files are retained until the cloud snapshot
        // is successfully fetched and merged.
        mine.forEach { source.saveCapsule(it.toCloud(uid)) }
        val cloud = source.loadCapsules().filter { it.userId == uid }
        val displayName = userRepository.currentUser.first()?.displayName ?: "أنا"
        val cloudLocal = cloud.mapNotNull { value ->
            // CapsuleCipher uses this device's Android Keystore key. Never show undecryptable
            // ciphertext as an empty message on a different device.
            if (CapsuleCipher.decrypt(value.cipherText, value.iv) == null) return@mapNotNull null
            StoredCapsule(
                id = value.id, title = value.title, cipherText = value.cipherText, iv = value.iv,
                createdAt = value.createdAt, unlockAt = value.unlockAt,
                authorId = value.userId, authorName = displayName, openedAt = value.openedAt
            )
        }
        if (cloud.isEmpty() && stored.isNotEmpty()) return
        val otherAccounts = stored.filter { it.authorId != uid }
        // Keep local-first ordering so a failed upsert cannot replace a newer local openedAt.
        val merged = (mine + cloudLocal + otherAccounts).distinctBy { it.id }
        write(merged)
    }

    private suspend fun replace(record: StoredCapsule) =
        write(stored.filterNot { it.id == record.id } + record)

    private suspend fun remove(id: String) =
        write(stored.filterNot { it.id == id })

    private suspend fun write(nextStored: List<StoredCapsule>) {
        withContext(Dispatchers.IO) { local.writeCapsules(nextStored) }
        stored.clear()
        stored.addAll(nextStored)
        _capsules.value = visibleOwnerId?.let { owner ->
            nextStored.filter { it.authorId == owner }.map { it.toDomain() }
        }.orEmpty()
    }

    private fun StoredCapsule.toCloud(uid: String) = CloudCapsule(
        id = id, userId = uid, title = title, cipherText = cipherText, iv = iv,
        unlockAt = unlockAt, openedAt = openedAt, createdAt = createdAt
    )

    private fun StoredCapsule.toDomain(): TimeCapsule = TimeCapsule(
        id = id, title = title, message = CapsuleCipher.decrypt(cipherText, iv).orEmpty(),
        createdAt = createdAt, unlockAt = unlockAt, authorId = authorId,
        authorName = authorName, openedAt = openedAt
    )
}
