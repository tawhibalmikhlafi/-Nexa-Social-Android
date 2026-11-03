package com.nexa.social.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.string
import com.nexa.social.domain.model.BlockedUser
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Remote block list backed by the `blocks` table. Rows are scoped to the signed-in account by
 * RLS, so a user only ever reads and writes their own block list. Returns null on any failure so
 * callers can keep the on-device copy intact while offline.
 */
class SupabaseSafetyDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?,
    private val currentUid: () -> String?
) {
    private val restUrl: String get() = config.restUrl

    suspend fun fetchBlocked(): List<BlockedUser>? {
        val uid = currentUid() ?: return null
        val rows = fetchRows(
            "blocks?select=blocked_id,created_at," +
                "blocked:profiles!blocked_id(id,username,display_name,avatar_url)" +
                "&blocker_id=eq.${encode(uid)}&order=created_at.desc"
        ) ?: return null
        return rows.mapNotNull { row ->
            val blockedId = row.string("blocked_id") ?: return@mapNotNull null
            val profile = row.getAsJsonObject("blocked")
            BlockedUser(
                userId = blockedId,
                username = profile?.string("username").orEmpty(),
                displayName = profile?.string("display_name")
                    ?: profile?.string("username")
                    ?: "",
                blockedAt = parseMillis(row.string("created_at"))
            )
        }
    }

    suspend fun block(blockedId: String): Boolean {
        val uid = currentUid() ?: return false
        if (uid == blockedId) return false
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("blocker_id", uid)
            addProperty("blocked_id", blockedId)
            addProperty("created_at", Instant.now().toString())
        }
        val response = http.execute(
            http.authenticatedRequest("$restUrl/blocks", token)
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(http.jsonBody(body))
                .build()
        ).getOrNull()
        return response?.successful == true
    }

    suspend fun unblock(blockedId: String): Boolean {
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val response = http.execute(
            http.authenticatedRequest(
                "$restUrl/blocks?blocker_id=eq.${encode(uid)}&blocked_id=eq.${encode(blockedId)}",
                token
            ).delete().build()
        ).getOrNull()
        return response?.successful == true
    }

    private suspend fun fetchRows(path: String): List<JsonObject>? {
        val token = accessToken() ?: return null
        val response = http.execute(
            http.authenticatedRequest("$restUrl/$path", token).get().build()
        ).getOrNull() ?: return null
        if (!response.successful) return null
        return runCatching {
            val element = JsonParser.parseString(response.body)
            if (!element.isJsonArray) return@runCatching null
            element.asJsonArray.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
        }.getOrNull()
    }

    private fun parseMillis(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrDefault(0L)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
