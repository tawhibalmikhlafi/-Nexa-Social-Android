package com.nexa.social.feature.pulse

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.string
import com.nexa.social.core.common.Resource
import com.nexa.social.data.media.MediaBucket
import com.nexa.social.data.media.MediaStorage
import android.net.Uri
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

data class DailyPulsePrompt(
    val id: String,
    val title: String,
    val description: String,
    val startsAt: String,
    val endsAt: String
)

data class DailyPulseEntry(
    val id: String,
    val userId: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String?,
    val body: String,
    val imageUrl: String?,
    val createdAt: String,
    val reactions: Int,
    val likedByMe: Boolean
)

data class DailyPulseFeed(
    val prompt: DailyPulsePrompt?,
    val entries: List<DailyPulseEntry>,
    val myEntryId: String?
)

data class PulseComment(
    val id: String,
    val userId: String,
    val displayName: String,
    val username: String,
    val body: String,
    val createdAt: String
)

data class PulseBadge(
    val id: String,
    val earnedAt: String
) {
    val title: String get() = when (id) {
        "pulse_first_entry" -> "أول نبضة"
        "pulse_five_entries" -> "صانع التحديات"
        "pulse_first_comment" -> "صوت المجتمع"
        "pulse_ten_comments" -> "مُشجّع المجتمع"
        "pulse_ten_likes" -> "محبوب المجتمع"
        else -> "إنجاز نبض"
    }
    val description: String get() = when (id) {
        "pulse_first_entry" -> "نشرت أول مشاركة في نبض Nexa"
        "pulse_five_entries" -> "شاركت في خمسة تحديات"
        "pulse_first_comment" -> "كتبت أول تعليق في نبض"
        "pulse_ten_comments" -> "كتبت عشرة تعليقات في نبض"
        "pulse_ten_likes" -> "حصلت مشاركاتك على عشرة إعجابات"
        else -> "إنجاز في نبض Nexa"
    }
}

/** Daily challenge data is always read/written through Supabase; no local demo fallback. */
class SupabaseDailyPulseRepository(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: suspend () -> String?,
    private val currentUid: suspend () -> String?,
    private val mediaStorage: MediaStorage?
) {
    private val rest get() = config.restUrl

    suspend fun load(): DailyPulseFeed? = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext null
        val uid = currentUid() ?: return@withContext null
        val now = URLEncoder.encode(Instant.now().toString(), StandardCharsets.UTF_8.name())
        val prompts = getRows(
            "pulse_prompts?select=id,title,description,starts_at,ends_at&is_published=eq.true&starts_at=lte.$now&ends_at=gt.$now&order=starts_at.desc&limit=1",
            token
        ) ?: return@withContext null
        val promptRow = prompts.firstOrNull() ?: return@withContext DailyPulseFeed(null, emptyList(), null)
        val prompt = DailyPulsePrompt(
            id = promptRow.string("id") ?: return@withContext null,
            title = promptRow.string("title").orEmpty(),
            description = promptRow.string("description").orEmpty(),
            startsAt = promptRow.string("starts_at").orEmpty(),
            endsAt = promptRow.string("ends_at").orEmpty()
        )
        val entries = getRows(
            "pulse_entries?select=id,user_id,body,image_path,created_at&prompt_id=eq.${enc(prompt.id)}&order=created_at.desc&limit=100",
            token
        ) ?: return@withContext null
        if (entries.isEmpty()) return@withContext DailyPulseFeed(prompt, emptyList(), null)

        val userIds = entries.mapNotNull { it.string("user_id") }.distinct()
        val entryIds = entries.mapNotNull { it.string("id") }
        val profiles = getRows(
            "profiles?select=id,username,display_name,avatar_url&id=in.(${userIds.joinToString(",") { enc(it) }})",
            token
        ) ?: return@withContext null
        val profileById = profiles.associateBy { it.string("id").orEmpty() }
        val reactions = getRows(
            "pulse_reactions?select=entry_id,user_id&entry_id=in.(${entryIds.joinToString(",") { enc(it) }})",
            token
        ) ?: return@withContext null
        val reactionGroups = reactions.groupBy { it.string("entry_id").orEmpty() }
        val mapped = entries.mapNotNull { row ->
            val id = row.string("id") ?: return@mapNotNull null
            val authorId = row.string("user_id") ?: return@mapNotNull null
            val profile = profileById[authorId]
            val username = profile?.string("username") ?: "user"
            val rowReactions = reactionGroups[id].orEmpty()
            DailyPulseEntry(
                id = id,
                userId = authorId,
                displayName = profile?.string("display_name") ?: username,
                username = username,
                avatarUrl = profile?.string("avatar_url"),
                body = row.string("body").orEmpty(),
                imageUrl = row.string("image_path")?.let { publicImageUrl(it) },
                createdAt = row.string("created_at").orEmpty(),
                reactions = rowReactions.size,
                likedByMe = rowReactions.any { it.string("user_id") == uid }
            )
        }
        DailyPulseFeed(prompt, mapped, mapped.firstOrNull { it.userId == uid }?.id)
    }

    suspend fun publish(promptId: String, bodyText: String, imageUri: String? = null): Boolean {
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val clean = bodyText.trim()
        if (!PulseInputRules.validEntry(clean, imageUri)) return false
        var imagePath: String? = null
        if (!imageUri.isNullOrBlank()) {
            val storage = mediaStorage ?: return false
            val extension = when (Uri.parse(imageUri).lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
                "png" -> "png"
                "webp" -> "webp"
                else -> "jpg"
            }
            val path = "$uid/${UUID.randomUUID()}.$extension"
            when (val uploaded = storage.uploadImage(imageUri, MediaBucket.PULSE, path)) {
                is Resource.Success -> imagePath = path
                else -> return false
            }
        }
        val body = JsonObject().apply {
            addProperty("prompt_id", promptId)
            addProperty("user_id", uid)
            addProperty("body", clean)
            if (imagePath != null) addProperty("image_path", imagePath)
        }
        val response = http.execute(
            http.authenticatedRequest("$rest/pulse_entries", token)
                .header("Prefer", "return=minimal")
                .post(http.jsonBody(body)).build()
        ).getOrNull()
        if (response?.successful == true) return true
        imagePath?.let { mediaStorage?.deleteObject(MediaBucket.PULSE, it) }
        return false
    }

    private fun publicImageUrl(path: String): String =
        config.normalizedUrl + "/storage/v1/object/public/nexa-pulse/" +
            path.split('/').joinToString("/") { Uri.encode(it) }

    suspend fun loadComments(entryId: String): List<PulseComment>? = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext null
        val rows = getRows(
            "pulse_comments?select=id,user_id,body,created_at&entry_id=eq.${enc(entryId)}&order=created_at.asc&limit=100",
            token
        ) ?: return@withContext null
        if (rows.isEmpty()) return@withContext emptyList()
        val ids = rows.mapNotNull { it.string("user_id") }.distinct()
        val profiles = getRows(
            "profiles?select=id,username,display_name&id=in.(${ids.joinToString(",") { enc(it) }})",
            token
        ) ?: return@withContext null
        val byId = profiles.associateBy { it.string("id").orEmpty() }
        rows.mapNotNull { row ->
            val id = row.string("id") ?: return@mapNotNull null
            val userId = row.string("user_id") ?: return@mapNotNull null
            val profile = byId[userId]
            val username = profile?.string("username") ?: "user"
            PulseComment(
                id = id,
                userId = userId,
                displayName = profile?.string("display_name") ?: username,
                username = username,
                body = row.string("body").orEmpty(),
                createdAt = row.string("created_at").orEmpty()
            )
        }
    }

    suspend fun addComment(entryId: String, bodyText: String): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext false
        val token = accessToken() ?: return@withContext false
        val clean = bodyText.trim()
        if (!PulseInputRules.validComment(clean)) return@withContext false
        val body = JsonObject().apply {
            addProperty("entry_id", entryId)
            addProperty("user_id", uid)
            addProperty("body", clean)
        }
        http.execute(
            http.authenticatedRequest("$rest/pulse_comments", token)
                .header("Prefer", "return=minimal")
                .post(http.jsonBody(body)).build()
        ).getOrNull()?.successful == true
    }

    suspend fun loadMyBadges(): List<PulseBadge>? = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext null
        val token = accessToken() ?: return@withContext null
        val rows = getRows(
            "pulse_user_badges?select=badge_id,earned_at&user_id=eq.${enc(uid)}&order=earned_at.desc",
            token
        ) ?: return@withContext null
        rows.mapNotNull { row ->
            val id = row.string("badge_id") ?: return@mapNotNull null
            PulseBadge(id, row.string("earned_at").orEmpty())
        }
    }

    suspend fun setReaction(entryId: String, enabled: Boolean): Boolean {
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val url = "$rest/pulse_reactions?entry_id=eq.${enc(entryId)}&user_id=eq.${enc(uid)}"
        val request = if (enabled) {
            val body = JsonObject().apply {
                addProperty("entry_id", entryId)
                addProperty("user_id", uid)
            }
            http.authenticatedRequest("$rest/pulse_reactions", token)
                .header("Prefer", "resolution=ignore-duplicates,return=minimal")
                .post(http.jsonBody(body)).build()
        } else {
            http.authenticatedRequest(url, token).delete().build()
        }
        return http.execute(request).getOrNull()?.successful == true
    }

    suspend fun deleteEntry(entryId: String): Boolean {
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val imagePath = getRows(
            "pulse_entries?select=image_path&id=eq.${enc(entryId)}&user_id=eq.${enc(uid)}&limit=1", token
        )?.firstOrNull()?.string("image_path")
        val request = http.authenticatedRequest(
            "$rest/pulse_entries?id=eq.${enc(entryId)}&user_id=eq.${enc(uid)}", token
        ).delete().build()
        val ok = http.execute(request).getOrNull()?.successful == true
        if (ok) imagePath?.let { mediaStorage?.deleteObject(MediaBucket.PULSE, it) }
        return ok
    }

    private suspend fun getRows(path: String, token: String): List<JsonObject>? {
        val response = http.execute(
            http.authenticatedRequest("$rest/$path", token).get().build()
        ).getOrNull() ?: return null
        if (!response.successful) return null
        return runCatching {
            JsonParser.parseString(response.body).asJsonArray.map { it.asJsonObject }
        }.getOrNull()
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
