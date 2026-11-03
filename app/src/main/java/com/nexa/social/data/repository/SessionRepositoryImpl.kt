package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.SupabaseJwt
import com.nexa.social.data.remote.SessionDataSource
import com.nexa.social.data.remote.SupabaseAuthDataSource
import com.nexa.social.data.remote.SupabaseSessionDataSource
import com.nexa.social.domain.model.UserSession
import com.nexa.social.domain.repository.SessionRepository

class SessionRepositoryImpl(
    private val http: SupabaseHttp,
    private val auth: SupabaseAuthDataSource,
    private val dataSource: SessionDataSource = SupabaseSessionDataSource(http.config, http),
) : SessionRepository {

    override suspend fun getActiveSessions(): Resource<List<UserSession>> {
        val token = auth.currentAccessToken()
            ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        return dataSource.getSessions(
            accessToken = token,
            currentSessionId = SupabaseJwt.sessionId(token),
        )
    }

    override suspend fun revokeSession(sessionId: String): Resource<Unit> {
        val token = auth.currentAccessToken()
            ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        return dataSource.revokeSession(accessToken = token, sessionId = sessionId)
    }

    override suspend fun revokeOtherSessions(): Resource<Unit> {
        val token = auth.currentAccessToken()
            ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        return dataSource.revokeOtherSessions(accessToken = token)
    }

    override suspend fun registerCurrentDevice(
        deviceName: String,
        ipAddress: String?,
    ): Resource<Unit> = Resource.Success(Unit)
}
