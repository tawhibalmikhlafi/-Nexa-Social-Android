package com.nexa.social.domain.repository

import com.nexa.social.core.common.Resource

/**
 * Account-level actions that are not part of the auth/session flow: exporting the signed-in
 * user's own data and permanently deleting the account.
 */
interface AccountRepository {
    /** Serializes every row owned by the current account into a single JSON document. */
    suspend fun exportData(): Resource<String>

    /** Temporarily hides the profile; the next successful sign-in reactivates it. */
    suspend fun deactivateAccount(): Resource<Unit>

    /** Deletes the Supabase auth user; profiles and owned rows cascade away. */
    suspend fun deleteAccount(): Resource<Unit>
}
