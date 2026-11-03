package com.nexa.social.core.supabase

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.FailureReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Small HTTP layer for Supabase Auth, Storage and Edge Functions.
 *
 * Requests run on the IO dispatcher and never throw. Callers receive a [Result] containing either
 * the HTTP response or the transport error, so a network failure cannot reach the UI as an
 * uncaught exception.
 */
class SupabaseHttp(val config: SupabaseConfig) {

    data class HttpResponse(val code: Int, val body: String) {
        val successful: Boolean get() = code in 200..299

        fun jsonOrNull(): JsonObject? = runCatching {
            JsonParser.parseString(body).asJsonObject
        }.getOrNull()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun execute(request: Request): Result<HttpResponse> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(request).execute().use { response ->
                HttpResponse(
                    code = response.code,
                    body = response.body?.string().orEmpty()
                )
            }
        }
    }

    fun request(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("apikey", config.anonKey)
        .header("Accept", "application/json")

    fun authenticatedRequest(url: String, accessToken: String): Request.Builder =
        request(url).header("Authorization", "Bearer $accessToken")

    fun jsonBody(json: JsonObject): RequestBody = json.toString().toRequestBody(jsonMediaType)

    val emptyJsonBody: RequestBody get() = "{}".toRequestBody(jsonMediaType)

    fun transportFailure(error: Throwable): FailureReason =
        if (error is IOException) FailureReason.NETWORK else FailureReason.GENERIC

    /**
     * Maps the Supabase/GoTrue error payload to the local failure vocabulary. Supabase returns
     * stable `error_code` values; the human readable `msg` is only used as a fallback because its
     * wording can change between versions.
     */
    fun failureReason(response: HttpResponse): FailureReason {
        val json = response.jsonOrNull()
        val code = json?.string("error_code")
            ?: json?.string("error")
            ?: json?.string("code")
        val message = (
            json?.string("msg")
                ?: json?.string("error_description")
                ?: json?.string("message")
                ?: response.body
            ).lowercase()

        return when {
            code == "invalid_credentials" ||
                code == "invalid_grant" ||
                message.contains("invalid login credentials") ||
                message.contains("invalid_grant") -> FailureReason.INVALID_CREDENTIALS

            code == "email_exists" ||
                code == "user_already_exists" ||
                message.contains("already registered") ||
                message.contains("already been registered") -> FailureReason.EMAIL_IN_USE

            code == "weak_password" ||
                message.contains("password should be") ||
                message.contains("weak password") -> FailureReason.WEAK_PASSWORD

            code == "email_not_confirmed" ||
                message.contains("email not confirmed") -> FailureReason.EMAIL_NOT_VERIFIED

            code == "over_email_send_rate_limit" ||
                code == "over_request_rate_limit" ||
                response.code == 429 ||
                message.contains("rate limit") -> FailureReason.TOO_MANY_REQUESTS

            code == "user_not_found" -> FailureReason.INVALID_CREDENTIALS

            code == "user_banned" ||
                message.contains("banned") -> FailureReason.ACCOUNT_DISABLED

            code == "signup_disabled" ||
                code == "email_provider_disabled" -> FailureReason.OPERATION_NOT_ALLOWED

            response.code == 413 ||
                message.contains("payload too large") ||
                message.contains("file too large") ||
                message.contains("maximum allowed size") -> FailureReason.FILE_TOO_LARGE

            message.contains("mime type") ||
                message.contains("invalid file type") -> FailureReason.UNSUPPORTED_FILE

            message.contains("bucket not found") -> FailureReason.NOT_CONFIGURED

            response.code == 401 -> FailureReason.NOT_AUTHENTICATED
            response.code == 403 -> FailureReason.PERMISSION_DENIED
            response.code == 404 -> FailureReason.NOT_CONFIGURED
            response.code >= 500 -> FailureReason.NETWORK
            else -> FailureReason.GENERIC
        }
    }

}

internal fun JsonObject.string(name: String): String? = runCatching {
    get(name)?.takeIf { !it.isJsonNull }?.asString
}.getOrNull()

internal fun JsonObject.boolean(name: String): Boolean? = runCatching {
    get(name)?.takeIf { !it.isJsonNull }?.asBoolean
}.getOrNull()

internal fun JsonObject.objectOrNull(name: String): JsonObject? = runCatching {
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject
}.getOrNull()

internal fun JsonObject.long(name: String): Long? = runCatching {
    get(name)?.takeIf { !it.isJsonNull }?.asLong
}.getOrNull()
