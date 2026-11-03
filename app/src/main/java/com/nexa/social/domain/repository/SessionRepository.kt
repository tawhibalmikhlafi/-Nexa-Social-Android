package com.nexa.social.domain.repository

import com.nexa.social.core.common.Resource
import com.nexa.social.domain.model.UserSession

interface SessionRepository {
    suspend fun getActiveSessions(): Resource<List<UserSession>>
    suspend fun revokeSession(sessionId: String): Resource<Unit>

    /** Signs out every other device while keeping the current session active. */
    suspend fun revokeOtherSessions(): Resource<Unit>

    suspend fun registerCurrentDevice(deviceName: String, ipAddress: String? = null): Resource<Unit>
}
