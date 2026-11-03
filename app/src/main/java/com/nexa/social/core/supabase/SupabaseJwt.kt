package com.nexa.social.core.supabase

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Reads the signed claims shipped inside a Supabase access token. Never logs the token. */
object SupabaseJwt {

    /**
     * Returns the `session_id` claim of a Supabase access token, or null when the token is
     * malformed or the project does not issue that claim.
     */
    fun sessionId(accessToken: String): String? {
        val payload = accessToken.split('.').getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
            val json = String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8)
            JsonParser.parseString(json)
                .asJsonObject
                .get("session_id")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
