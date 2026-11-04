package com.nexa.social.feature.pulse

/** Shared client-side guards; Supabase RLS and database constraints remain authoritative. */
internal object PulseInputRules {
    fun validEntry(body: String, imageUri: String?): Boolean =
        body.trim().length <= 1000 && (body.trim().isNotEmpty() || !imageUri.isNullOrBlank())

    fun validComment(body: String): Boolean =
        body.trim().isNotEmpty() && body.trim().length <= 500
}
