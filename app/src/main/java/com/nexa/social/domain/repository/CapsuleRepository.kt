package com.nexa.social.domain.repository

import com.nexa.social.core.common.Resource
import com.nexa.social.domain.model.TimeCapsule
import kotlinx.coroutines.flow.Flow

/**
 * Encrypted, device-local time capsules. A capsule can only be opened after [TimeCapsule.unlockAt];
 * the repository enforces the lock even if the UI clock is manipulated forward and back.
 */
interface CapsuleRepository {
    fun observeCapsules(): Flow<List<TimeCapsule>>
    suspend fun createCapsule(title: String, message: String, unlockAt: Long): Resource<TimeCapsule>
    suspend fun openCapsule(id: String): Resource<TimeCapsule>
    suspend fun deleteCapsule(id: String): Boolean
    suspend fun refresh() {}
}
