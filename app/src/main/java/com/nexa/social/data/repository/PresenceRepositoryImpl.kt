package com.nexa.social.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.supabase.AccessTokenProvider
import com.nexa.social.core.supabase.CurrentUidProvider
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.boolean
import com.nexa.social.core.supabase.string
import com.nexa.social.domain.repository.PresenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PresenceRepositoryImpl(
    private val http: SupabaseHttp?,
    private val accessToken: AccessTokenProvider,
    private val currentUid: CurrentUidProvider,
    private val prefs: PreferenceStorage,
) : PresenceRepository {

    private val heartbeatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    override suspend fun pingNow() = withContext(Dispatchers.IO) {
        val client = http ?: return@withContext
        val settings = prefs.privacySettings.first()
        if (!settings.showActivityStatus) return@withContext
        val token = accessToken.token() ?: return@withContext
        val request = client.authenticatedRequest("${client.config.restUrl}/rpc/ping_presence", token)
            .post(client.emptyJsonBody)
            .build()
        client.execute(request)
        Unit
    }

    override fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = heartbeatScope.launch {
            runCatching { pingNow() }
            while (isActive) {
                delay(30_000L)
                runCatching { pingNow() }
            }
        }
    }

    override fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    override suspend fun isOnline(userId: String): Boolean = withContext(Dispatchers.IO) {
        val client = http ?: return@withContext false
        val token = accessToken.token() ?: return@withContext false
        val body = JsonObject().apply { addProperty("target_uid", userId) }
        val request = client.authenticatedRequest("${client.config.restUrl}/rpc/is_user_online", token)
            .post(client.jsonBody(body))
            .build()
        val response = client.execute(request).getOrNull() ?: return@withContext false
        if (!response.successful) return@withContext false
        response.body.trim().equals("true", ignoreCase = true)
    }

    override fun observeOnline(userId: String): Flow<Boolean> = flow {
        emit(isOnline(userId))
        while (true) {
            delay(25_000L)
            emit(isOnline(userId))
        }
    }.catch {
        // Transient failures keep the last emitted value.
    }

    override suspend fun areOnline(userIds: List<String>): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            if (userIds.isEmpty()) return@withContext emptyMap()
            val client = http ?: return@withContext emptyMap()
            val token = accessToken.token() ?: return@withContext emptyMap()
            val arr = JsonArray().apply { userIds.forEach { add(it) } }
            val body = JsonObject().apply { add("target_uids", arr) }
            val request = client.authenticatedRequest("${client.config.restUrl}/rpc/users_online", token)
                .post(client.jsonBody(body))
                .build()
            val response = client.execute(request).getOrNull() ?: return@withContext emptyMap()
            if (!response.successful) return@withContext emptyMap()
            runCatching {
                JsonParser.parseString(response.body).asJsonArray.associate { element ->
                    val row = element.asJsonObject
                    row.string("user_id").orEmpty() to (row.boolean("is_online") ?: false)
                }
            }.getOrDefault(emptyMap())
        }
}
