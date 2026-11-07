package com.nexa.social.data.remote

import com.nexa.social.core.common.Resource
import com.nexa.social.domain.model.User
import com.nexa.social.domain.model.FollowRequest
import kotlinx.coroutines.flow.Flow

/**
 * Remote user directory contract.
 *
 * The Supabase/PostgREST implementation lives in [SupabaseUserDataSource]; no account, profile,
 * username or follow operation is backed by Supabase.
 */
interface UserRemoteDataSource {
    fun observeProfile(uid: String): Flow<User?>
    fun observeUsers(limit: Long = 100L): Flow<List<User>>
    suspend fun getProfile(uid: String, includePrivate: Boolean = false): User?

    /** Live prefix search, case-insensitive, by username or display name. */
    suspend fun searchUsers(query: String, limit: Long = 20L): List<User>

    /** Inserts the profile row. Returns false when the insert was rejected (e.g. username taken). */
    suspend fun createProfile(user: User): Boolean

    suspend fun updateProfile(
        uid: String,
        displayName: String,
        bio: String,
        avatarUri: String?,
        coverUri: String?,
        city: String?,
        website: String?,
        placeOfOrigin: String? = null,
        currentResidence: String? = null,
        birthDate: String? = null,
        skills: List<String> = emptyList(),
        hobbies: List<String> = emptyList()
    ): Resource<User>

    suspend fun changeUsername(uid: String, username: String): Resource<User>

    suspend fun updateBasicProfile(
        uid: String,
        displayName: String,
        bio: String,
        avatarUri: String?
    ): Boolean

    suspend fun reserveUsername(uid: String, username: String): Boolean
    suspend fun isUsernameAvailable(username: String): Boolean?

    fun observeFollowingIds(uid: String): Flow<Set<String>>
    fun observeFollowerIds(uid: String): Flow<Set<String>>
    fun observeFollowing(uid: String): Flow<List<User>>
    fun observeFollowers(uid: String): Flow<List<User>>
    suspend fun isFollowing(uid: String, targetId: String): Boolean
    suspend fun setFollowing(uid: String, targetId: String, following: Boolean): Boolean
    suspend fun removeFollower(ownerUid: String, followerId: String): Boolean = false
    suspend fun hasPendingFollowRequest(uid: String, targetId: String): Boolean? = null
    suspend fun loadIncomingFollowRequests(uid: String): List<FollowRequest>? = null
    suspend fun respondToFollowRequest(requestId: String, approve: Boolean): Boolean = false

    /** Builds a unique username for a new account (Google or email sign-up). */
    suspend fun uniqueUsername(preferred: String): String

    fun refresh() {}
}
