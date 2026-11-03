package com.nexa.social.domain.model

/**
 * Current authentication/session state.
 *
 * [LocalAccount] is a development account stored only on this device. [RemoteAccount] is backed by
 * Supabase Authentication before the app has necessarily loaded the remote profile.
 */
sealed interface SessionState {
    /** Session is still being read from local storage. */
    data object Loading : SessionState

    /** No session: the app should show the welcome/auth flow. */
    data object None : SessionState

    /** Anonymous browsing, no account and no identity on any server. */
    data object Guest : SessionState

    /** A local development account stored on this device only. */
    data class LocalAccount(val userId: String) : SessionState

    /** An account authenticated with Supabase (email/password or Google). */
    data class RemoteAccount(val userId: String) : SessionState
}

/** The account id for any signed-in session, or null for guest/none. */
val SessionState.accountUserId: String?
    get() = when (this) {
        is SessionState.LocalAccount -> userId
        is SessionState.RemoteAccount -> userId
        else -> null
    }

/** True when [this] is either a local or a remote account. */
val SessionState.isAccount: Boolean
    get() = accountUserId != null

/** Credential record kept locally and never exposed through public models. */
data class LocalCredential(
    val userId: String,
    val username: String,
    val passwordHash: String,
    val salt: String
)
