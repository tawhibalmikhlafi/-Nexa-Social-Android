package com.nexa.social.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.Ids
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.long
import com.nexa.social.core.supabase.string
import com.nexa.social.domain.model.AccountOrigin
import com.nexa.social.domain.model.PostComment
import com.nexa.social.domain.model.PostReaction
import com.nexa.social.domain.model.UserSummary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

private const val POLL_MS = 8_000L

class SupabaseCommentDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?,
    private val currentUid: () -> String?
) {
    private val restUrl: String get() = config.restUrl
    val pageSize = MutableStateFlow(100L)
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun notifyChanged() {
        refreshSignal.tryEmit(Unit)
    }

    fun loadMore() {
        pageSize.value = (pageSize.value + 50L).coerceAtMost(500L)
    }

    fun observeComments(postId: String): Flow<List<PostComment>> = flow {
        while (currentCoroutineContext().isActive) {
            val uid = currentUid()
            val limit = pageSize.value
            val rows = fetchRows("comments?select=*,author:profiles!author_id(id,username,display_name,avatar_url,account_origin)&post_id=eq.${encode(postId)}&order=created_at.asc&limit=$limit")
            val myRows = uid?.let {
                fetchRows("comment_likes?select=comment_id,emoji&user_id=eq.${encode(uid)}").orEmpty()
            }.orEmpty()
            val myReactions = myRows.mapNotNull { row ->
                row.string("comment_id")?.let { id -> id to (row.string("emoji") ?: "❤️") }
            }.toMap()
            val liked = myReactions.keys
            val postAuthorId = fetchRows("posts?select=author_id&id=eq.${encode(postId)}&limit=1")
                ?.firstOrNull()
                ?.string("author_id")

            val comments = rows.orEmpty().mapNotNull { row ->
                parseComment(row, uid, liked, postAuthorId, myReactions)
            }
            emit(comments)
            withTimeoutOrNull(POLL_MS) { refreshSignal.first() }
        }
    }

    suspend fun getCommentPreview(postId: String): List<PostComment> {
        val uid = currentUid()
        val rows = fetchRows("comments?select=*,author:profiles!author_id(id,username,display_name,avatar_url,account_origin)&post_id=eq.${encode(postId)}&order=created_at.desc&limit=2").orEmpty()
        if (rows.isEmpty()) return emptyList()
        val ids = rows.mapNotNull { it.string("id") }
        val myRows = if (uid == null || ids.isEmpty()) emptyList() else {
            fetchRows("comment_likes?select=comment_id,emoji&user_id=eq.${encode(uid)}&comment_id=in.(${ids.joinToString(",") { encode(it) }})").orEmpty()
        }
        val myReactions = myRows.mapNotNull { row ->
            row.string("comment_id")?.let { id -> id to (row.string("emoji") ?: "❤️") }
        }.toMap()
        val liked = myReactions.keys
        val postAuthorId = fetchRows("posts?select=author_id&id=eq.${encode(postId)}&limit=1")?.firstOrNull()?.string("author_id")
        return rows.mapNotNull { parseComment(it, uid, liked, postAuthorId, myReactions) }.sortedBy { it.createdAt }
    }

    suspend fun addComment(
        postId: String,
        author: UserSummary,
        text: String,
        parentId: String?,
        imageUri: String? = null
    ): PostComment {
        val commentId = Ids.newId()
        val token = accessToken() ?: throw IllegalStateException("Not authenticated")
        val body = JsonObject().apply {
            addProperty("id", commentId)
            addProperty("post_id", postId)
            addProperty("author_id", author.id)
            addProperty("text", text.trim())
            if (parentId != null) addProperty("parent_id", parentId)
            if (!imageUri.isNullOrBlank()) addProperty("image_uri", imageUri)
        }
        val response = execute(
            http.authenticatedRequest("$restUrl/comments", token)
                .header("Prefer", "return=minimal")
                .post(http.jsonBody(body))
                .build()
        )
        if (response?.successful != true) {
            throw IllegalStateException("Comment insert failed with HTTP ${response?.code}")
        }
        notifyChanged()
        val now = System.currentTimeMillis()
        return PostComment(
            id = commentId,
            postId = postId,
            author = author,
            text = text.trim(),
            imageUri = imageUri,
            createdAt = now,
            updatedAt = now,
            parentId = parentId,
            likeCount = 0,
            likedByMe = false,
            isMine = true,
            canDelete = true,
            isLocal = false
        )
    }

    suspend fun getComment(commentId: String): PostComment? {
        val rows = fetchRows("comments?select=*,author:profiles!author_id(id,username,display_name,avatar_url,account_origin)&id=eq.${encode(commentId)}&limit=1")
        val row = rows?.firstOrNull() ?: return null
        val uid = currentUid()
        val myReaction = uid?.let {
            fetchRows("comment_likes?select=emoji&user_id=eq.${encode(uid)}&comment_id=eq.${encode(commentId)}")
                ?.firstOrNull()?.string("emoji")
        }
        return parseComment(row, uid, if (myReaction != null) setOf(commentId) else emptySet(), myReactions = if (myReaction != null) mapOf(commentId to myReaction) else emptyMap())
    }

    suspend fun editComment(commentId: String, text: String): PostComment? {
        val token = accessToken() ?: return null
        val body = JsonObject().apply {
            addProperty("text", text.trim())
            addProperty("updated_at", Instant.now().toString())
        }
        val response = execute(
            http.authenticatedRequest("$restUrl/comments?id=eq.${encode(commentId)}", token)
                .header("Prefer", "return=minimal")
                .patch(http.jsonBody(body))
                .build()
        )
        if (response?.successful != true) {
            throw IllegalStateException("Comment update failed with HTTP ${response?.code ?: "NETWORK_ERROR"}")
        }
        notifyChanged()
        return getComment(commentId)
    }

    suspend fun deleteComment(commentId: String): Boolean {
        val token = accessToken() ?: return false
        val response = execute(
            http.authenticatedRequest("$restUrl/comments?id=eq.${encode(commentId)}", token)
                .delete().build()
        )
        val deleted = response?.successful == true
        if (deleted) notifyChanged()
        return deleted
    }

    suspend fun toggleLike(commentId: String, emoji: String = "❤️") {
        val uid = currentUid() ?: return
        val token = accessToken() ?: return
        val existing = fetchRows("comment_likes?select=comment_id,emoji&comment_id=eq.${encode(commentId)}&user_id=eq.${encode(uid)}")
            ?.firstOrNull()
        val url = "$restUrl/comment_likes?comment_id=eq.${encode(commentId)}&user_id=eq.${encode(uid)}"
        val response = when {
            existing == null -> {
                val body = JsonObject().apply {
                    addProperty("comment_id", commentId)
                    addProperty("user_id", uid)
                    addProperty("emoji", emoji)
                }
                execute(
                    http.authenticatedRequest("$restUrl/comment_likes", token)
                        .header("Prefer", "return=minimal")
                        .post(http.jsonBody(body))
                        .build()
                )
            }
            (existing.string("emoji") ?: "❤️") == emoji -> {
                execute(http.authenticatedRequest(url, token).delete().build())
            }
            else -> {
                val body = JsonObject().apply { addProperty("emoji", emoji) }
                execute(
                    http.authenticatedRequest(url, token)
                        .header("Prefer", "return=minimal")
                        .patch(http.jsonBody(body))
                        .build()
                )
            }
        }
        if (response?.successful != true) {
            throw IllegalStateException("Comment reaction failed with HTTP ${response?.code ?: "NETWORK_ERROR"}")
        }
        notifyChanged()
    }

    suspend fun getCommentReactions(commentId: String): List<PostReaction> {
        val rows = fetchRows(
            "comment_likes?select=emoji,created_at,user:profiles!user_id(id,username,display_name,avatar_url,account_origin)&comment_id=eq.${encode(commentId)}&order=created_at.desc"
        ).orEmpty()
        return rows.mapNotNull { row ->
            val user = row.getAsJsonObject("user") ?: return@mapNotNull null
            val id = user.string("id") ?: return@mapNotNull null
            val username = user.string("username") ?: "user"
            val displayName = user.string("display_name")?.takeIf { it.isNotBlank() } ?: username
            val author = UserSummary(
                id, username, displayName, user.string("avatar_url"),
                enumOrDefault(user.string("account_origin"), AccountOrigin.REMOTE_ACCOUNT)
            )
            PostReaction(
                user = author,
                emoji = row.string("emoji") ?: "❤️",
                createdAt = parseMillis(row.string("created_at"))
            )
        }
    }

    suspend fun refreshAuthor(_author: UserSummary) {}

    private fun parseComment(
        row: JsonObject,
        currentUid: String?,
        liked: Set<String>,
        postAuthorId: String? = null,
        myReactions: Map<String, String> = emptyMap()
    ): PostComment? {
        val id = row.string("id") ?: return null
        val postId = row.string("post_id") ?: return null
        val authorObj = row.getAsJsonObject("author") ?: return null
        val authorId = authorObj.string("id") ?: return null
        val username = authorObj.string("username") ?: "user"
        val displayName = authorObj.string("display_name") ?: username
        val avatarUrl = authorObj.string("avatar_url")
        val origin = enumOrDefault(authorObj.string("account_origin"), AccountOrigin.REMOTE_ACCOUNT)

        val author = UserSummary(authorId, username, displayName, avatarUrl, origin)
        val text = row.string("text").orEmpty()
        val imageUri = row.string("image_uri")
        val createdAt = parseMillis(row.string("created_at"))
        val updatedAt = parseMillis(row.string("updated_at")).takeIf { it > 0L } ?: createdAt
        val parentId = row.string("parent_id")
        val likeCount = row.long("like_count")?.toInt() ?: 0

        val isMine = authorId == currentUid
        return PostComment(
            id = id,
            postId = postId,
            author = author,
            text = text,
            imageUri = imageUri,
            createdAt = createdAt,
            updatedAt = updatedAt,
            parentId = parentId,
            likeCount = likeCount,
            likedByMe = id in liked,
            myReaction = myReactions[id],
            isMine = isMine,
            canDelete = isMine || (postAuthorId != null && postAuthorId == currentUid),
            isLocal = false
        )
    }

    private suspend fun fetchRows(path: String): List<JsonObject>? {
        val token = accessToken()
        val builder = if (token != null) {
            http.authenticatedRequest("$restUrl/$path", token).get()
        } else {
            http.request("$restUrl/$path").get()
        }
        val response = execute(builder.build()) ?: return null
        if (!response.successful) return null
        return parseArray(response.body)
    }

    private suspend fun execute(request: okhttp3.Request): SupabaseHttp.HttpResponse? =
        http.execute(request).getOrNull()

    private fun parseArray(body: String): List<JsonObject>? = runCatching {
        val element = JsonParser.parseString(body)
        if (!element.isJsonArray) return@runCatching null
        element.asJsonArray.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
    }.getOrNull()

    private fun parseMillis(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrDefault(0L)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
