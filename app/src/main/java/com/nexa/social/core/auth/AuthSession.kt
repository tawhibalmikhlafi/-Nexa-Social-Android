package com.nexa.social.core.auth

import kotlinx.coroutines.flow.Flow

/**
 * The identity surface shared by repositories, Supabase data sources and storage.
 *
 * The uid is always the Supabase Auth user id, which is also the id used for Supabase rows
 * (through the custom-token bridge) and for Supabase Storage folders.
 */
interface AuthSession {
    val available: Boolean
    val uid: Flow<String?>
    fun currentUid(): String?
}
