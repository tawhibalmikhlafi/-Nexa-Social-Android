
package com.nexa.social.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.long
import com.nexa.social.core.supabase.string
import com.nexa.social.domain.model.AccountOrigin
import com.nexa.social.domain.model.Audience
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.PostCreationException
import com.nexa.social.domain.model.PostReaction
import com.nexa.social.domain.model.UserSummary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

private const val POLL_MS = 8_000L

class SupabasePostDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?,
    private val currentUid: () -> String?
) {
    private val restUrl: String
        get() = config.restUrl

    private val pageSize = MutableStateFlow(20L)
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun notifyChanged() {
        refreshSignal.tryEmit(Unit)
    }

    fun loadMore(): Boolean {
        pageSize.value += 20L
        notifyChanged()
        return true
    }

    fun observeFeed(): Flow<List<Post>> = flow {
        while (currentCoroutineContext().isActive) {
            val uid = currentUid()
            val limit = pageSize.value

            val rows = fetchRows(
                "posts?select=*,author:profiles!author_id(id,username,display_name,avatar_url,account_origin)" +
                    "&order=created_at.desc&limit=$limit"
            )

            val reactions = uid?.let { fetchMyReactions(it) } ?: emptyMap()

            val saved = uid?.let {
                fetchRows("post_saves?select=post_id&user_id=eq.${encode(it)}")
                    ?.mapNotNull { row -> row.string("post_id") }
                    .orEmpty()
                    .toSet()
            } ?: emptySet()

            emit(rows.orEmpty().mapNotNull { parsePost(it, reactions, saved) })
            withTimeoutOrNull(POLL_MS) { refreshSignal.first() }
        }
    }

    fun observePostsByAuthor(authorId: String): Flow<List<Post>> = flow {
        while (currentCoroutineContext().isActive) {
            val uid = currentUid()

            val rows = fetchRows(
                "posts?select=*,author:profiles!author_id(id,username,display_name,avatar_url,account_origin)" +
                    "&author_id=eq.${encode(authorId)}&order=created_at.desc&limit=100"
            )

            val reactions = uid?.let { fetchMyReactions(it) } ?: emptyMap()

            val saved = uid?.let {
                fetchRows("post_saves?select=post_id&user_id=eq.${encode(it)}")
                    ?.mapNotNull { row -> row.string("post_id") }
                    .orEmpty()
                    .toSet()
            } ?: emptySet()

            emit(rows.orEmpty().mapNotNull { parsePost(it, reactions, saved) })
            withTimeoutOrNull(POLL_MS) { refreshSignal.first() }
        }
    }

    fun observeSavedPosts(): Flow<List<Post>> = flow {
        while (currentCoroutineContext().isActive) {
            val uid = currentUid()

            if (uid == null) {
                emit(emptyList())
                withTimeoutOrNull(POLL_MS) { refreshSignal.first() }
                continue
            }

            val savedRows = fetchRows(
                "post_saves?select=post_id&user_id=eq.${encode(uid)}&order=created_at.desc"
            )
            val ids = savedRows.orEmpty().mapNotNull { it.string("post_id") }

            if (ids.isEmpty()) {
                emit(emptyList())
            } else {
                val filter = ids.joinToString(",") { encode(it) }

                val rows = fetchRows(
                    "posts?select=*,author:profiles!author_id(id,username,display_name,avatar_url,account_origin)" +
                        "&id=in.($filter)"
                )

                val reactions = fetchMyReactions(uid)

                emit(
                    rows.orEmpty().mapNotNull {
                        parsePost(it, reactions, ids.toSet(), forcedSaved = true)
                    }
                )
            }

            withTimeoutOrNull(POLL_MS) { refreshSignal.first() }
        }
    }

    fun observeHiddenPostIds(): Flow<Set<String>> = flow {
        while (currentCoroutineContext().isActive) {
            val uid = currentUid()
            val ids = if (uid == null) emptySet() else {
                fetchRows("post_hides?select=post_id&user_id=eq.${encode(uid)}")
                    ?.mapNotNull { it.string("post_id") }
                    ?.toSet()
                    .orEmpty()
            }
            emit(ids)
            withTimeoutOrNull(POLL_MS) { refreshSignal.first() }
        }
    }

    fun observePinnedPostIds(): Flow<Set<String>> = flow {
        while (currentCoroutineContext().isActive) {
            val uid = currentUid()
            val ids = if (uid == null) emptySet() else {
                fetchRows("post_pins?select=post_id&user_id=eq.${encode(uid)}")
                    ?.mapNotNull { it.string("post_id") }
                    ?.toSet()
                    .orEmpty()
            }
            emit(ids)
            withTimeoutOrNull(POLL_MS) { refreshSignal.first() }
        }
    }

    suspend fun setHidden(postId: String, hidden: Boolean) {
        setPostPreference("post_hides", postId, hidden)
    }

    suspend fun togglePin(postId: String) {
        val uid = currentUid() ?: throw IllegalStateException("NOT_AUTHENTICATED")
        val exists = fetchRows(
            "post_pins?select=post_id&post_id=eq.${encode(postId)}&user_id=eq.${encode(uid)}"
        )?.isNotEmpty() == true
        setPostPreference("post_pins", postId, !exists)
    }

    private suspend fun setPostPreference(
        table: String,
        postId: String,
        enabled: Boolean
    ) {
        require(table == "post_hides" || table == "post_pins")
        val uid = currentUid() ?: throw IllegalStateException("NOT_AUTHENTICATED")
        val token = accessToken() ?: throw IllegalStateException("NOT_AUTHENTICATED")
        val url = if (enabled) {
            "$restUrl/$table?on_conflict=post_id,user_id"
        } else {
            "$restUrl/$table?post_id=eq.${encode(postId)}&user_id=eq.${encode(uid)}"
        }
        val request = if (enabled) {
            val body = JsonObject().apply {
                addProperty("post_id", postId)
                addProperty("user_id", uid)
            }
            http.authenticatedRequest(url, token)
                .header("Prefer", "resolution=ignore-duplicates,return=minimal")
                .post(http.jsonBody(body))
                .build()
        } else {
            http.authenticatedRequest(url, token).delete().build()
        }
        val response = execute(request)
        if (response?.successful != true) {
            throw IllegalStateException(
                "POST_${table.uppercase()}_FAILED_HTTP_${response?.code ?: "NETWORK_ERROR"}"
            )
        }
        notifyChanged()
    }

    fun observeHiddenPosts(hiddenIds: Flow<Set<String>>): Flow<List<Post>> = flow {
        while (currentCoroutineContext().isActive) {
            val uid = currentUid()
            val ids = hiddenIds.first().filter {
                runCatching { java.util.UUID.fromString(it) }.isSuccess
            }

            if (ids.isEmpty()) {
                emit(emptyList())
            } else {
                val filter = ids.joinToString(",") { "id.eq.$it" }

                val rows = fetchRows(
                    "posts?select=*,author:profiles!author_id(id,username,display_name,avatar_url,account_origin)" +
                        "&or=($filter)&limit=${ids.size}"
                )

                val reactions = uid?.let { fetchMyReactions(it) } ?: emptyMap()

                val saved = uid?.let {
                    fetchRows("post_saves?select=post_id&user_id=eq.${encode(it)}")
                        ?.mapNotNull { row -> row.string("post_id") }
                        .orEmpty()
                        .toSet()
                } ?: emptySet()

                emit(
                    rows.orEmpty()
                        .mapNotNull { parsePost(it, reactions, saved) }
                        .sortedByDescending { it.createdAt }
                )
            }

            withTimeoutOrNull(POLL_MS) { refreshSignal.first() }
        }
    }

    fun observePost(postId: String): Flow<Post?> = flow {
        while (currentCoroutineContext().isActive) {
            val uid = currentUid()

            val rows = fetchRows(
                "posts?select=*,author:profiles!author_id(id,username,display_name,avatar_url,account_origin)" +
                    "&id=eq.${encode(postId)}&limit=1"
            )

            val row = rows?.firstOrNull()

            if (row == null) {
                emit(null)
            } else {
                val reactions = uid?.let { fetchMyReactions(it, postId) } ?: emptyMap()

                val saved = uid?.let {
                    fetchRows(
                        "post_saves?select=post_id&user_id=eq.${encode(it)}&post_id=eq.${encode(postId)}"
                    )?.isNotEmpty() == true
                } ?: false

                emit(
                    parsePost(
                        row,
                        reactions,
                        if (saved) setOf(postId) else emptySet()
                    )
                )
            }

            withTimeoutOrNull(POLL_MS) { refreshSignal.first() }
        }
    }

    suspend fun getPost(postId: String): Post? {
        val rows = fetchRows(
            "posts?select=*,author:profiles!author_id(id,username,display_name,avatar_url,account_origin)" +
                "&id=eq.${encode(postId)}&limit=1"
        )

        val row = rows?.firstOrNull() ?: return null
        val uid = currentUid()

        val reactions = uid?.let { fetchMyReactions(it, postId) } ?: emptyMap()

        val saved = uid?.let {
            fetchRows(
                "post_saves?select=post_id&user_id=eq.${encode(it)}&post_id=eq.${encode(postId)}"
            )?.isNotEmpty() == true
        } ?: false

        return parsePost(
            row,
            reactions,
            if (saved) setOf(postId) else emptySet()
        )
    }

    suspend fun createPost(
        author: UserSummary,
        text: String,
        imageUrls: List<String>,
        tags: List<String>,
        audience: Audience,
        communityId: String?
    ): Post {
        val cleanText = text.trim()

        if (cleanText.isEmpty() && imageUrls.isEmpty()) {
            throw PostCreationException(FailureReason.REQUIRED)
        }

        val token = accessToken()
            ?: throw PostCreationException(FailureReason.NOT_AUTHENTICATED)

        if (author.id.isBlank()) {
            throw PostCreationException(FailureReason.NOT_AUTHENTICATED)
        }

        val uid = currentUid()
        if (uid == null || uid != author.id) {
            throw PostCreationException(FailureReason.NOT_AUTHENTICATED)
        }

        val postId = Ids.newId()

        val body = JsonObject().apply {
            addProperty("id", postId)
            addProperty("author_id", author.id)
            addProperty("text", cleanText)
            addProperty("image_uri", imageUrls.firstOrNull())

            add("image_uris", JsonArray().apply {
                imageUrls.forEach { add(it) }
            })

            add("tags", JsonArray().apply {
                tags.forEach { add(it) }
            })

            addProperty("audience", audience.name)

            if (communityId != null) {
                addProperty("community_id", communityId)
            }
        }

        val request = http.authenticatedRequest(
            "$restUrl/posts",
            token
        )
            .header("Prefer", "return=minimal")
            .post(http.jsonBody(body))
            .build()

        val response = http.execute(request).getOrElse { error ->
            throw PostCreationException(
                FailureReason.NETWORK,
                error.message ?: error.javaClass.simpleName
            )
        }

        if (!response.successful) {
            throw PostCreationException(
                postFailureReason(response.code, response.body),
                "HTTP ${response.code}: ${response.body}"
            )
        }

        notifyChanged()

        val now = System.currentTimeMillis()

        return Post(
            id = postId,
            author = author,
            text = cleanText,
            imageUri = imageUrls.firstOrNull(),
            imageUris = imageUrls,
            tags = tags,
            audience = audience,
            createdAt = now,
            updatedAt = now,
            likeCount = 0,
            commentCount = 0,
            shareCount = 0,
            likedByMe = false,
            savedByMe = false,
            communityId = communityId,
            isLocal = false
        )
    }

    suspend fun updatePost(
        postId: String,
        text: String,
        imageUrls: List<String>,
        tags: List<String>,
        audience: Audience
    ): Post? {
        val token = accessToken() ?: return null

        val body = JsonObject().apply {
            addProperty("text", text.trim())
            addProperty("image_uri", imageUrls.firstOrNull())

            add("image_uris", JsonArray().apply {
                imageUrls.forEach { add(it) }
            })

            add("tags", JsonArray().apply {
                tags.forEach { add(it) }
            })

            addProperty("audience", audience.name)
            addProperty("updated_at", Instant.now().toString())
        }

        val response = execute(
            http.authenticatedRequest(
                "$restUrl/posts?id=eq.${encode(postId)}",
                token
            )
                .header("Prefer", "return=minimal")
                .patch(http.jsonBody(body))
                .build()
        )

        if (response?.successful != true) {
            throw IllegalStateException(
                "POST_UPDATE_FAILED_HTTP_${response?.code ?: "NETWORK_ERROR"}: " +
                    (response?.body.orEmpty().take(1000))
            )
        }

        notifyChanged()
        return getPost(postId)
    }

    suspend fun toggleLike(postId: String, emoji: String? = null) {
        val uid = currentUid() ?: return
        val token = accessToken() ?: return

        val current = fetchRows(
            "post_likes?select=emoji&post_id=eq.${encode(postId)}&user_id=eq.${encode(uid)}"
        )?.firstOrNull()?.string("emoji")

        val desired: String? = when {
            emoji != null && current != emoji -> emoji
            emoji != null && current == emoji -> null
            else -> if (current != null) null else "👍"
        }

        if (current != null) {
            val deleteResponse = execute(
                http.authenticatedRequest(
                    "$restUrl/post_likes?post_id=eq.${encode(postId)}&user_id=eq.${encode(uid)}",
                    token
                ).delete().build()
            )
            if (deleteResponse?.successful != true) {
                throw IllegalStateException(
                    "POST_LIKE_DELETE_FAILED_HTTP_${deleteResponse?.code ?: "NETWORK_ERROR"}"
                )
            }
        }

        if (desired != null) {
            val body = JsonObject().apply {
                addProperty("post_id", postId)
                addProperty("user_id", uid)
                addProperty("emoji", desired)
            }

            val response = execute(
                http.authenticatedRequest("$restUrl/post_likes", token)
                    .header("Prefer", "return=minimal")
                    .post(http.jsonBody(body))
                    .build()
            )

            if (response?.successful != true) {
                throw IllegalStateException(
                    "POST_LIKE_FAILED_HTTP_${response?.code ?: "NETWORK_ERROR"}"
                )
            }
        }

        notifyChanged()
    }

    private suspend fun fetchMyReactions(uid: String, postId: String? = null): Map<String, String> {
        val filter = buildString {
            append("&user_id=eq.${encode(uid)}")
            if (postId != null) append("&post_id=eq.${encode(postId)}")
        }
        val rows = fetchRows("post_likes?select=post_id,emoji$filter")
            ?: fetchRows("post_likes?select=post_id$filter")
            ?: return emptyMap()
        return rows
            .mapNotNull { row ->
                val id = row.string("post_id") ?: return@mapNotNull null
                id to (row.string("emoji") ?: "❤️")
            }
            .toMap()
    }

    suspend fun getPostReactions(postId: String): List<PostReaction> {
        val rows = fetchRows(
            "post_likes?select=*,user:profiles!user_id(id,username,display_name,avatar_url,account_origin)&post_id=eq.${encode(postId)}&order=created_at.desc"
        ) ?: return emptyList()

        return rows.mapNotNull { row ->
            val userObj = row.getAsJsonObject("user") ?: return@mapNotNull null
            val userId = userObj.string("id") ?: return@mapNotNull null
            val username = userObj.string("username") ?: "user"
            val displayName = userObj.string("display_name") ?: username
            val avatarUrl = userObj.string("avatar_url")
            val origin = enumOrDefault(userObj.string("account_origin"), AccountOrigin.REMOTE_ACCOUNT)
            val userSummary = UserSummary(userId, username, displayName, avatarUrl, origin)
            val createdAt = parseMillis(row.string("created_at"))
            PostReaction(
                user = userSummary,
                emoji = row.string("emoji") ?: "❤️",
                createdAt = createdAt
            )
        }
    }

    suspend fun toggleSave(postId: String) {
        val uid = currentUid() ?: return
        val token = accessToken() ?: return

        val saved = fetchRows(
            "post_saves?select=post_id&post_id=eq.${encode(postId)}&user_id=eq.${encode(uid)}"
        )?.isNotEmpty() == true

        val response = if (saved) {
            execute(
                http.authenticatedRequest(
                    "$restUrl/post_saves?post_id=eq.${encode(postId)}&user_id=eq.${encode(uid)}",
                    token
                ).delete().build()
            )
        } else {
            val body = JsonObject().apply {
                addProperty("post_id", postId)
                addProperty("user_id", uid)
            }

            execute(
                http.authenticatedRequest("$restUrl/post_saves", token)
                    .header("Prefer", "return=minimal")
                    .post(http.jsonBody(body))
                    .build()
            )
        }

        if (response?.successful != true) {
            throw IllegalStateException(
                "POST_SAVE_FAILED_HTTP_${response?.code ?: "NETWORK_ERROR"}"
            )
        }

        notifyChanged()
    }

    suspend fun incrementShare(postId: String) {
        val uid = currentUid() ?: return
        val token = accessToken() ?: return

        val body = JsonObject().apply {
            addProperty("post_id", postId)
            addProperty("user_id", uid)
        }

        val response = execute(
            http.authenticatedRequest("$restUrl/post_shares", token)
                .header("Prefer", "resolution=ignore-duplicates,return=minimal")
                .post(http.jsonBody(body))
                .build()
        )

        if (response?.successful != true) {
            throw IllegalStateException(
                "POST_SHARE_FAILED_HTTP_${response?.code ?: "NETWORK_ERROR"}"
            )
        }

        notifyChanged()
    }

    suspend fun adjustCommentCount(postId: String, delta: Int) {
        val token = accessToken() ?: return
        val current = fetchRows("posts?select=comment_count&id=eq.${encode(postId)}&limit=1")
            ?.firstOrNull()
            ?.long("comment_count")?.toInt() ?: 0
        val newCount = (current + delta).coerceAtLeast(0)
        val body = JsonObject().apply {
            addProperty("comment_count", newCount)
        }
        execute(
            http.authenticatedRequest("$restUrl/posts?id=eq.${encode(postId)}", token)
                .header("Prefer", "return=minimal")
                .patch(http.jsonBody(body))
                .build()
        )
        notifyChanged()
    }

    suspend fun deletePost(postId: String): Boolean {
        val token = accessToken() ?: return false

        val response = execute(
            http.authenticatedRequest(
                "$restUrl/posts?id=eq.${encode(postId)}",
                token
            ).delete().build()
        )

        val deleted = response?.successful == true
        if (deleted) notifyChanged()
        return deleted
    }

    suspend fun refreshAuthor(author: UserSummary) {
        if (author.id.isBlank()) return
        // Author data is loaded through the profiles relation.
    }

    private fun parsePost(
        row: JsonObject,
        reactions: Map<String, String>,
        saved: Set<String>,
        forcedSaved: Boolean = false
    ): Post? {
        val id = row.string("id") ?: return null
        val authorObj = row.getAsJsonObject("author") ?: return null
        val authorId = authorObj.string("id") ?: return null

        val username = authorObj.string("username") ?: "user"
        val displayName = authorObj.string("display_name") ?: username
        val avatarUrl = authorObj.string("avatar_url")

        val origin = enumOrDefault(
            authorObj.string("account_origin"),
            AccountOrigin.REMOTE_ACCOUNT
        )

        val author = UserSummary(
            authorId,
            username,
            displayName,
            avatarUrl,
            origin
        )

        val text = row.string("text").orEmpty()
        val singleImage = row.string("image_uri")

        val imageUris = row.getAsJsonArray("image_uris")
            ?.mapNotNull { element ->
                runCatching { element.asString }.getOrNull()
            }
            ?: emptyList()

        val tags = row.getAsJsonArray("tags")
            ?.mapNotNull { element ->
                runCatching { element.asString }.getOrNull()
            }
            ?: emptyList()

        val audience = enumOrDefault(
            row.string("audience"),
            Audience.PUBLIC_LOCAL
        )

        val createdAt = parseMillis(row.string("created_at"))
        val updatedAt = parseMillis(row.string("updated_at"))
            .takeIf { it > 0L } ?: createdAt

        val likeCount = row.long("like_count")?.toInt() ?: 0
        val commentCount = row.long("comment_count")?.toInt() ?: 0
        val shareCount = row.long("share_count")?.toInt() ?: 0

        return Post(
            id = id,
            author = author,
            text = text,
            imageUri = singleImage ?: imageUris.firstOrNull(),
            imageUris = imageUris.ifEmpty { listOfNotNull(singleImage) },
            tags = tags,
            audience = audience,
            createdAt = createdAt,
            updatedAt = updatedAt,
            likeCount = likeCount,
            commentCount = commentCount,
            shareCount = shareCount,
            likedByMe = id in reactions,
            myReaction = reactions[id],
            savedByMe = forcedSaved || id in saved,
            communityId = row.string("community_id"),
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

    private suspend fun execute(
        request: okhttp3.Request
    ): SupabaseHttp.HttpResponse? =
        http.execute(request).getOrNull()

    private fun postFailureReason(code: Int, body: String): FailureReason {
        val message = body.lowercase()
        return when {
            code == 401 -> FailureReason.NOT_AUTHENTICATED
            code == 403 ||
                message.contains("row-level security") ||
                message.contains("permission denied") -> FailureReason.PERMISSION_DENIED
            code == 404 ||
                message.contains("could not find the table") ||
                message.contains("schema cache") -> FailureReason.NOT_CONFIGURED
            code == 413 || message.contains("too large") -> FailureReason.FILE_TOO_LARGE
            code >= 500 -> FailureReason.NETWORK
            else -> FailureReason.GENERIC
        }
    }

    private fun parseArray(body: String): List<JsonObject>? = runCatching {
        val element = JsonParser.parseString(body)

        if (!element.isJsonArray) {
            return@runCatching null
        }

        element.asJsonArray.mapNotNull {
            it.takeIf { item -> item.isJsonObject }?.asJsonObject
        }
    }.getOrNull()

    private fun parseMillis(value: String?): Long {
        if (value.isNullOrBlank()) return 0L

        return runCatching {
            Instant.parse(value).toEpochMilli()
        }.recoverCatching {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(
        value: String?,
        default: T
    ): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
