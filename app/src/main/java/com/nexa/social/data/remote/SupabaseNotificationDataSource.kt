package com.nexa.social.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.RefreshBus
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.boolean
import com.nexa.social.core.supabase.string
import com.nexa.social.domain.model.NexaNotification
import com.nexa.social.domain.model.NotificationTargetKind
import com.nexa.social.domain.model.NotificationType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

private const val POLL_MS = 20_000L

/**
 * Reads the notifications produced by the server-side triggers. Rows are never written from the
 * client: only `is_read` and deletion are allowed, both enforced by RLS.
 */
class SupabaseNotificationDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?,
) {
    private val restUrl: String get() = config.restUrl

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun refresh() {
        refreshSignal.tryEmit(Unit)
    }

    suspend fun loadPreferences(uid: String): Pair<Boolean, Map<NotificationType, Boolean>>? {
        val token = accessToken() ?: return null
        val response = execute(
            http.authenticatedRequest(
                "$restUrl/user_notification_preferences?user_id=eq.${encode(uid)}&select=notifications_enabled,categories&limit=1",
                token
            ).get().build()
        ) ?: return null
        if (!response.successful) return null
        val row = runCatching { JsonParser.parseString(response.body).asJsonArray.firstOrNull()?.asJsonObject }.getOrNull()
            ?: return Pair(true, emptyMap())
        val enabled = row.boolean("notifications_enabled") ?: true
        val json = row.getAsJsonObject("categories")
        val categories = NotificationType.entries.mapNotNull { type ->
            json?.get(type.name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
                ?.let { type to it.asBoolean }
        }.toMap()
        return enabled to categories
    }

    suspend fun savePreferences(
        uid: String,
        enabled: Boolean,
        categories: Map<NotificationType, Boolean>
    ): Boolean {
        val token = accessToken() ?: return false
        val categoryJson = JsonObject().apply {
            categories.forEach { (type, value) -> addProperty(type.name, value) }
        }
        val body = JsonObject().apply {
            addProperty("user_id", uid)
            addProperty("notifications_enabled", enabled)
            add("categories", categoryJson)
        }
        val response = execute(
            http.authenticatedRequest("$restUrl/user_notification_preferences?on_conflict=user_id", token)
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(http.jsonBody(body)).build()
        ) ?: return false
        return response.successful
    }

    private suspend fun awaitNextPoll() {
        withTimeoutOrNull(POLL_MS) {
            merge(refreshSignal, RefreshBus.requests).first()
        }
    }

    fun observeNotifications(uid: String): Flow<List<NexaNotification>> = flow {
        while (currentCoroutineContext().isActive) {
            // A failed fetch must not wipe the list: keep showing the last known rows offline.
            fetch(uid)?.let { emit(it) }
            awaitNextPoll()
        }
    }

    suspend fun markRead(id: String) {
        val token = accessToken() ?: return
        val body = JsonObject().apply { addProperty("is_read", true) }
        execute(
            http.authenticatedRequest("$restUrl/notifications?id=eq.${encode(id)}", token)
                .header("Prefer", "return=minimal")
                .patch(http.jsonBody(body))
                .build()
        )
    }

    suspend fun markAllRead(uid: String) {
        val token = accessToken() ?: return
        val body = JsonObject().apply { addProperty("is_read", true) }
        execute(
            http.authenticatedRequest(
                "$restUrl/notifications?user_id=eq.${encode(uid)}&is_read=eq.false",
                token
            )
                .header("Prefer", "return=minimal")
                .patch(http.jsonBody(body))
                .build()
        )
    }

    suspend fun delete(id: String) {
        val token = accessToken() ?: return
        execute(
            http.authenticatedRequest("$restUrl/notifications?id=eq.${encode(id)}", token)
                .delete()
                .build()
        )
    }

    suspend fun deleteAll(uid: String) {
        val token = accessToken() ?: return
        execute(
            http.authenticatedRequest("$restUrl/notifications?user_id=eq.${encode(uid)}", token)
                .delete()
                .build()
        )
    }

    private suspend fun fetch(uid: String): List<NexaNotification>? {
        val token = accessToken() ?: return null
        val query = "notifications?select=*,actor:profiles!notifications_actor_id_fkey(" +
            "id,username,display_name,avatar_url)" +
            "&user_id=eq.${encode(uid)}&order=created_at.desc&limit=100"
        val response = execute(
            http.authenticatedRequest("$restUrl/$query", token).get().build()
        ) ?: return null
        if (!response.successful) return null
        return runCatching {
            JsonParser.parseString(response.body).asJsonArray.mapNotNull { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject?.let(::toNotification)
            }
        }.getOrNull()
    }

    private fun toNotification(row: JsonObject): NexaNotification? {
        val id = row.string("id") ?: return null
        val type = NotificationType.fromWire(row.string("type")) ?: return null
        val actor = row.getAsJsonObject("actor")
        val actorName = actor?.string("display_name")?.takeIf { it.isNotBlank() }
            ?: actor?.string("username")
        val actorId = row.string("actor_id")
        val postId = row.string("post_id")
        val groupId = row.string("group_id")
        val targetId = when (type) {
            NotificationType.LIKE,
            NotificationType.COMMENT,
            NotificationType.REPLY,
            NotificationType.COMMENT_LIKE,
            NotificationType.MENTION -> postId
            NotificationType.FOLLOW,
            NotificationType.MESSAGE,
            NotificationType.STORY_REPLY -> actorId
            NotificationType.GROUP_JOIN_REQUEST,
            NotificationType.GROUP_JOIN_APPROVED,
            NotificationType.GROUP_JOIN_REJECTED -> groupId
            else -> null
        }
        return NexaNotification(
            id = id,
            type = type,
            title = "",
            body = row.string("preview").orEmpty(),
            timestamp = parseMillis(row.string("created_at")),
            isRead = row.boolean("is_read") ?: false,
            actorName = actorName,
            actorAvatarUri = actor?.string("avatar_url"),
            targetKind = when (type) {
                NotificationType.LIKE,
                NotificationType.COMMENT,
                NotificationType.REPLY,
                NotificationType.COMMENT_LIKE,
                NotificationType.MENTION -> NotificationTargetKind.POST
                NotificationType.FOLLOW,
                NotificationType.MESSAGE,
                NotificationType.STORY_REPLY -> NotificationTargetKind.USER
                NotificationType.GROUP_JOIN_REQUEST,
                NotificationType.GROUP_JOIN_APPROVED,
                NotificationType.GROUP_JOIN_REJECTED -> NotificationTargetKind.GROUP
                else -> NotificationTargetKind.NONE
            },
            targetId = targetId
        )
    }

    private suspend fun execute(request: okhttp3.Request): SupabaseHttp.HttpResponse? =
        http.execute(request).getOrNull()

    private fun parseMillis(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrDefault(0L)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
