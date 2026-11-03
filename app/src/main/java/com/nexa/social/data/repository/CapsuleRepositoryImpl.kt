package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.core.security.CapsuleCipher
import com.nexa.social.data.local.CapsuleLocalDataSource
import com.nexa.social.data.local.StoredCapsule
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
    private val sessionFlow: Flow<SessionState>
) : CapsuleRepository {

    private val stored = local.readCapsules().toMutableList()
    private val _capsules = MutableStateFlow(stored.map { it.toDomain() })

    override fun observeCapsules(): Flow<List<TimeCapsule>> =
        _capsules.asStateFlow().map { list ->
            list.sortedWith(compareBy<TimeCapsule> { it.isLocked() }.thenBy { it.unlockAt })
        }

    override suspend fun createCapsule(
        title: String,
        message: String,
        unlockAt: Long
    ): Resource<TimeCapsule> {
        if (title.trim().length < 3) return Resource.Failure(FailureReason.REQUIRED)
        if (message.trim().length < 3) return Resource.Failure(FailureReason.REQUIRED)
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
            id = Ids.newId(),
            title = title.trim(),
            cipherText = payload.cipherText,
            iv = payload.iv,
            createdAt = System.currentTimeMillis(),
            unlockAt = unlockAt,
            authorId = user.id.ifBlank { userId },
            authorName = user.displayName
        )
        replace(record)
        return Resource.Success(record.toDomain())
    }

    override suspend fun openCapsule(id: String): Resource<TimeCapsule> {
        val capsule = _capsules.value.firstOrNull { it.id == id }
            ?: return Resource.Failure(FailureReason.GENERIC)
        if (capsule.isLocked()) return Resource.Failure(FailureReason.OPERATION_NOT_ALLOWED)
        if (capsule.openedAt == null) {
            val record = stored.firstOrNull { it.id == id }
                ?: return Resource.Failure(FailureReason.GENERIC)
            replace(record.copy(openedAt = System.currentTimeMillis()))
        }
        return Resource.Success(_capsules.value.first { it.id == id })
    }

    override suspend fun deleteCapsule(id: String): Boolean {
        val userId = sessionFlow.first().accountUserId ?: return false
        val capsule = _capsules.value.firstOrNull { it.id == id } ?: return false
        if (capsule.authorId != userId) return false
        remove(id)
        return true
    }

    private suspend fun replace(record: StoredCapsule) {
        val nextStored = stored
            .filterNot { it.id == record.id }
            .plus(record)
        write(nextStored)
    }

    private suspend fun remove(id: String) {
        write(stored.filterNot { it.id == id })
    }

    private suspend fun write(nextStored: List<StoredCapsule>) {
        withContext(Dispatchers.IO) { local.writeCapsules(nextStored) }
        stored.clear()
        stored.addAll(nextStored)
        _capsules.value = nextStored.map { it.toDomain() }
    }

    private fun StoredCapsule.toDomain(): TimeCapsule = TimeCapsule(
        id = id,
        title = title,
        message = CapsuleCipher.decrypt(cipherText, iv).orEmpty(),
        createdAt = createdAt,
        unlockAt = unlockAt,
        authorId = authorId,
        authorName = authorName,
        openedAt = openedAt
    )
}
