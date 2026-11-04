package com.nexa.social.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.Ids
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.long
import com.nexa.social.core.supabase.string
import com.nexa.social.domain.model.AccountOrigin
import com.nexa.social.domain.model.Clip
import com.nexa.social.domain.model.ClipComment
import com.nexa.social.domain.model.UserSummary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

/** Supabase-backed short video feed. The device cache remains the offline read source. */
class SupabaseClipDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?,
    private val currentUid: () -> String?
) {
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val restUrl get() = config.restUrl

    fun notifyChanged() { refreshSignal.tryEmit(Unit) }

    fun observeClips(): Flow<List<Clip>> = flow {
        while (currentCoroutineContext().isActive) {
            val uid = currentUid()
            val token = accessToken()
            if (token != null) {
                val rows = fetchRows("clips?select=*,author:profiles!author_id(id,username,display_name,avatar_url,account_origin)&order=created_at.desc&limit=100", token)
                if (rows != null) {
                    val likes = fetchRows("clip_likes?select=clip_id&user_id=eq.${encode(uid.orEmpty())}", token)
                        ?.mapNotNull { it.string("clip_id") }?.toSet().orEmpty()
                    val saves = fetchRows("clip_saves?select=clip_id&user_id=eq.${encode(uid.orEmpty())}", token)
                        ?.mapNotNull { it.string("clip_id") }?.toSet().orEmpty()
                    emit(rows.mapNotNull { parseClip(it, likes, saves, uid) })
                }
            }
            withTimeoutOrNull(8_000L) { refreshSignal.first() }
        }
    }

    suspend fun createClip(author: UserSummary, videoPath: String, videoUrl: String, caption: String): Clip? {
        val uid = currentUid() ?: return null
        val token = accessToken() ?: return null
        if (uid != author.id) return null
        val id = Ids.newId()
        val now = Instant.now()
        val body = JsonObject().apply {
            addProperty("id", id); addProperty("author_id", uid)
            addProperty("video_path", videoPath); addProperty("video_url", videoUrl)
            addProperty("caption", caption.trim()); addProperty("created_at", now.toString())
        }
        val response = http.execute(http.authenticatedRequest("$restUrl/clips", token)
            .header("Prefer", "return=minimal").post(http.jsonBody(body)).build()).getOrNull()
            ?: return null
        if (!response.successful) return null
        notifyChanged()
        return Clip(id, author, videoUrl, caption.trim(), now.toEpochMilli(), 0, 0, false, false, false)
    }

    suspend fun recordView(clipId: String): Boolean {
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val body = JsonObject().apply { addProperty("clip_id", clipId); addProperty("user_id", uid) }
        val request = http.authenticatedRequest("$restUrl/clip_views", token)
            .header("Prefer", "resolution=ignore-duplicates,return=minimal")
            .post(http.jsonBody(body)).build()
        val response = http.execute(request).getOrNull() ?: return false
        if (response.successful) notifyChanged()
        return response.successful
    }

    suspend fun getComments(clipId: String): List<ClipComment>? {
        val uid = currentUid() ?: return null
        val token = accessToken() ?: return null
        val path = "clip_comments?select=*,author:profiles!author_id(id,display_name,username,avatar_url)&clip_id=eq." + encode(clipId) + "&is_deleted=eq.false&order=created_at.asc"
        val rows = fetchRows(path, token) ?: return null
        return rows.mapNotNull { row ->
            val author = row.getAsJsonObject("author") ?: return@mapNotNull null
            val authorId = author.string("id") ?: return@mapNotNull null
            val username = author.string("username") ?: "user"
            ClipComment(row.string("id") ?: return@mapNotNull null, clipId, authorId, author.string("display_name") ?: username, author.string("avatar_url"), row.string("content").orEmpty(), parseMillis(row.string("created_at")), authorId == uid)
        }
    }
    suspend fun toggleLike(clipId: String): Boolean = toggleRelation("clip_likes", clipId)
    suspend fun toggleSave(clipId: String): Boolean = toggleRelation("clip_saves", clipId)

    /** Applies an explicit desired state so an offline retry cannot invert a newer action. */
    suspend fun setLike(clipId: String, enabled: Boolean): Boolean =
        setRelation("clip_likes", clipId, enabled)

    suspend fun setSave(clipId: String, enabled: Boolean): Boolean =
        setRelation("clip_saves", clipId, enabled)

    private suspend fun setRelation(table: String, clipId: String, enabled: Boolean): Boolean {
        if (table != "clip_likes" && table != "clip_saves") return false
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val url = "$restUrl/$table?clip_id=eq.${encode(clipId)}&user_id=eq.${encode(uid)}"
        val request = if (enabled) {
            val body = JsonObject().apply { addProperty("clip_id", clipId); addProperty("user_id", uid) }
            http.authenticatedRequest("$restUrl/$table", token)
                .header("Prefer", "resolution=ignore-duplicates,return=minimal")
                .post(http.jsonBody(body)).build()
        } else {
            http.authenticatedRequest(url, token).delete().build()
        }
        val response = http.execute(request).getOrNull() ?: return false
        if (response.successful) notifyChanged()
        return response.successful
    }

    private suspend fun toggleRelation(table: String, clipId: String): Boolean {
        if (table != "clip_likes" && table != "clip_saves") return false
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val exists = fetchRows("$table?select=clip_id&clip_id=eq.${encode(clipId)}&user_id=eq.${encode(uid)}", token)
            ?.isNotEmpty() == true
        val request = if (exists) {
            http.authenticatedRequest("$restUrl/$table?clip_id=eq.${encode(clipId)}&user_id=eq.${encode(uid)}", token).delete().build()
        } else {
            val body = JsonObject().apply { addProperty("clip_id", clipId); addProperty("user_id", uid) }
            http.authenticatedRequest("$restUrl/$table", token).header("Prefer", "return=minimal")
                .post(http.jsonBody(body)).build()
        }
        val response = http.execute(request).getOrNull() ?: return false
        if (response.successful) notifyChanged()
        return response.successful
    }

    suspend fun deleteClip(clipId: String): String? {
        val uid = currentUid() ?: return null
        val token = accessToken() ?: return null
        val rows = fetchRows("clips?select=video_path&id=eq.${encode(clipId)}&author_id=eq.${encode(uid)}", token)
            ?: return null
        val path = rows.firstOrNull()?.string("video_path") ?: return null
        val response = http.execute(http.authenticatedRequest(
            "$restUrl/clips?id=eq.${encode(clipId)}&author_id=eq.${encode(uid)}", token
        ).delete().build()).getOrNull() ?: return null
        if (!response.successful) return null
        notifyChanged()
        return path
    }

    private suspend fun fetchRows(path: String, token: String): List<JsonObject>? {
        val response = http.execute(http.authenticatedRequest("$restUrl/$path", token).get().build()).getOrNull()
            ?: return null
        if (!response.successful) return null
        return runCatching {
            JsonParser.parseString(response.body).asJsonArray.map { it.asJsonObject }
        }.getOrNull()
    }

    private fun parseClip(row: JsonObject, likes: Set<String>, saves: Set<String>, uid: String?): Clip? {
        val id = row.string("id") ?: return null
        val authorRow = row.getAsJsonObject("author") ?: return null
        val authorId = authorRow.string("id") ?: return null
        val username = authorRow.string("username") ?: "user"
        val author = UserSummary(
            id = authorId, username = username,
            displayName = authorRow.string("display_name") ?: username,
            avatarUri = authorRow.string("avatar_url"),
            accountOrigin = runCatching { AccountOrigin.valueOf(authorRow.string("account_origin") ?: "REMOTE_ACCOUNT") }
                .getOrDefault(AccountOrigin.REMOTE_ACCOUNT)
        )
        return Clip(
            id = id, author = author, videoUri = row.string("video_url"),
            caption = row.string("caption").orEmpty(),
            createdAt = parseMillis(row.string("created_at")),
            likeCount = row.long("like_count")?.toInt() ?: 0,
            commentCount = row.long("comment_count")?.toInt() ?: 0,
            likedByMe = id in likes, savedByMe = id in saves, isLocal = false,
            viewCount = row.long("view_count") ?: 0L
        )
    }

    private fun parseMillis(value: String?): Long =
        runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrDefault(0L)

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
