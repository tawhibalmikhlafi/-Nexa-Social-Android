package com.nexa.social.data.remote

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.Instant

data class UserNote(
    val userId: String,
    val note: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String?
)

class SupabaseNotesDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?,
    private val currentUid: () -> String?
) {
    private val restUrl get() = config.restUrl

    suspend fun loadNotes(): List<UserNote> = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext emptyList()
        val response = runCatching {
            http.execute(http.authenticatedRequest(
                "$restUrl/user_notes?select=user_id,note&expires_at=gt.${encode(Instant.now().toString())}&order=updated_at.desc&limit=50",
                token
            ).get().build()).getOrNull()
        }.getOrNull() ?: return@withContext emptyList()
        if (!response.successful) {
            Log.w(TAG, "loadNotes failed with HTTP ${response.code}")
            return@withContext emptyList()
        }
        val notes = runCatching {
            JsonParser.parseString(response.body.orEmpty()).asJsonArray.mapNotNull { item ->
                item.takeIf { it.isJsonObject }?.asJsonObject?.let { row ->
                    val id = row.string("user_id") ?: return@let null
                    val note = row.string("note") ?: return@let null
                    id to note
                }
            }
        }.getOrDefault(emptyList())
        if (notes.isEmpty()) return@withContext emptyList()
        val ids = notes.map { it.first }.distinct()
        val profileResponse = runCatching {
            http.execute(http.authenticatedRequest(
                "$restUrl/profiles?select=id,username,display_name,avatar_url&id=in.(${ids.joinToString(",") { encode(it) }})",
                token
            ).get().build()).getOrNull()
        }.getOrNull() ?: return@withContext emptyList()
        if (!profileResponse.successful) return@withContext emptyList()
        val profiles = runCatching {
            JsonParser.parseString(profileResponse.body.orEmpty()).asJsonArray.mapNotNull { item ->
                item.takeIf { it.isJsonObject }?.asJsonObject?.let { row ->
                    val id = row.string("id") ?: return@let null
                    id to Triple(
                        row.string("display_name") ?: row.string("username") ?: "مستخدم",
                        row.string("username") ?: "",
                        row.string("avatar_url")
                    )
                }
            }.toMap()
        }.getOrDefault(emptyMap())
        notes.mapNotNull { (id, note) ->
            val profile = profiles[id] ?: return@mapNotNull null
            UserNote(id, note, profile.first, profile.second, profile.third)
        }
    }

    suspend fun saveNote(note: String): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext false
        val token = accessToken() ?: return@withContext false
        val value = note.trim().take(60)
        if (value.isBlank()) return@withContext clearNote()
        val expiresAt = Instant.now().plusSeconds(24 * 60 * 60L).toString()
        val body = JsonObject().apply {
            addProperty("user_id", uid)
            addProperty("note", value)
            addProperty("expires_at", expiresAt)
            addProperty("updated_at", Instant.now().toString())
        }
        val response = runCatching {
            http.execute(http.authenticatedRequest("$restUrl/user_notes?on_conflict=user_id", token)
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(http.jsonBody(body)).build()).getOrNull()
        }.getOrNull() ?: return@withContext false
        if (!response.successful) Log.w(TAG, "saveNote failed with HTTP ${response.code}")
        response.successful
    }

    suspend fun clearNote(): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext false
        val token = accessToken() ?: return@withContext false
        val response = runCatching {
            http.execute(http.authenticatedRequest("$restUrl/user_notes?user_id=eq.${encode(uid)}", token)
                .delete().build()).getOrNull()
        }.getOrNull() ?: return@withContext false
        if (!response.successful) Log.w(TAG, "clearNote failed with HTTP ${response.code}")
        response.successful
    }

    fun currentUserId(): String? = currentUid()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object { const val TAG = "NexaUserNotes" }
}
