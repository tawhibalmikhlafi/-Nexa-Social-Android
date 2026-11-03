package com.nexa.social.core.supabase

import com.nexa.social.BuildConfig

/**
 * Immutable Supabase project configuration.
 *
 * The URL and the publishable (anon) key are injected through BuildConfig from the git-ignored
 * local.properties file. The service-role key must never be shipped in the application.
 */
data class SupabaseConfig(
    val url: String,
    val anonKey: String
) {
    val normalizedUrl: String = url.trim().trimEnd('/')

    val configured: Boolean
        get() = normalizedUrl.startsWith("https://") && anonKey.isNotBlank()

    val authUrl: String get() = "$normalizedUrl/auth/v1"
    val storageUrl: String get() = "$normalizedUrl/storage/v1"
    val restUrl: String get() = "$normalizedUrl/rest/v1"
    val functionsUrl: String get() = "$normalizedUrl/functions/v1"

    companion object {
        fun fromBuild(): SupabaseConfig = SupabaseConfig(
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        )
    }
}
