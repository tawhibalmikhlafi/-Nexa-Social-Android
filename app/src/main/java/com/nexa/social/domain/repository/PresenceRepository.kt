package com.nexa.social.domain.repository

import kotlinx.coroutines.flow.Flow

interface PresenceRepository {
    suspend fun pingNow()
    fun startHeartbeat()
    fun stopHeartbeat()
    suspend fun isOnline(userId: String): Boolean
    fun observeOnline(userId: String): Flow<Boolean>
    suspend fun areOnline(userIds: List<String>): Map<String, Boolean>
}
