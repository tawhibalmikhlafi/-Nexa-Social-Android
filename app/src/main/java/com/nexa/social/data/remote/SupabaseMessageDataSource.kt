package com.nexa.social.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.RefreshBus
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.boolean
import com.nexa.social.core.supabase.long
import com.nexa.social.core.supabase.string
import com.nexa.social.domain.model.AccountOrigin
import com.nexa.social.domain.model.Conversation
import com.nexa.social.domain.model.Message
import com.nexa.social.domain.model.MessageReaction
import com.nexa.social.domain.model.UserSummary
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

private const val POLL_MS = 6_000L

class SupabaseMessageDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?,
    private val remoteUsers: UserRemoteDataSource?
) {
    private val restUrl: String get() = config.restUrl
    private val messageCache = java.util.concurrent.ConcurrentHashMap<String, List<Message>>()

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun refresh() {
        refreshSignal.tryEmit(Unit)
    }

    private suspend fun awaitNextPoll() {
        withTimeoutOrNull(POLL_MS) {
            merge(refreshSignal, RefreshBus.requests).first()
        }
    }

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        while (currentCoroutineContext().isActive) {
            val rows = fetchRows(
                "conversations?select=*," +
                    "participant_a:profiles!user_a(id,username,display_name,avatar_url,account_origin)," +
                    "participant_b:profiles!user_b(id,username,display_name,avatar_url,account_origin)" +
                    "&or=(user_a.eq.${encode(uid)},user_b.eq.${encode(uid)})&order=last_message_at.desc"
            )
            if (rows == null) {
                // Keep the last known conversations on screen while offline.
                awaitNextPoll()
                continue
            }
            val conversations = mutableListOf<Conversation>()
            rows.forEach { row ->
                val id = row.string("id") ?: return@forEach
                val userA = row.string("user_a") ?: return@forEach
                val userB = row.string("user_b") ?: return@forEach
                val otherUid = if (userA == uid) userB else userA
                // PostgREST embeds can be JSON null when the related profile is
                // missing or hidden by RLS. JsonObject.getAsJsonObject(key) casts
                // JsonNull and crashes the conversation collector on the main flow.
                val participantKey = if (userA == uid) "participant_b" else "participant_a"
                val participantRow = row.get(participantKey)
                    ?.takeIf { it.isJsonObject && !it.isJsonNull }
                    ?.asJsonObject
                val participant = participantRow?.let { profile ->
                    val username = profile.string("username") ?: "user"
                    UserSummary(
                        id = profile.string("id") ?: otherUid,
                        username = username,
                        displayName = profile.string("display_name") ?: username,
                        avatarUri = profile.string("avatar_url"),
                        accountOrigin = enumOrDefault(
                            profile.string("account_origin"),
                            AccountOrigin.REMOTE_ACCOUNT
                        )
                    )
                } ?: remoteUsers?.getProfile(otherUid, includePrivate = false)?.let {
                    UserSummary.from(it)
                } ?: UserSummary(otherUid, "user", "User", null, AccountOrigin.REMOTE_ACCOUNT)
                val suffix = "&conversation_id=eq.${encode(id)}&order=created_at.desc&limit=100"
                val msgRows = fetchRows(
                    "messages?select=*,reactions:message_reactions(emoji,user_id),stars:message_stars(user_id)$suffix"
                ) ?: fetchRows("messages?select=*$suffix")
                val messages = if (msgRows != null) {
                    msgRows.mapNotNull { parseMessage(it, uid) }
                        .reversed()
                        .also { messageCache[id] = it }
                } else {
                    messageCache[id].orEmpty()
                }
                val unreadCount = messages.count { !it.isRead && !it.isMine }

                conversations.add(
                    Conversation(
                        id = id,
                        participant = participant,
                        lastMessagePreview = row.string("last_message_preview").orEmpty(),
                        lastTimestamp = parseMillis(row.string("last_message_at")),
                        unreadCount = unreadCount,
                        messages = messages,
                        isLocalSample = false
                    )
                )
            }
            emit(conversations)
            awaitNextPoll()
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        text: String,
        attachmentPath: String?,
        attachmentMime: String?,
        attachmentName: String?,
        replyToStoryId: String? = null,
        messageId: String = Ids.newId()
    ): Message? {
        val token = accessToken() ?: return null
        // Keep the same ID across retries so the persistent outbox is idempotent.
        val body = JsonObject().apply {
            addProperty("id", messageId)
            addProperty("conversation_id", conversationId)
            addProperty("sender_id", senderId)
            addProperty("text", text.trim())
            if (!attachmentPath.isNullOrBlank()) addProperty("attachment_path", attachmentPath)
            if (!attachmentMime.isNullOrBlank()) addProperty("attachment_mime", attachmentMime)
            if (!attachmentName.isNullOrBlank()) addProperty("attachment_name", attachmentName)
            if (!replyToStoryId.isNullOrBlank()) addProperty("reply_to_story_id", replyToStoryId)
        }
        val response = execute(
            http.authenticatedRequest("$restUrl/messages", token)
                .header("Prefer", "return=minimal")
                .post(http.jsonBody(body))
                .build()
        )
        if (response?.successful != true) {
            // The first insert may have committed even if its response was lost.
            val existing = fetchRows("messages?select=*&id=eq." + encode(messageId) + "&limit=1")
                ?.firstOrNull()
            if (existing != null) return parseMessage(existing, senderId)
            return null
        }
        val now = System.currentTimeMillis()
        return Message(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            text = text.trim(),
            timestamp = now,
            isRead = true,
            isMine = true,
            isLocal = false,
            attachmentPath = attachmentPath,
            attachmentMime = attachmentMime,
            attachmentName = attachmentName,
            replyToStoryId = replyToStoryId
        )
    }

    suspend fun startConversation(uid: String, participantId: String, conversationId: String = Ids.newId()): String? {
        val token = accessToken() ?: return null
        val userA = if (uid < participantId) uid else participantId
        val userB = if (uid < participantId) participantId else uid
        
        val existing = fetchRows("conversations?select=id&user_a=eq.${encode(userA)}&user_b=eq.${encode(userB)}&limit=1")
        existing?.firstOrNull()?.string("id")?.let { return it }

        val convId = conversationId
        val body = JsonObject().apply {
            addProperty("id", convId)
            addProperty("user_a", userA)
            addProperty("user_b", userB)
        }
        val response = execute(
            http.authenticatedRequest("$restUrl/conversations", token)
                .header("Prefer", "resolution=ignore-duplicates,return=representation")
                .post(http.jsonBody(body))
                .build()
        )
        if (response?.successful == true) {
            val parsed = parseArray(response.body)
            parsed?.firstOrNull()?.string("id")?.let { return it }
        }
        val recheck = fetchRows("conversations?select=id&user_a=eq.${encode(userA)}&user_b=eq.${encode(userB)}&limit=1")
        return recheck?.firstOrNull()?.string("id")
    }

    suspend fun setMessageRequestStatus(conversationId: String, uid: String, status: String): Boolean {
        if (status !in setOf("accepted", "rejected")) return false
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("user_id", uid)
            addProperty("conversation_id", conversationId)
            addProperty("status", status)
            addProperty("updated_at", Instant.now().toString())
        }
        val response = execute(
            http.authenticatedRequest("$restUrl/message_request_decisions?on_conflict=user_id,conversation_id", token)
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(http.jsonBody(body)).build()
        )
        return response?.successful == true
    }

    suspend fun getResolvedMessageRequestIds(uid: String): Set<String>? {
        if (accessToken() == null) return null
        val rows = fetchRows(
            "message_request_decisions?select=conversation_id&user_id=eq.${encode(uid)}&status=in.(accepted,rejected)"
        ) ?: return null
        return rows.mapNotNull { it.string("conversation_id") }.toSet()
    }

    suspend fun markRead(conversationId: String, uid: String): Boolean {
        val token = accessToken() ?: return false
        val body = JsonObject().apply { addProperty("read_at", Instant.now().toString()) }
        return execute(
            http.authenticatedRequest("$restUrl/messages?conversation_id=eq.${encode(conversationId)}&sender_id=neq.${encode(uid)}&read_at=is.null", token)
                .header("Prefer", "return=minimal").patch(http.jsonBody(body)).build()
        )?.successful == true
    }

    suspend fun deleteMessage(messageId: String): Boolean {
        val token = accessToken() ?: return false
        val response = execute(
            http.authenticatedRequest("$restUrl/messages?id=eq.${encode(messageId)}", token)
                .delete().build()
        )
        return response?.successful == true
    }

    suspend fun deleteForMe(messageId: String, uid: String): Boolean {
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("message_id", messageId)
            addProperty("user_id", uid)
        }
        val response = execute(
            http.authenticatedRequest("$restUrl/message_deletions", token)
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(http.jsonBody(body))
                .build()
        )
        return response?.successful == true
    }

    suspend fun deleteForEveryone(messageId: String): Boolean {
        val token = accessToken() ?: return false
        val body = JsonObject().apply { addProperty("is_deleted", true) }
        val response = execute(
            http.authenticatedRequest("$restUrl/messages?id=eq.${encode(messageId)}", token)
                .header("Prefer", "return=minimal")
                .patch(http.jsonBody(body))
                .build()
        )
        if (response?.successful == true) return true
        // Older or partially migrated projects reject the soft-delete write. Removing the row is
        // an equivalent "for everyone" outcome and is allowed by the nexa_messages_delete policy.
        val deleted = execute(
            http.authenticatedRequest("$restUrl/messages?id=eq.${encode(messageId)}", token)
                .header("Prefer", "return=minimal")
                .delete()
                .build()
        )
        return deleted?.successful == true
    }

    suspend fun editMessage(messageId: String, text: String): Boolean {
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("text", text.trim())
            addProperty("edited_at", Instant.now().toString())
        }
        val response = execute(
            http.authenticatedRequest("$restUrl/messages?id=eq.${encode(messageId)}", token)
                .header("Prefer", "return=minimal")
                .patch(http.jsonBody(body))
                .build()
        )
        return response?.successful == true
    }

    suspend fun setStarred(messageId: String, uid: String, starred: Boolean): Boolean {
        val token = accessToken() ?: return false
        val request = if (starred) {
            val body = JsonObject().apply { addProperty("message_id", messageId); addProperty("user_id", uid) }
            http.authenticatedRequest("$restUrl/message_stars?on_conflict=message_id,user_id", token)
                .header("Prefer", "resolution=ignore-duplicates,return=minimal")
                .post(http.jsonBody(body)).build()
        } else {
            http.authenticatedRequest("$restUrl/message_stars?message_id=eq.${encode(messageId)}&user_id=eq.${encode(uid)}", token).delete().build()
        }
        return execute(request)?.successful == true
    }
    suspend fun reactToMessage(messageId: String, uid: String, emoji: String): Boolean {
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("message_id", messageId)
            addProperty("user_id", uid)
            addProperty("emoji", emoji)
        }
        val response = execute(
            http.authenticatedRequest("$restUrl/message_reactions?on_conflict=message_id,user_id", token)
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(http.jsonBody(body))
                .build()
        )
        return response?.successful == true
    }

    suspend fun unreactToMessage(messageId: String, uid: String): Boolean {
        val token = accessToken() ?: return false
        val response = execute(
            http.authenticatedRequest(
                "$restUrl/message_reactions?message_id=eq.${encode(messageId)}&user_id=eq.${encode(uid)}",
                token
            ).delete().build()
        )
        return response?.successful == true
    }

    private fun parseMessage(row: JsonObject, uid: String): Message? {
        val id = row.string("id") ?: return null
        if (row.boolean("is_deleted") == true) return null
        val conversationId = row.string("conversation_id") ?: return null
        val senderId = row.string("sender_id") ?: return null
        val text = row.string("text").orEmpty()
        val createdAt = parseMillis(row.string("created_at"))
        val readAt = row.string("read_at")
        val editedAt = parseMillis(row.string("edited_at")).takeIf { it > 0L }
        val isMine = senderId == uid

        return Message(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            text = text,
            timestamp = createdAt,
            isRead = readAt != null || isMine,
            isMine = isMine,
            isLocal = false,
            attachmentPath = row.string("attachment_path"),
            attachmentMime = row.string("attachment_mime"),
            attachmentName = row.string("attachment_name"),
            editedAt = editedAt,
            replyToStoryId = row.string("reply_to_story_id"),
            reactions = parseReactions(row),
            isStarred = runCatching { row.getAsJsonArray("stars").any { it.asJsonObject.string("user_id") == uid } }.getOrDefault(false)
        )
    }

    private fun parseReactions(row: JsonObject): List<MessageReaction>? {
        val array = runCatching { row.getAsJsonArray("reactions") }.getOrNull() ?: return null
        val list = array.mapNotNull { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val emoji = item.string("emoji") ?: return@mapNotNull null
            val userId = item.string("user_id") ?: return@mapNotNull null
            MessageReaction(emoji = emoji, userId = userId, userName = null)
        }
        return list.ifEmpty { null }
    }

    private suspend fun fetchRows(path: String): List<JsonObject>? {
        val token = accessToken() ?: return null
        val response = execute(
            http.authenticatedRequest("$restUrl/$path", token).get().build()
        ) ?: return null
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

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
