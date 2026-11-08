package com.nexa.social.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.string
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class OwnerUserRow(
    val id: String,
    val email: String,
    val username: String,
    val displayName: String,
    val createdAt: String,
    val status: String,
    val verified: Boolean
)

data class OwnerReportRow(
    val id: String,
    val targetType: String,
    val targetId: String,
    val reason: String,
    val note: String,
    val status: String,
    val createdAt: String
)

class SupabaseOwnerAdminDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?
) {
    private val base get() = config.restUrl
    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private suspend fun rpc(name: String, body: JsonObject = JsonObject()): String? {
        val token = accessToken() ?: return null
        val response = http.execute(
            http.authenticatedRequest("$base/rpc/$name", token)
                .post(http.jsonBody(body)).build()
        ).getOrNull() ?: return null
        return response.takeIf { it.successful }?.body
    }

    suspend fun isOwner(): Boolean = rpc("nexa_is_owner")?.let {
        runCatching { JsonParser.parseString(it).asBoolean }.getOrDefault(false)
    } ?: false

    suspend fun dashboardStats(): JsonObject? = rpc("nexa_owner_dashboard_stats")?.let {
        runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull()
    }

    suspend fun listUsers(search: String = ""): List<OwnerUserRow> {
        val body = JsonObject().apply {
            addProperty("p_search", search.trim().ifBlank { null })
            addProperty("p_limit", 100)
            addProperty("p_offset", 0)
        }
        val raw = rpc("nexa_owner_list_users", body) ?: return emptyList()
        return runCatching {
            JsonParser.parseString(raw).asJsonArray.map { e ->
                val o = e.asJsonObject
                OwnerUserRow(
                    o.string("user_id").orEmpty(), o.string("email").orEmpty(),
                    o.string("username").orEmpty(), o.string("display_name").orEmpty(),
                    o.string("created_at").orEmpty(), o.string("account_status").orEmpty(),
                    o.get("is_verified")?.asBoolean ?: false
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun setUserStatus(userId: String, status: String): Boolean {
        val body = JsonObject().apply {
            addProperty("p_user_id", userId)
            addProperty("p_status", status)
            addProperty("p_reason", "إجراء إداري من لوحة مالك Nexa")
        }
        return rpc("nexa_owner_set_user_status", body) != null
    }

    suspend fun deleteUser(userId: String): Boolean {
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("user_id", userId)
            addProperty("confirmation", "DELETE_ACCOUNT")
        }
        val response = http.execute(
            http.authenticatedRequest("${config.url.trimEnd('/')}/functions/v1/admin-delete-user", token)
                .post(http.jsonBody(body)).build()
        ).getOrNull() ?: return false
        return response.successful
    }

    suspend fun listReports(): List<OwnerReportRow> {
        val token = accessToken() ?: return emptyList()
        val response = http.execute(
            http.authenticatedRequest(
                "$base/content_reports?select=id,target_type,target_id,reason,note,status,created_at&order=created_at.desc&limit=100",
                token
            ).get().build()
        ).getOrNull()?.takeIf { it.successful } ?: return emptyList()
        return runCatching {
            JsonParser.parseString(response.body).asJsonArray.map { e ->
                val o=e.asJsonObject
                OwnerReportRow(o.string("id").orEmpty(),o.string("target_type").orEmpty(),
                    o.string("target_id").orEmpty(),o.string("reason").orEmpty(),
                    o.string("note").orEmpty(),o.string("status").orEmpty(),o.string("created_at").orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    suspend fun updateReport(reportId: String, status: String): Boolean {
        val token = accessToken() ?: return false
        val body=JsonObject().apply { addProperty("status",status) }
        val response=http.execute(
            http.authenticatedRequest("$base/content_reports?id=eq.${enc(reportId)}",token)
                .header("Prefer","return=minimal").patch(http.jsonBody(body)).build()
        ).getOrNull() ?: return false
        return response.successful
    }

    suspend fun getSettings(): JsonObject? = rpc("nexa_owner_get_settings")?.let {
        runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull()
    }

    suspend fun setSetting(key: String, enabled: Boolean): Boolean {
        val body=JsonObject().apply {
            addProperty("p_key",key)
            add("p_value",com.google.gson.JsonPrimitive(enabled))
        }
        return rpc("nexa_owner_set_setting",body) != null
    }

    suspend fun createAnnouncement(title: String, bodyText: String): Boolean {
        val body=JsonObject().apply { addProperty("p_title",title); addProperty("p_body",bodyText) }
        return rpc("nexa_owner_create_announcement",body) != null
    }

    suspend fun createOfficialPost(text: String): Boolean {
        val token=accessToken() ?: return false
        val uid=currentUid() ?: return false
        val body=JsonObject().apply {
            addProperty("author_id",uid); addProperty("text",text.trim()); addProperty("audience","PUBLIC_LOCAL")
        }
        val response=http.execute(http.authenticatedRequest("$base/posts",token)
            .header("Prefer","return=minimal").post(http.jsonBody(body)).build()).getOrNull() ?: return false
        return response.successful
    }

    suspend fun enableFounderBadge(): Boolean {
        val token=accessToken() ?: return false
        val uid=currentUid() ?: return false
        val body=JsonObject().apply { addProperty("is_verified",true); addProperty("verified_label","مؤسس Nexa") }
        val response=http.execute(http.authenticatedRequest("$base/profiles?id=eq.${enc(uid)}",token)
            .header("Prefer","return=minimal").patch(http.jsonBody(body)).build()).getOrNull() ?: return false
        return response.successful
    }
}
