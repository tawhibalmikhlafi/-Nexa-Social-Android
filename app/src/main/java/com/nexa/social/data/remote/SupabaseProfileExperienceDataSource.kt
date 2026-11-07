package com.nexa.social.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import java.net.URLEncoder

/**
 * PostgREST gateway for the professional profile suite.
 * Every write is bound to the signed-in profile; RLS remains the final authority.
 */
class SupabaseProfileExperienceDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?,
    private val currentUid: () -> String?
) {
    private val rest get() = "${config.normalizedUrl}/rest/v1"

    private val ownerTables = setOf(
        "nexa_profile_timeline",
        "nexa_profile_languages",
        "nexa_profile_credentials",
        "nexa_profile_status",
        "nexa_profile_status_history",
        "nexa_profile_testimonials",
        "nexa_testimonial_requests",
        "nexa_profile_favorites",
        "nexa_profile_achievements",
        "nexa_profile_analytics_events"
    )

    private fun ownerColumn(table: String): String =
        if (table == "nexa_profile_testimonials" || table == "nexa_testimonial_requests" ||
            table == "nexa_profile_analytics_events") "profile_id" else "user_id"

    suspend fun loadRows(table: String, profileId: String): List<JsonObject> {
        if (table !in ownerTables || profileId.isBlank()) return emptyList()
        val token = accessToken() ?: return emptyList()
        val owner = ownerColumn(table)
        val url = "$rest/$table?select=*&$owner=eq.${encode(profileId)}&limit=300"
        val response = http.execute(http.authenticatedRequest(url, token).get().build()).getOrNull()
            ?: return emptyList()
        if (!response.successful) return emptyList()
        return runCatching {
            JsonParser.parseString(response.body).asJsonArray.mapNotNull { item ->
                item.takeIf { it.isJsonObject }?.asJsonObject
            }
        }.getOrDefault(emptyList())
    }

    suspend fun saveRow(table: String, values: JsonObject): Boolean {
        if (table !in ownerTables || table in setOf(
                "nexa_profile_status_history",
                "nexa_profile_analytics_events",
                "nexa_testimonial_requests"
            )
        ) return false
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val owner = ownerColumn(table)
        val body = values.deepCopy().apply {
            addProperty(owner, uid)
            if (table == "nexa_profile_testimonials") {
                // Recommendations entered by the owner are clearly marked as manual.
                add("author_id", null)
                addProperty("is_approved", true)
            }
        }
        val conflict = when (table) {
            "nexa_profile_status" -> "user_id"
            "nexa_profile_achievements" -> "user_id,achievement_code"
            else -> "id"
        }
        val url = "$rest/$table?on_conflict=${encode(conflict)}"
        val response = http.execute(
            http.authenticatedRequest(url, token)
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(http.jsonBody(body))
                .build()
        ).getOrNull()
        return response?.successful == true
    }

    suspend fun deleteRow(table: String, id: String): Boolean {
        if (table !in setOf(
                "nexa_profile_timeline",
                "nexa_profile_languages",
                "nexa_profile_credentials",
                "nexa_profile_status",
                "nexa_profile_testimonials",
                "nexa_profile_favorites"
            ) || id.isBlank()
        ) return false
        val uid = currentUid() ?: return false
        val token = accessToken() ?: return false
        val filter = if (table == "nexa_profile_status") {
            "user_id=eq.${encode(uid)}"
        } else {
            "id=eq.${encode(id)}&${ownerColumn(table)}=eq.${encode(uid)}"
        }
        val response = http.execute(
            http.authenticatedRequest("$rest/$table?$filter", token).delete().build()
        ).getOrNull()
        return response?.successful == true
    }

    suspend fun recordEvent(profileId: String, eventType: String, eventKey: String? = null): Boolean {
        val viewer = currentUid() ?: return false
        if (profileId.isBlank() || profileId == viewer ||
            eventType !in setOf("PROFILE_VIEW", "SKILL_CLICK", "SOCIAL_LINK_CLICK", "CV_DOWNLOAD", "QR_SCAN", "TESTIMONIAL_RECEIVED")
        ) return false
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("profile_id", profileId)
            addProperty("viewer_id", viewer)
            addProperty("event_type", eventType)
            addProperty("event_key", eventKey)
        }
        val response = http.execute(
            http.authenticatedRequest("$rest/nexa_profile_analytics_events", token)
                .header("Prefer", "return=minimal")
                .post(http.jsonBody(body))
                .build()
        ).getOrNull()
        return response?.successful == true
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
