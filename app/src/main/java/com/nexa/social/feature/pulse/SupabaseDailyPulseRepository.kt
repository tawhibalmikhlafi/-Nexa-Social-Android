package com.nexa.social.feature.pulse

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.string
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
    val createdAt: String,
    val reactions: Int,
    val likedByMe: Boolean
)

data class DailyPulseFeed(
    val prompt: DailyPulsePrompt?,
    val entries: List<DailyPulseEntry>,
    val myEntryId: String?
)

/** Daily challenge data is always read/written through Supabase; no local demo fallback. */
class SupabaseDailyPulseRepository(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: suspend () -> String?,
    private val currentUid: suspend () -> String?
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
            "pulse_entries?select=id,user_id,body,created_at&prompt_id=eq.${enc(prompt.id)}&order=created_at.desc&limit=100",
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
                createdAt = row.string("created_at").orEmpty(),
                reactions = rowReactions.size,
                likedByMe = rowReactions.any { it.string("user_id") == uid }
            )
        }
        DailyPulseFeed(prompt, mapped, mapped.firstOrNull { it.userId == uid }?.id)
    }

    suspend fun publish(promptId: String, bodyText: String): Boolean {
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val clean = bodyText.trim()
        if (clean.isEmpty() || clean.length > 1000) return false
        val body = JsonObject().apply {
            addProperty("prompt_id", promptId)
            addProperty("user_id", uid)
            addProperty("body", clean)
        }
        val response = http.execute(
            http.authenticatedRequest("$rest/pulse_entries", token)
                .header("Prefer", "return=minimal")
                .post(http.jsonBody(body)).build()
        ).getOrNull() ?: return false
        return response.successful
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
        val request = http.authenticatedRequest(
            "$rest/pulse_entries?id=eq.${enc(entryId)}&user_id=eq.${enc(uid)}", token
        ).delete().build()
        return http.execute(request).getOrNull()?.successful == true
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
