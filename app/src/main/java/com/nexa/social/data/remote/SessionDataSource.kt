package com.nexa.social.data.remote

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.domain.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read/revoke operations against the authenticated user's real Supabase Auth sessions. */
interface SessionDataSource {
    suspend fun getSessions(accessToken: String, currentSessionId: String?): Resource<List<UserSession>>
    suspend fun revokeSession(accessToken: String, sessionId: String): Resource<Unit>
    suspend fun revokeOtherSessions(accessToken: String): Resource<Unit>
}

/**
 * Talks to the `get_my_sessions` RPC, the official `logout?scope=others` endpoint and the
 * `revoke-session` Edge Function. The Auth schema is never queried directly from Android.
 */
class SupabaseSessionDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
) : SessionDataSource {

    override suspend fun getSessions(
        accessToken: String,
        currentSessionId: String?,
    ): Resource<List<UserSession>> = withContext(Dispatchers.IO) {
        val request = http
            .authenticatedRequest("${config.restUrl}/rpc/get_my_sessions", accessToken)
            .header("Prefer", "return=representation")
            .post(http.emptyJsonBody)
            .build()

        val response = http.execute(request).getOrElse { error ->
            Log.w(TAG, "get_my_sessions transport failure", error)
            return@withContext Resource.Failure(http.transportFailure(error))
        }

        if (!response.successful) {
            Log.w(TAG, "get_my_sessions failed with HTTP ${response.code}")
            return@withContext Resource.Failure(http.failureReason(response))
        }

        val payload = runCatching { JsonParser.parseString(response.body) }.getOrNull()
        val array = payload as? JsonArray ?: run {
            Log.w(TAG, "get_my_sessions returned a non-array payload")
            return@withContext Resource.Failure(FailureReason.GENERIC)
        }

        val sessions = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val dto = SessionDto.fromJson(obj) ?: return@mapNotNull null
            dto.toDomain(isCurrent = currentSessionId != null && dto.id == currentSessionId)
        }

        if (array.size() > 0 && sessions.isEmpty()) {
            Log.w(TAG, "get_my_sessions payload had no usable session rows")
            return@withContext Resource.Failure(FailureReason.GENERIC)
        }

        Log.d(TAG, "Loaded ${sessions.size} session(s)")
        Resource.Success(sessions)
    }

    override suspend fun revokeSession(
        accessToken: String,
        sessionId: String,
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        val body = JsonObject().apply { addProperty("sessionId", sessionId) }
        val request = http
            .authenticatedRequest("${config.functionsUrl}/revoke-session", accessToken)
            .post(http.jsonBody(body))
            .build()

        val response = http.execute(request).getOrElse { error ->
            Log.w(TAG, "revoke-session transport failure", error)
            return@withContext Resource.Failure(http.transportFailure(error))
        }

        if (!response.successful) {
            if (response.code == 404 && response.body.contains("SESSION_NOT_FOUND")) {
                // Already revoked on the server; the local list is still consistent.
                return@withContext Resource.Success(Unit)
            }
            Log.w(TAG, "revoke-session failed with HTTP ${response.code}")
            return@withContext Resource.Failure(http.failureReason(response))
        }

        Resource.Success(Unit)
    }

    /**
     * Official Supabase Auth sign-out for every other session. It keeps the current session and
     * invalidates the other sessions and their refresh tokens.
     */
    override suspend fun revokeOtherSessions(accessToken: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val request = http
                .authenticatedRequest("${config.authUrl}/logout?scope=others", accessToken)
                .post(http.emptyJsonBody)
                .build()

            val response = http.execute(request).getOrElse { error ->
                Log.w(TAG, "logout(scope=others) transport failure", error)
                return@withContext Resource.Failure(http.transportFailure(error))
            }

            if (!response.successful) {
                Log.w(TAG, "logout(scope=others) failed with HTTP ${response.code}")
                return@withContext Resource.Failure(http.failureReason(response))
            }

            Resource.Success(Unit)
        }

    private companion object {
        const val TAG = "SessionDataSource"
    }
}
