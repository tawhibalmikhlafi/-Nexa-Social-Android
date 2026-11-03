package com.nexa.social.domain.model

/** Coarse device category derived only from the recorded `user_agent` value. */
enum class SessionDeviceKind {
    ANDROID_APP,
    CHROME,
    BROWSER,
    OTHER,
}

/**
 * A real Supabase Auth session (`auth.sessions`) belonging to the signed-in user.
 *
 * [isCurrent] is only true when the access token's signed `session_id` claim matches [id], so the
 * UI never guesses the current device from list ordering.
 */
data class UserSession(
    val id: String,
    val userId: String?,
    val deviceName: String,
    val deviceKind: SessionDeviceKind,
    val ipAddress: String?,
    val createdAt: Long?,
    val refreshedAt: Long?,
    val lastActiveAt: Long?,
    val expiresAt: Long?,
    val isCurrent: Boolean,
) {
    /** True when `not_after` is set and already in the past. */
    val isExpired: Boolean
        get() = expiresAt != null && expiresAt > 0L && expiresAt <= System.currentTimeMillis()
}
