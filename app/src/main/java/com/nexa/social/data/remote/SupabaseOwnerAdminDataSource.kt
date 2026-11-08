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

data class OwnerSupportRow(val id: String, val userId: String, val subject: String, val message: String, val status: String, val reply: String, val createdAt: String)

data class OwnerContentRow(val id: String, val type: String, val body: String, val authorName: String, val createdAt: String)

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
    private val accessToken: suspend () -> String?,
    private val currentUid: suspend () -> String?
) {
    private val base get() = config.restUrl
    var lastError: String? = null
        private set
    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private suspend fun rpc(name: String, body: JsonObject = JsonObject()): String? {
        val token = accessToken() ?: return null
        val response = http.execute(
            http.authenticatedRequest("$base/rpc/$name", token)
                .post(http.jsonBody(body)).build()
        ).getOrNull()
        if (response == null) {
            lastError = "تعذر الاتصال بخدمة Supabase"
            return null
        }
        if (!response.successful) {
            lastError = "Supabase HTTP ${response.code}: ${response.body.take(500)}"
            return null
        }
        lastError = null
        return response.body
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

    suspend fun getUserDetails(userId: String): JsonObject? {
        val body = JsonObject().apply { addProperty("p_user_id", userId) }
        return rpc("nexa_owner_user_details", body)?.let {
            runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull()
        }
    }

    suspend fun moderateUser(userId: String, action: String, reason: String): Boolean {
        val body = JsonObject().apply {
            addProperty("p_user_id", userId)
            addProperty("p_action", action)
            addProperty("p_reason", reason.trim().ifBlank { "إجراء إداري من لوحة مالك Nexa" })
        }
        val result = rpc("nexa_owner_moderate_user", body) ?: return false
        return runCatching { JsonParser.parseString(result).asBoolean }.getOrDefault(false)
    }

    suspend fun moderationHistory(userId: String): List<JsonObject> {
        val body = JsonObject().apply {
            addProperty("p_user_id", userId)
            addProperty("p_limit", 50)
        }
        val raw = rpc("nexa_owner_list_moderation_log", body) ?: return emptyList()
        return runCatching { JsonParser.parseString(raw).asJsonArray.map { it.asJsonObject } }
            .getOrDefault(emptyList())
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
        ).getOrNull()
        if (response == null) {
            lastError = "تعذر الاتصال بخدمة حذف الحساب"
            return false
        }
        if (!response.successful) {
            lastError = "فشل حذف الحساب (HTTP ${response.code}): ${response.body.take(500)}"
            return false
        }
        lastError = null
        return true
    }

    suspend fun listContent(): List<OwnerContentRow> {
        val body = JsonObject().apply { addProperty("p_type", "ALL"); addProperty("p_limit", 200) }
        val raw = rpc("nexa_owner_list_content", body) ?: return emptyList()
        return runCatching {
            JsonParser.parseString(raw).asJsonArray.map { e ->
                val o = e.asJsonObject
                OwnerContentRow(
                    o.string("id").orEmpty(),
                    o.string("content_type").orEmpty(),
                    o.string("body").orEmpty(),
                    o.string("author_name").orEmpty(),
                    o.string("created_at").orEmpty()
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun deleteContent(type: String, contentId: String, reason: String): Boolean {
        val body = JsonObject().apply {
            addProperty("p_type", type)
            addProperty("p_content_id", contentId)
            addProperty("p_reason", reason.trim().ifBlank { "حذف إداري من لوحة مالك Nexa" })
        }
        val raw = rpc("nexa_owner_delete_content", body) ?: return false
        val result = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return false
        if (result.get("success")?.asBoolean != true) return false

        val token = accessToken() ?: return true
        val media = result.getAsJsonArray("media_paths") ?: return true
        val failed = mutableListOf<String>()
        media.forEach { entry ->
            val item = entry.asJsonObject
            val bucket = item.string("bucket").orEmpty()
            val storagePath = item.string("path").orEmpty()
            if (bucket.isBlank() || storagePath.isBlank() || storagePath == "null") return@forEach
            val payload = JsonObject().apply {
                add("prefixes", com.google.gson.JsonArray().apply { add(storagePath) })
            }
            val response = http.execute(
                http.authenticatedRequest("${config.url.trimEnd('/')}/storage/v1/object/${enc(bucket)}", token)
                    .method("DELETE", http.jsonBody(payload)).build()
            ).getOrNull()
            if (response == null || !response.successful) failed += "$bucket/$storagePath"
        }
        if (failed.isNotEmpty()) lastError = "حُذف المحتوى، لكن تعذر تنظيف بعض الوسائط: ${failed.size}"
        return true
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

    suspend fun listSupport(): List<OwnerSupportRow> {
        val raw=rpc("nexa_owner_list_support") ?: return emptyList()
        return runCatching {
            JsonParser.parseString(raw).asJsonArray.map { e ->
                val o=e.asJsonObject
                OwnerSupportRow(o.string("id").orEmpty(),o.string("user_id").orEmpty(),
                    o.string("subject").orEmpty(),o.string("message").orEmpty(),o.string("status").orEmpty(),
                    o.string("admin_reply").orEmpty(),o.string("created_at").orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    suspend fun replySupport(ticketId: String, reply: String, status: String = "RESOLVED"): Boolean {
        val body=JsonObject().apply {
            addProperty("p_ticket_id",ticketId); addProperty("p_reply",reply); addProperty("p_status",status)
        }
        val result = rpc("nexa_owner_reply_support", body) ?: return false
        return runCatching { JsonParser.parseString(result).asBoolean }.getOrDefault(false)
    }

    suspend fun getSettings(): JsonObject? = rpc("nexa_owner_get_settings")?.let {
        runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull()
    }

    suspend fun setSetting(key: String, enabled: Boolean): Boolean {
        val body=JsonObject().apply {
            addProperty("p_key",key)
            add("p_value",com.google.gson.JsonPrimitive(enabled))
        }
        val settingResult = rpc("nexa_owner_set_setting", body) ?: return false
        val saved = runCatching { JsonParser.parseString(settingResult).asBoolean }.getOrDefault(false)
        if (saved && key == "official_badge_enabled") {
            val token = accessToken() ?: return false
            val uid = currentUid() ?: return false
            val profileBody = JsonObject().apply {
                addProperty("is_verified", enabled)
                addProperty("verified_label", if (enabled) "مؤسس Nexa" else null as String?)
            }
            val response = http.execute(
                http.authenticatedRequest("$base/profiles?id=eq.${enc(uid)}", token)
                    .header("Prefer", "return=minimal")
                    .patch(http.jsonBody(profileBody)).build()
            ).getOrNull() ?: return false
            return response.successful
        }
        return saved
    }

    suspend fun createAnnouncement(title: String, bodyText: String): Boolean {
        val cleanTitle = title.trim()
        val cleanBody = bodyText.trim()
        if (cleanTitle.isEmpty() || cleanTitle.length > 140 || cleanBody.isEmpty() || cleanBody.length > 5000) {
            lastError = "تحقق من الإعلان: العنوان من 1 إلى 140 حرفاً والنص من 1 إلى 5000 حرف."
            return false
        }
        val body = JsonObject().apply {
            addProperty("p_title", cleanTitle)
            addProperty("p_body", cleanBody)
        }
        val result = rpc("nexa_owner_create_announcement", body) ?: return false
        val id = runCatching { JsonParser.parseString(result).asString }.getOrNull()
        if (id.isNullOrBlank()) {
            lastError = "لم تُرجع قاعدة البيانات معرّف الإعلان بعد الحفظ."
            return false
        }
        lastError = null
        return true
    }

    suspend fun setAdsSettings(enabled: Boolean, placement: String, durationSeconds: Int, frequency: Int): Boolean {
        val body = JsonObject().apply {
            addProperty("p_enabled", enabled)
            addProperty("p_placement", placement)
            addProperty("p_duration_seconds", durationSeconds)
            addProperty("p_frequency_per_session", frequency)
        }
        val result = rpc("nexa_owner_set_ads_settings", body) ?: return false
        val saved = runCatching { JsonParser.parseString(result).asBoolean }.getOrDefault(false)
        if (!saved && lastError == null) lastError = "قاعدة البيانات لم تؤكد حفظ إعدادات الإعلانات."
        return saved
    }

    suspend fun createOfficialPost(text: String): Boolean {
        if (text.isBlank()) {
            lastError = "اكتب نص المنشور أولاً"
            return false
        }
        val body = JsonObject().apply { addProperty("p_text", text.trim()) }
        val result = rpc("nexa_owner_create_official_post", body) ?: return false
        return runCatching { JsonParser.parseString(result).asString.isNotBlank() }.getOrDefault(false)
    }

    suspend fun isFounderVerified(userId: String): Boolean {
        val token = accessToken() ?: return false
        val response = http.execute(
            http.authenticatedRequest("$base/profiles?id=eq.${enc(userId)}&select=is_verified&limit=1", token)
                .get().build()
        ).getOrNull()?.takeIf { it.successful } ?: return false
        return runCatching {
            JsonParser.parseString(response.body).asJsonArray.first().asJsonObject
                .get("is_verified")?.asBoolean == true
        }.getOrDefault(false)
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
