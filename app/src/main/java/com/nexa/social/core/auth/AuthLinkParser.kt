package com.nexa.social.core.auth

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Values carried by a Supabase auth redirect back into the app. */
data class AuthLinkParams(
    val accessToken: String?,
    val refreshToken: String?,
    val type: String?,
    val error: String?,
    val errorDescription: String?
) {
    val hasSessionTokens: Boolean
        get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    val isRecovery: Boolean
        get() = type == "recovery"
}

/**
 * Parses `nexasocial://auth/callback` links produced after Supabase verifies an email address or
 * accepts a password-recovery request. Supabase puts the session tokens in the URI fragment; other
 * providers use the query string, so both are accepted.
 */
object AuthLinkParser {

    const val SCHEME = "nexasocial"
    const val HOST = "auth"
    const val PATH = "/callback"

    fun parse(url: String?): AuthLinkParams? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
        if (!HOST.equals(uri.host, ignoreCase = true)) return null
        if (uri.path.orEmpty().trimEnd('/') != PATH) return null

        val values = parsePairs(uri.rawQuery) + parsePairs(uri.rawFragment)
        return AuthLinkParams(
            accessToken = values["access_token"],
            refreshToken = values["refresh_token"],
            type = values["type"],
            error = values["error"] ?: values["error_code"],
            errorDescription = values["error_description"]
        )
    }

    private fun parsePairs(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&')
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val name = decode(part.substring(0, separator))
                val value = decode(part.substring(separator + 1))
                name to value
            }
            .toMap()
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
}
