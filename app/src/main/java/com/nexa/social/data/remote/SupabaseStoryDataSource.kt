package com.nexa.social.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.long
import com.nexa.social.core.supabase.string
import com.nexa.social.domain.model.AccountOrigin
import com.nexa.social.domain.model.Story
import com.nexa.social.domain.model.StoryAudienceMember
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
import java.time.temporal.ChronoUnit

private const val POLL_MS = 8_000L

/**
 * Remote stories backed by PostgREST. The server owns expiry and visibility; this data source
 * only maps rows and writes the current user's story.
 */
class SupabaseStoryDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?,
    private val currentUid: () -> String?
) {
    private val restUrl: String get() = config.restUrl
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun notifyChanged() {
        refreshSignal.tryEmit(Unit)
    }

    fun observeStories(): Flow<List<Story>> = flow {
        while (currentCoroutineContext().isActive) {
            val uid = currentUid()
            val now = Instant.now().toString()
            val rows = fetchRows(
                "stories?select=*,author:profiles!author_id" +
                    "(id,username,display_name,avatar_url,account_origin)" +
                    "&expires_at=gt.${encode(now)}&order=created_at.asc"
            )
            emit(
                rows.orEmpty()
                    .mapNotNull { parseStory(it, uid) }
                    .filterNot { it.isExpired() }
            )
            withTimeoutOrNull(POLL_MS) { refreshSignal.first() }
        }
    }

    suspend fun createStory(author: UserSummary, imageUri: String?, text: String?): Story? {
        val token = accessToken() ?: return null
        val uid = currentUid()
        if (uid == null || uid != author.id) return null

        val storyId = Ids.newId()
        val createdAt = Instant.now()
        val body = JsonObject().apply {
            addProperty("id", storyId)
            addProperty("author_id", author.id)
            addProperty("image_uri", imageUri)
            addProperty("text", text?.trim().orEmpty())
            addProperty("created_at", createdAt.toString())
            addProperty("expires_at", createdAt.plus(24, ChronoUnit.HOURS).toString())
        }
        val response = http.execute(
            http.authenticatedRequest("$restUrl/stories", token)
                .header("Prefer", "return=minimal")
                .post(http.jsonBody(body))
                .build()
        ).getOrNull() ?: return null
        if (!response.successful) return null

        notifyChanged()
        return Story(
            id = storyId,
            author = author,
            imageUri = imageUri,
            text = text?.trim()?.ifBlank { null },
            createdAt = createdAt.toEpochMilli(),
            expiresAt = createdAt.plus(24, ChronoUnit.HOURS).toEpochMilli(),
            viewedLocally = true,
            isMine = true,
            isLocal = false
        )
    }

    suspend fun deleteStory(storyId: String): Boolean {
        val token = accessToken() ?: return false
        val uid = currentUid() ?: return false
        val response = http.execute(
            http.authenticatedRequest(
                "$restUrl/stories?id=eq.${encode(storyId)}&author_id=eq.${encode(uid)}",
                token
            ).delete().build()
        ).getOrNull() ?: return false
        if (response.successful) notifyChanged()
        return response.successful
    }

    /** Records the current user's view in `story_views`; the RPC is idempotent. */
    suspend fun recordView(storyId: String): Boolean {
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("target_story_id", storyId)
        }
        val response = http.execute(
            http.authenticatedRequest("$restUrl/rpc/record_story_view", token)
                .post(http.jsonBody(body))
                .build()
        ).getOrNull() ?: return false
        return response.successful
    }

    /** Owner-only list of viewers and reply interactions for a story. */
    suspend fun fetchAudience(storyId: String): Resource<List<StoryAudienceMember>> {
        val token = accessToken()
            ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply {
            addProperty("target_story_id", storyId)
        }
        val response = http.execute(
            http.authenticatedRequest("$restUrl/rpc/get_story_audience", token)
                .post(http.jsonBody(body))
                .build()
        ).getOrElse { error ->
            return Resource.Failure(http.transportFailure(error))
        }
        if (!response.successful) {
            return Resource.Failure(http.failureReason(response))
        }
        val rows = parseArray(response.body)
            ?: return Resource.Failure(FailureReason.GENERIC)
        return Resource.Success(rows.mapNotNull(::parseAudience))
    }

    private fun parseAudience(row: JsonObject): StoryAudienceMember? {
        val userId = row.string("user_id") ?: return null
        val username = row.string("username") ?: "user"
        val displayName = row.string("display_name") ?: username
        val user = UserSummary(
            id = userId,
            username = username,
            displayName = displayName,
            avatarUri = row.string("avatar_url"),
            accountOrigin = enumOrDefault(
                row.string("account_origin"),
                AccountOrigin.REMOTE_ACCOUNT
            )
        )
        return StoryAudienceMember(
            user = user,
            viewedAt = row.string("viewed_at")?.let(::parseMillis)?.takeIf { it > 0L },
            interactedAt = row.string("interacted_at")?.let(::parseMillis)?.takeIf { it > 0L },
            interactionText = row.string("interaction_text")?.trim()?.ifBlank { null },
            interactionCount = row.long("interaction_count")?.toInt() ?: 0
        )
    }

    private fun parseStory(row: JsonObject, uid: String?): Story? {
        val id = row.string("id") ?: return null
        val authorRow = row.getAsJsonObject("author") ?: return null
        val authorId = authorRow.string("id") ?: return null
        val username = authorRow.string("username") ?: "user"
        val displayName = authorRow.string("display_name") ?: username
        val author = UserSummary(
            id = authorId,
            username = username,
            displayName = displayName,
            avatarUri = authorRow.string("avatar_url"),
            accountOrigin = enumOrDefault(
                authorRow.string("account_origin"),
                AccountOrigin.REMOTE_ACCOUNT
            )
        )
        val createdAt = parseMillis(row.string("created_at"))
        val expiresAt = parseMillis(row.string("expires_at"))
        if (createdAt <= 0L || expiresAt <= createdAt) return null
        return Story(
            id = id,
            author = author,
            imageUri = row.string("image_uri"),
            text = row.string("text")?.takeIf { it.isNotBlank() },
            createdAt = createdAt,
            expiresAt = expiresAt,
            viewedLocally = false,
            isMine = authorId == uid,
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
        val response = http.execute(builder.build()).getOrNull() ?: return null
        if (!response.successful) return null
        return parseArray(response.body)
    }

    private fun parseArray(body: String): List<JsonObject>? = runCatching {
        val element = JsonParser.parseString(body)
        if (!element.isJsonArray) return@runCatching null
        element.asJsonArray.mapNotNull {
            it.takeIf { item -> item.isJsonObject }?.asJsonObject
        }
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
