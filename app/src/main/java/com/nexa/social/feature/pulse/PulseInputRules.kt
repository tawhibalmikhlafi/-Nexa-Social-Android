package com.nexa.social.feature.pulse

/** Shared client-side guards; Supabase RLS and database constraints remain authoritative. */
internal object PulseInputRules {
    fun validEntry(body: String, imageUri: String?, videoUri: String? = null): Boolean {
        val text = body.trim()
        val hasImage = !imageUri.isNullOrBlank()
        val hasVideo = !videoUri.isNullOrBlank()
        return text.length <= 1000 &&
            !(hasImage && hasVideo) &&
            (text.isNotEmpty() || hasImage || hasVideo)
    }

    fun validComment(body: String, imageUri: String? = null, videoUri: String? = null): Boolean {
        val text = body.trim()
        val hasImage = !imageUri.isNullOrBlank()
        val hasVideo = !videoUri.isNullOrBlank()
        return text.length <= 500 &&
            !(hasImage && hasVideo) &&
            (text.isNotEmpty() || hasImage || hasVideo)
    }
}
