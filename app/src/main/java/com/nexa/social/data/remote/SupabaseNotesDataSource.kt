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
    val avatarUrl: String?,
    val visibility: String = "FOLLOWING",
    val durationHours: Int = 24
)

data class UserNoteInteraction(
    val id: String,
    val actorId: String,
    val actorName: String,
    val kind: String,
    val content: String
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
                "$restUrl/user_notes?select=user_id,note,visibility,duration_hours&expires_at=gt.${encode(Instant.now().toString())}&order=updated_at.desc&limit=50",
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
                    id to Triple(note, row.string("visibility") ?: "FOLLOWING", runCatching { row.get("duration_hours")?.asInt }.getOrNull() ?: 24)
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
            UserNote(id, note.first, profile.first, profile.second, profile.third, note.second, note.third)
        }
    }

    suspend fun saveNote(note: String, visibility: String = "FOLLOWING", durationHours: Int = 24): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext false
        val token = accessToken() ?: return@withContext false
        val value = note.trim().take(60)
        if (value.isBlank()) return@withContext clearNote()
        val safeDuration = durationHours.coerceIn(1, 48).let { if (it in listOf(1,6,12,24,48)) it else 24 }
        val expiresAt = Instant.now().plusSeconds(safeDuration * 60 * 60L).toString()
        val body = JsonObject().apply {
            addProperty("user_id", uid)
            addProperty("note", value)
            addProperty("visibility", visibility.takeIf { it in listOf("FOLLOWING","FOLLOWERS","EVERYONE") } ?: "FOLLOWING")
            addProperty("duration_hours", safeDuration)
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

    suspend fun addInteraction(noteUserId: String, kind: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext false
        val token = accessToken() ?: return@withContext false
        if (kind !in listOf("REACTION", "REPLY") || content.isBlank()) return@withContext false
        val body = JsonObject().apply {
            addProperty("note_user_id", noteUserId)
            addProperty("actor_id", uid)
            addProperty("kind", kind)
            addProperty("content", content.trim().take(200))
        }
        val response = runCatching {
            http.execute(http.authenticatedRequest("$restUrl/user_note_interactions", token)
                .header("Prefer", "return=minimal").post(http.jsonBody(body)).build()).getOrNull()
        }.getOrNull() ?: return@withContext false
        response.successful
    }

    suspend fun loadInteractions(noteUserId: String): List<UserNoteInteraction> = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext emptyList()
        val response = runCatching {
            http.execute(http.authenticatedRequest(
                "$restUrl/user_note_interactions?select=id,actor_id,kind,content&note_user_id=eq.${encode(noteUserId)}&order=created_at.desc&limit=100",
                token
            ).get().build()).getOrNull()
        }.getOrNull() ?: return@withContext emptyList()
        if (!response.successful) return@withContext emptyList()
        val rows = runCatching { JsonParser.parseString(response.body.orEmpty()).asJsonArray.mapNotNull { it.takeIf { x -> x.isJsonObject }?.asJsonObject } }.getOrDefault(emptyList())
        if (rows.isEmpty()) return@withContext emptyList()
        val ids = rows.mapNotNull { it.string("actor_id") }.distinct()
        val profiles = runCatching {
            val pr = http.execute(http.authenticatedRequest("$restUrl/profiles?select=id,display_name&id=in.(${ids.joinToString(",") { encode(it) }})", token).get().build()).getOrNull()
                ?: return@runCatching emptyMap<String, String>()
            if (!pr.successful) emptyMap() else JsonParser.parseString(pr.body).asJsonArray.associate { row ->
                row.asJsonObject.string("id").orEmpty() to (row.asJsonObject.string("display_name") ?: "مستخدم")
            }
        }.getOrDefault(emptyMap())
        rows.mapNotNull { row ->
            val id = row.string("id") ?: return@mapNotNull null
            val actor = row.string("actor_id") ?: return@mapNotNull null
            UserNoteInteraction(id, actor, profiles[actor] ?: "مستخدم", row.string("kind") ?: "REACTION", row.string("content") ?: "")
        }
    }

    fun currentUserId(): String? = currentUid()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object { const val TAG = "NexaUserNotes" }
}
