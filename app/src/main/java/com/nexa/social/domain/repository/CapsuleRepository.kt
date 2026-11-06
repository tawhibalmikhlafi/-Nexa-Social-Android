package com.nexa.social.domain.repository

import com.nexa.social.core.common.Resource
import com.nexa.social.domain.model.TimeCapsule
import kotlinx.coroutines.flow.Flow

/**
 * Encrypted time capsules mirrored to Supabase while retaining a local cache. The payload uses
 * this device's Android Keystore key, so ciphertext is not portable to a different device.
 * A capsule can only be opened after [TimeCapsule.unlockAt].
 */
interface CapsuleRepository {
    fun observeCapsules(): Flow<List<TimeCapsule>>
    suspend fun createCapsule(title: String, message: String, unlockAt: Long): Resource<TimeCapsule>
    suspend fun openCapsule(id: String): Resource<TimeCapsule>
    suspend fun deleteCapsule(id: String): Boolean
    suspend fun refresh() {}
}
