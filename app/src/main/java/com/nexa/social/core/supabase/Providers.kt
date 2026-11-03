package com.nexa.social.core.supabase

/** Supplies the current Supabase access token, or null when there is no signed-in session. */
interface AccessTokenProvider {
    suspend fun token(): String?
}

/** Supplies the current authenticated user id, or null when there is no signed-in session. */
interface CurrentUidProvider {
    suspend fun uid(): String?
}
