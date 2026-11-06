package com.nexa.social.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.string
import com.nexa.social.domain.model.Gathering
import com.nexa.social.domain.model.WellbeingPlan
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

/** Authenticated REST access for Nexa's cloud-backed life features. */
class SupabaseLifeDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?,
    private val currentUid: () -> String?
) {
    private val rest get() = config.restUrl

    fun currentUserId(): String? = currentUid()

    suspend fun loadGatherings(): List<Gathering> {
        val token = accessToken() ?: return emptyList()
        val rows = getRows(rest + "/nexa_gatherings?select=*&order=starts_at.asc&limit=200", token)
            ?: return emptyList()
        val ids = rows.mapNotNull { it.string("id") }
        val attendees = if (ids.isEmpty()) emptyMap() else {
            getRows(rest + "/nexa_gathering_attendees?select=gathering_id,user_id&gathering_id=in.(" +
                ids.joinToString(",") { encode(it) } + ")", token)
                ?.groupBy { it.string("gathering_id").orEmpty() }
                ?.mapValues { it.value.mapNotNull { row -> row.string("user_id") } }
                .orEmpty()
        }
        val hostIds = rows.mapNotNull { it.string("host_id") }.distinct()
        val hosts = if (hostIds.isEmpty()) emptyMap() else {
            getRows(rest + "/profiles?select=id,display_name,username&id=in.(" +
                hostIds.joinToString(",") { encode(it) } + ")", token)
                ?.associate { row ->
                    row.string("id").orEmpty() to
                        (row.string("display_name") ?: row.string("username") ?: "مستخدم")
                }.orEmpty()
        }
        val uid = currentUid()
        return rows.mapNotNull { row ->
            val id = row.string("id") ?: return@mapNotNull null
            val host = row.string("host_id") ?: return@mapNotNull null
            val tags = runCatching {
                row.getAsJsonArray("tags").mapNotNull { runCatching { it.asString }.getOrNull() }
            }.getOrDefault(emptyList())
            Gathering(
                id = id, title = row.string("title") ?: return@mapNotNull null,
                description = row.string("description").orEmpty(), hostId = host,
                hostName = hosts[host] ?: "مستخدم", location = row.string("location").orEmpty(),
                isVirtual = valueBoolean(row, "is_virtual", false),
                startsAt = millis(row.string("starts_at")),
                durationMinutes = valueInt(row, "duration_minutes", 60).coerceIn(15, 1440),
                capacity = valueInt(row, "capacity", 0).coerceAtLeast(0),
                attendeeIds = attendees[id].orEmpty(), tags = tags,
                createdAt = millis(row.string("created_at")), isMine = host == uid
            )
        }
    }

    suspend fun saveGathering(value: Gathering): Boolean {
        val uid = currentUid() ?: return false
        if (value.hostId != uid) return false
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("id", value.id); addProperty("host_id", uid)
            addProperty("title", value.title); addProperty("description", value.description)
            addProperty("location", value.location); addProperty("is_virtual", value.isVirtual)
            addProperty("starts_at", Instant.ofEpochMilli(value.startsAt).toString())
            addProperty("duration_minutes", value.durationMinutes); addProperty("capacity", value.capacity)
            add("tags", JsonArray().apply { value.tags.forEach { add(it) } })
        }
        if (!post(rest + "/nexa_gatherings?on_conflict=id", token, body)) return false
        return setAttendance(value.id, value.attendeeIds.contains(uid))
    }

    suspend fun setAttendance(id: String, attending: Boolean): Boolean {
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val url = rest + "/nexa_gathering_attendees?gathering_id=eq." + encode(id) +
            "&user_id=eq." + encode(uid)
        val request = if (attending) {
            val body = JsonObject().apply { addProperty("gathering_id", id); addProperty("user_id", uid) }
            http.authenticatedRequest(rest + "/nexa_gathering_attendees?on_conflict=gathering_id,user_id", token)
                .header("Prefer", "resolution=ignore-duplicates,return=minimal")
                .post(http.jsonBody(body)).build()
        } else http.authenticatedRequest(url, token).delete().build()
        return http.execute(request).getOrNull()?.successful == true
    }

    suspend fun deleteGathering(id: String): Boolean {
        val token = accessToken() ?: return false
        return http.execute(http.authenticatedRequest(rest + "/nexa_gatherings?id=eq." + encode(id), token)
            .delete().build()).getOrNull()?.successful == true
    }

    suspend fun loadCapsules(): List<CloudCapsule> {
        val token = accessToken() ?: return emptyList()
        return getRows(rest + "/nexa_time_capsules?select=id,user_id,title,ciphertext,iv,unlock_at,opened_at,created_at&order=unlock_at.asc&limit=200", token)
            ?.mapNotNull { row ->
                CloudCapsule(
                    id = row.string("id") ?: return@mapNotNull null,
                    userId = row.string("user_id") ?: return@mapNotNull null,
                    title = row.string("title") ?: return@mapNotNull null,
                    cipherText = row.string("ciphertext") ?: return@mapNotNull null,
                    iv = row.string("iv") ?: return@mapNotNull null,
                    unlockAt = millis(row.string("unlock_at")),
                    openedAt = row.string("opened_at")?.let(::millis),
                    createdAt = millis(row.string("created_at"))
                )
            }.orEmpty()
    }

    suspend fun saveCapsule(value: CloudCapsule): Boolean {
        val uid = currentUid() ?: return false
        if (value.userId != uid) return false
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("id", value.id); addProperty("user_id", uid)
            addProperty("title", value.title); addProperty("ciphertext", value.cipherText)
            addProperty("iv", value.iv)
            addProperty("unlock_at", Instant.ofEpochMilli(value.unlockAt).toString())
            if (value.openedAt == null) add("opened_at", null)
            else addProperty("opened_at", Instant.ofEpochMilli(value.openedAt).toString())
        }
        return post(rest + "/nexa_time_capsules?on_conflict=id", token, body)
    }

    suspend fun deleteCapsule(id: String): Boolean {
        val token = accessToken() ?: return false
        return http.execute(http.authenticatedRequest(rest + "/nexa_time_capsules?id=eq." + encode(id), token)
            .delete().build()).getOrNull()?.successful == true
    }

    suspend fun loadWellbeing(): WellbeingPlan? {
        val uid = currentUid() ?: return null
        val token = accessToken() ?: return null
        val row = getRows(rest + "/nexa_wellbeing_plans?select=*&user_id=eq." + encode(uid) + "&limit=1", token)
            ?.firstOrNull() ?: return null
        return WellbeingPlan(
            dailyIntention = row.string("daily_intention").orEmpty(),
            focusMode = valueBoolean(row, "focus_mode", false),
            quietHoursEnabled = valueBoolean(row, "quiet_hours_enabled", true),
            quietStartHour = valueInt(row, "quiet_start_hour", 22).coerceIn(0, 23),
            quietEndHour = valueInt(row, "quiet_end_hour", 7).coerceIn(0, 23),
            dailyGoalMinutes = valueInt(row, "daily_goal_minutes", 45).coerceIn(5, 1440),
            mindfulReminders = valueBoolean(row, "mindful_reminders", true),
            weeklyReflection = valueBoolean(row, "weekly_reflection", true)
        )
    }

    suspend fun saveWellbeing(plan: WellbeingPlan): Boolean {
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("user_id", uid); addProperty("daily_intention", plan.dailyIntention.take(500))
            addProperty("focus_mode", plan.focusMode); addProperty("quiet_hours_enabled", plan.quietHoursEnabled)
            addProperty("quiet_start_hour", plan.quietStartHour.coerceIn(0, 23))
            addProperty("quiet_end_hour", plan.quietEndHour.coerceIn(0, 23))
            addProperty("daily_goal_minutes", plan.dailyGoalMinutes.coerceIn(5, 1440))
            addProperty("mindful_reminders", plan.mindfulReminders)
            addProperty("weekly_reflection", plan.weeklyReflection)
            addProperty("updated_at", Instant.now().toString())
        }
        return post(rest + "/nexa_wellbeing_plans?on_conflict=user_id", token, body)
    }

    private suspend fun getRows(url: String, token: String): List<JsonObject>? {
        val response = http.execute(http.authenticatedRequest(url, token).get().build()).getOrNull()
            ?: return null
        if (!response.successful) return null
        return runCatching {
            JsonParser.parseString(response.body).asJsonArray.mapNotNull {
                it.takeIf { item -> item.isJsonObject }?.asJsonObject
            }
        }.getOrNull()
    }

    private suspend fun post(url: String, token: String, body: JsonObject): Boolean =
        http.execute(http.authenticatedRequest(url, token)
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(http.jsonBody(body)).build()).getOrNull()?.successful == true

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private fun millis(value: String?): Long = if (value.isNullOrBlank()) 0L else runCatching {
        Instant.parse(value).toEpochMilli()
    }.recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(0L)
    private fun valueInt(row: JsonObject, key: String, fallback: Int) =
        runCatching { row.get(key)?.asInt ?: fallback }.getOrDefault(fallback)
    private fun valueBoolean(row: JsonObject, key: String, fallback: Boolean) =
        runCatching { row.get(key)?.asBoolean ?: fallback }.getOrDefault(fallback)
}

data class CloudCapsule(
    val id: String, val userId: String, val title: String, val cipherText: String,
    val iv: String, val unlockAt: Long, val openedAt: Long?, val createdAt: Long
)
