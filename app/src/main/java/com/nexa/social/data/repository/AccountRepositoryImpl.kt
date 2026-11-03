package com.nexa.social.data.repository

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.data.remote.SupabaseAuthDataSource
import com.nexa.social.domain.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccountRepositoryImpl(
    private val http: SupabaseHttp,
    private val auth: SupabaseAuthDataSource,
) : AccountRepository {

    private val restUrl: String get() = http.config.restUrl

    override suspend fun exportData(): Resource<String> = withContext(Dispatchers.IO) {
        val token = auth.currentAccessToken()
            ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val uid = auth.currentUid()
            ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)

        val tables = listOf(
            "profiles" to "id",
            "posts" to "author_id",
            "comments" to "author_id",
            "post_likes" to "user_id",
            "post_saves" to "user_id",
            "post_shares" to "user_id",
            "follows" to "follower_id",
            "messages" to "sender_id",
            "user_privacy_settings" to "user_id",
        )

        val root = JsonObject().apply {
            addProperty("exported_at", System.currentTimeMillis())
            addProperty("user_id", uid)
        }

        for ((table, column) in tables) {
            val url = "$restUrl/$table?$column=eq.$uid&select=*"
            val request = http.authenticatedRequest(url, token).get().build()
            val response = http.execute(request).getOrElse { error ->
                return@withContext Resource.Failure(http.transportFailure(error))
            }
            if (!response.successful) {
                return@withContext Resource.Failure(http.failureReason(response))
            }
            val rows = runCatching {
                JsonParser.parseString(response.body).asJsonArray
            }.getOrElse { JsonArray() }
            root.add(table, rows)
        }

        val pretty = GsonBuilder().setPrettyPrinting().create().toJson(root)
        Resource.Success(pretty)
    }

    override suspend fun deleteAccount(): Resource<Unit> = withContext(Dispatchers.IO) {
        val token = auth.currentAccessToken()
            ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)

        val request = http
            .authenticatedRequest("$restUrl/rpc/delete_own_account", token)
            .post(http.emptyJsonBody)
            .build()

        val response = http.execute(request).getOrElse { error ->
            return@withContext Resource.Failure(http.transportFailure(error))
        }
        if (!response.successful) {
            return@withContext Resource.Failure(http.failureReason(response))
        }
        Resource.Success(Unit)
    }
}