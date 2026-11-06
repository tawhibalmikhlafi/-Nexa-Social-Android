package com.nexa.social.feature.pulse

/** Shared client-side guards; Supabase RLS and database constraints remain authoritative. */
internal object PulseInputRules {
    fun validEntry(body: String, imageUri: String?, videoUri: String? = null): Boolean =
        body.trim().length <= 1000 &&
            !( !imageUri.isNullOrBlank() && !videoUri.isNullOrBlank()) &&
            (body.trim().isNotEmpty() || !imageUri.isNullOrBlank() || !videoUri.isNullOrBlank())

    fun validComment(body: String): Boolean =
        body.trim().isNotEmpty() && body.trim().length <= 500
}
