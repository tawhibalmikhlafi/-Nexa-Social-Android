package com.nexa.social.data.remote

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.RefreshBus
import com.nexa.social.core.common.Resource
import com.nexa.social.core.common.Validators
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.long
import com.nexa.social.core.supabase.objectOrNull
import com.nexa.social.core.supabase.string
import com.nexa.social.domain.model.AccountOrigin
import com.nexa.social.domain.model.AccountStatus
import com.nexa.social.domain.model.EmailVerificationStatus
import com.nexa.social.domain.model.User
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

private const val TAG = "SupabaseUsers"
private const val PROFILE_POLL_MS = 10_000L
private const val MAX_FOLLOW_PAGE = 500L

/**
 * Supabase/PostgREST implementation of the user directory, private account fields, username
 * reservations and the follow graph.
 *
 * Reads use PostgREST filters protected by RLS; writes never send a user id other than the
 * authenticated one. Live data is refreshed by a short poll because the app talks to PostgREST
 * directly and does not depend on the Realtime websocket.
 */
class SupabaseUserDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val accessToken: () -> String?,
    private val currentUid: () -> String?,
    private val emailProvider: () -> String? = { null }
) : UserRemoteDataSource {

    private val restUrl: String get() = "${config.normalizedUrl}/rest/v1"

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun refresh() {
        refreshSignal.tryEmit(Unit)
    }

    private suspend fun awaitNextPoll() {
        withTimeoutOrNull(PROFILE_POLL_MS) {
            merge(refreshSignal, RefreshBus.requests).first()
        }
    }

    override fun observeProfile(uid: String): Flow<User?> = flow {
        var emitted = false
        while (currentCoroutineContext().isActive) {
            val profile = getProfile(uid, includePrivate = currentUid() == uid)
            if (profile != null || !emitted) {
                emit(profile)
                emitted = true
            }
            awaitNextPoll()
        }
    }

    override fun observeUsers(limit: Long): Flow<List<User>> = flow {
        var emitted = false
        while (currentCoroutineContext().isActive) {
            val users = fetchProfiles("select=*&order=created_at.desc&limit=$limit")
                ?.mapNotNull { SupabaseUserMapper.toUser(it) }
            if (users != null || !emitted) {
                emit(users.orEmpty())
                emitted = true
            }
            awaitNextPoll()
        }
    }

    override suspend fun getProfile(uid: String, includePrivate: Boolean): User? {
        if (uid.isBlank()) return null
        val rows = fetchProfiles("select=*&id=eq.${encode(uid)}&limit=1") ?: return null
        val row = rows.firstOrNull() ?: return null
        val email = if (includePrivate || currentUid() == uid) emailProvider() else null
        return SupabaseUserMapper.toUser(row, email)
    }

    override suspend fun searchUsers(query: String, limit: Long): List<User> {
        val needle = query.trim().removePrefix("@").trim()
        if (needle.isBlank()) return emptyList()
        val viaRpc = searchUsersViaRpc(needle, limit)
        val rows = viaRpc ?: searchUsersViaRest(needle, limit).orEmpty()
        return rows
            .mapNotNull { SupabaseUserMapper.toUser(it) }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<User>(
                    { !it.username.startsWith(needle, ignoreCase = true) },
                    { it.username.lowercase() }
                )
            )
            .take(limit.toInt())
    }

    /** Respects `show_in_search` and private-account rules defined in the SQL function. */
    private suspend fun searchUsersViaRpc(needle: String, limit: Long): List<JsonObject>? {
        val token = accessToken() ?: return null
        val body = JsonObject().apply {
            addProperty("q", needle)
            addProperty("lim", limit)
        }
        val response = execute(
            http.authenticatedRequest("$restUrl/rpc/nexa_search_users", token)
                .header("Prefer", "return=representation")
                .post(http.jsonBody(body))
                .build()
        ) ?: return null
        if (!response.successful) {
            Log.w(TAG, "RPC nexa_search_users failed with HTTP ${response.code}")
            return null
        }
        return parseArray(response.body)
    }

    private suspend fun searchUsersViaRest(needle: String, limit: Long): List<JsonObject>? {
        // Prefix search: the first typed character is enough to query live profiles.
        val pattern = encode("${escapeLike(needle)}*")
        return coroutineScope {
            val byUsername = async {
                fetchProfiles(
                    "select=*&account_status=eq.ACTIVE&username=ilike.$pattern&limit=$limit"
                ).orEmpty()
            }
            val byDisplayName = async {
                fetchProfiles(
                    "select=*&account_status=eq.ACTIVE&display_name=ilike.$pattern&limit=$limit"
                ).orEmpty()
            }
            byUsername.await() + byDisplayName.await()
        }
    }

    override suspend fun createProfile(user: User): Boolean {
        val token = accessToken() ?: return false
        val response = execute(
            http.authenticatedRequest("$restUrl/profiles", token)
                .header("Prefer", "return=minimal")
                .post(http.jsonBody(SupabaseUserMapper.toProfileRow(user)))
                .build()
        ) ?: return false
        if (!response.successful) {
            Log.w(TAG, "createProfile failed with HTTP ${response.code}")
            if (response.code == 409) {
                deleteOwnUsernameReservation(user.id)
            }
            return false
        }
        return true
    }

    override suspend fun updateProfile(
        uid: String,
        displayName: String,
        bio: String,
        avatarUri: String?,
        coverUri: String?,
        city: String?,
        website: String?
    ): Resource<User> {
        val body = JsonObject().apply {
            addProperty("display_name", displayName)
            addProperty("bio", bio)
            addProperty("avatar_url", avatarUri)
            addProperty("cover_url", coverUri)
            addProperty("city", city)
            addProperty("website", website)
            addProperty("updated_at", Instant.now().toString())
        }
        val failure = patchProfile(uid, body)
        if (failure != null) return Resource.Failure(failure)
        val profile = getProfile(uid, includePrivate = true)
            ?: return Resource.Failure(FailureReason.GENERIC)
        return Resource.Success(profile)
    }

    override suspend fun changeUsername(uid: String, username: String): Resource<User> {
        if (currentUid() != uid) return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val canonical = Validators.canonicalUsername(username)
        if (!Validators.isValidUsername(canonical)) return Resource.Failure(FailureReason.USERNAME_INVALID)
        if (Validators.isReservedUsername(canonical)) return Resource.Failure(FailureReason.USERNAME_RESERVED)
        val token = accessToken() ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val body = JsonObject().apply { addProperty("p_username", canonical) }
        val response = execute(
            http.authenticatedRequest("${restUrl}/rpc/nexa_change_username", token)
                .header("Prefer", "return=representation")
                .post(http.jsonBody(body)).build()
        ) ?: return Resource.Failure(FailureReason.NETWORK)
        if (!response.successful) {
            val reason = when {
                response.code == 409 || response.body?.contains("23505") == true -> FailureReason.USERNAME_TAKEN
                response.code == 401 || response.code == 403 -> FailureReason.PERMISSION_DENIED
                response.body?.contains("reserved", ignoreCase = true) == true -> FailureReason.USERNAME_RESERVED
                response.body?.contains("format", ignoreCase = true) == true -> FailureReason.USERNAME_INVALID
                else -> http.failureReason(response)
            }
            Log.w(TAG, "changeUsername failed with HTTP ${response.code}")
            return Resource.Failure(reason)
        }
        val profile = getProfile(uid, includePrivate = true)
            ?: return Resource.Failure(FailureReason.GENERIC)
        refresh()
        return Resource.Success(profile)
    }

    override suspend fun updateBasicProfile(
        uid: String,
        displayName: String,
        bio: String,
        avatarUri: String?
    ): Boolean {
        val body = JsonObject().apply {
            addProperty("display_name", displayName)
            addProperty("bio", bio)
            if (!avatarUri.isNullOrBlank()) addProperty("avatar_url", avatarUri)
            addProperty("updated_at", Instant.now().toString())
        }
        return patchProfile(uid, body) == null
    }

    override suspend fun reserveUsername(uid: String, username: String): Boolean {
        val canonical = Validators.canonicalUsername(username)
        if (canonical.isBlank()) return false
        val token = accessToken() ?: return false
        val body = JsonObject().apply {
            addProperty("username", canonical)
            addProperty("user_id", uid)
        }
        // One reservation per user: a retry replaces the name chosen in the previous attempt.
        val response = execute(
            http.authenticatedRequest("$restUrl/usernames?on_conflict=user_id", token)
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(http.jsonBody(body))
                .build()
        ) ?: return false
        if (!response.successful) {
            Log.w(TAG, "reserveUsername failed with HTTP ${response.code}")
        }
        return response.successful
    }

    override suspend fun isUsernameAvailable(username: String): Boolean? {
        val canonical = Validators.canonicalUsername(username)
        if (canonical.isBlank()) return false
        val token = accessToken()
        val builder = if (token != null) {
            http.authenticatedRequest(
                "$restUrl/usernames?select=user_id&username=eq.${encode(canonical)}&limit=1",
                token
            )
        } else {
            http.request("$restUrl/usernames?select=user_id&username=eq.${encode(canonical)}&limit=1")
        }
        val response = execute(builder.get().build()) ?: return null
        if (!response.successful) {
            Log.w(TAG, "isUsernameAvailable failed with HTTP ${response.code}")
            return null
        }
        return parseArray(response.body)?.isEmpty() ?: false
    }

    override fun observeFollowingIds(uid: String): Flow<Set<String>> =
        observeIds(uid, selectColumn = "following_id", filterColumn = "follower_id")

    override fun observeFollowerIds(uid: String): Flow<Set<String>> =
        observeIds(uid, selectColumn = "follower_id", filterColumn = "following_id")

    override fun observeFollowing(uid: String): Flow<List<User>> = flow {
        var emitted = false
        while (currentCoroutineContext().isActive) {
            val users = followingUsers(uid)
            if (users != null || !emitted) {
                emit(users.orEmpty())
                emitted = true
            }
            awaitNextPoll()
        }
    }

    override fun observeFollowers(uid: String): Flow<List<User>> = flow {
        var emitted = false
        while (currentCoroutineContext().isActive) {
            val users = followerUsers(uid)
            if (users != null || !emitted) {
                emit(users.orEmpty())
                emitted = true
            }
            awaitNextPoll()
        }
    }

    override suspend fun isFollowing(uid: String, targetId: String): Boolean {
        if (uid == targetId) return false
        val rows = fetchRows(
            "follows?select=follower_id&follower_id=eq.${encode(uid)}" +
                "&following_id=eq.${encode(targetId)}&limit=1"
        ) ?: return false
        return rows.isNotEmpty()
    }

    override suspend fun setFollowing(uid: String, targetId: String, following: Boolean): Boolean {
        if (uid == targetId) return false
        val token = accessToken() ?: return false
        val response = if (following) {
            // Public accounts are followed immediately by the RPC; private accounts create
            // a pending request. The database decides which path is valid under RLS.
            val body = JsonObject().apply { addProperty("target_id", targetId) }
            execute(
                http.authenticatedRequest("$restUrl/rpc/nexa_request_follow", token)
                    .header("Prefer", "return=representation")
                    .post(http.jsonBody(body))
                    .build()
            )
        } else {
            // Unfollowing also cancels an outstanding private-account request. The
            // request table intentionally has no client UPDATE permission; cancellation
            // must go through its SECURITY DEFINER RPC.
            val pendingRows = execute(
                http.authenticatedRequest(
                    "$restUrl/follow_requests?select=id&requester_id=eq.${encode(uid)}" +
                        "&target_id=eq.${encode(targetId)}&status=eq.pending&limit=1",
                    token
                ).get().build()
            )?.takeIf { it.successful }?.let { parseArray(it.body) }.orEmpty()
            val pendingId = pendingRows.firstOrNull()?.string("id")
            if (pendingId != null) {
                val cancelBody = JsonObject().apply { addProperty("request_id", pendingId) }
                val cancelled = execute(
                    http.authenticatedRequest("$restUrl/rpc/nexa_cancel_follow_request", token)
                        .post(http.jsonBody(cancelBody)).build()
                ) ?: return false
                if (!cancelled.successful) {
                    Log.w(TAG, "cancel follow request failed with HTTP ${cancelled.code}")
                    return false
                }
            }
            execute(
                http.authenticatedRequest(
                    "$restUrl/follows?follower_id=eq.${encode(uid)}" +
                        "&following_id=eq.${encode(targetId)}",
                    token
                ).delete().build()
            )
        } ?: return false
        if (!response.successful) {
            Log.w(TAG, "setFollowing($following) failed with HTTP ${response.code}")
            return false
        }
        refresh()
        return true
    }

    override suspend fun uniqueUsername(preferred: String): String {
        val base = Validators.canonicalUsername(preferred)
            .filter { it.isLetterOrDigit() || it == '_' }
            .take(Validators.USERNAME_MAX)
            .ifBlank { "nexa_user" }
        val startsWithLetter = base.firstOrNull()?.isLetter() == true
        val candidate = if (startsWithLetter) base else "nexa_$base"
        val candidateAvailable = isUsernameAvailable(candidate)
        if (!Validators.isReservedUsername(candidate) &&
            (candidateAvailable == true || candidateAvailable == null)
        ) {
            return candidate
        }
        for (index in 1..99) {
            val suffix = index.toString()
            val trimmed = candidate.take(Validators.USERNAME_MAX - suffix.length)
            val attempt = "$trimmed$suffix"
            val available = isUsernameAvailable(attempt)
            if (!Validators.isReservedUsername(attempt) &&
                (available == true || available == null)
            ) {
                return attempt
            }
        }
        return "nexa_${System.currentTimeMillis()}"
    }

    private fun observeIds(
        uid: String,
        selectColumn: String,
        filterColumn: String
    ): Flow<Set<String>> = flow {
        var emitted = false
        while (currentCoroutineContext().isActive) {
            val rows = fetchRows(
                "follows?select=$selectColumn&$filterColumn=eq.${encode(uid)}&limit=$MAX_FOLLOW_PAGE"
            )
            if (rows != null || !emitted) {
                emit(rows.orEmpty().mapNotNull { it.string(selectColumn) }.toSet())
                emitted = true
            }
            awaitNextPoll()
        }
    }

    private suspend fun followingUsers(uid: String): List<User>? {
        val ids = fetchRows("follows?select=following_id&follower_id=eq.${encode(uid)}&limit=$MAX_FOLLOW_PAGE")
            ?.mapNotNull { it.string("following_id") }
            ?: return null
        return fetchProfilesByIds(ids)
    }

    private suspend fun followerUsers(uid: String): List<User>? {
        val ids = fetchRows("follows?select=follower_id&following_id=eq.${encode(uid)}&limit=$MAX_FOLLOW_PAGE")
            ?.mapNotNull { it.string("follower_id") }
            ?: return null
        return fetchProfilesByIds(ids)
    }

    private suspend fun fetchProfilesByIds(ids: List<String>): List<User> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(100).flatMap { chunk ->
            val filter = chunk.joinToString(",") { encode(it) }
            fetchProfiles("select=*&id=in.($filter)&limit=${chunk.size}")
                .orEmpty()
                .mapNotNull { SupabaseUserMapper.toUser(it) }
        }
    }

    private suspend fun deleteOwnUsernameReservation(uid: String) {
        val token = accessToken() ?: return
        execute(
            http.authenticatedRequest(
                "$restUrl/usernames?user_id=eq.${encode(uid)}",
                token
            ).delete().build()
        )
    }

    private suspend fun patchProfile(uid: String, body: JsonObject): FailureReason? {
        val token = accessToken() ?: return FailureReason.NOT_AUTHENTICATED
        val response = execute(
            http.authenticatedRequest("$restUrl/profiles?id=eq.${encode(uid)}", token)
                .header("Prefer", "return=minimal")
                .patch(http.jsonBody(body))
                .build()
        ) ?: return FailureReason.NETWORK
        if (!response.successful) {
            Log.w(TAG, "patchProfile failed with HTTP ${response.code}")
            return http.failureReason(response)
        }
        return null
    }

    /** Returns null when the request failed so callers can keep the previous value. */
    private suspend fun fetchProfiles(query: String): List<JsonObject>? =
        fetchRows("profiles?$query")

    private suspend fun fetchRows(path: String): List<JsonObject>? {
        val token = accessToken() ?: return null
        val response = execute(
            http.authenticatedRequest("$restUrl/$path", token).get().build()
        ) ?: return null
        if (!response.successful) {
            Log.w(TAG, "GET $path failed with HTTP ${response.code}")
            return null
        }
        return parseArray(response.body)
    }

    private suspend fun execute(request: okhttp3.Request): SupabaseHttp.HttpResponse? =
        http.execute(request).getOrNull()

    private fun parseArray(body: String): List<JsonObject>? = runCatching {
        val element = JsonParser.parseString(body)
        if (!element.isJsonArray) return@runCatching null
        element.asJsonArray.mapNotNull { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject
        }
    }.onFailure {
        Log.w(TAG, "Malformed PostgREST response", it)
    }.getOrNull()

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/**
 * Pure mapping between the PostgREST `profiles` row and the domain model. Kept free of Android
 * and network dependencies so it can be unit-tested directly.
 */
internal object SupabaseUserMapper {

    fun toUser(row: JsonObject, email: String? = null): User? {
        val id = row.string("id") ?: return null
        val username = row.string("username") ?: return null
        return User(
            id = id,
            username = username,
            displayName = row.string("display_name")?.takeIf { it.isNotBlank() } ?: username,
            email = email,
            emailVerification = enumOrDefault(
                row.string("email_verification"),
                EmailVerificationStatus.NOT_VERIFIED
            ),
            avatarUri = row.string("avatar_url"),
            coverUri = row.string("cover_url"),
            bio = row.string("bio").orEmpty(),
            createdAt = parseMillis(row.string("created_at")),
            accountStatus = enumOrDefault(row.string("account_status"), AccountStatus.ACTIVE),
            accountOrigin = enumOrDefault(
                row.string("account_origin"),
                AccountOrigin.REMOTE_ACCOUNT
            ),
            followersCount = row.long("followers_count")?.toInt() ?: 0,
            followingCount = row.long("following_count")?.toInt() ?: 0,
            city = row.string("city"),
            website = row.string("website")
        )
    }

    fun toProfileRow(user: User): JsonObject = JsonObject().apply {
        addProperty("id", user.id)
        addProperty("username", Validators.canonicalUsername(user.username))
        addProperty("display_name", user.displayName)
        addProperty("bio", user.bio)
        addProperty("avatar_url", user.avatarUri)
        addProperty("cover_url", user.coverUri)
        addProperty("city", user.city)
        addProperty("website", user.website)
        addProperty("created_at", Instant.ofEpochMilli(user.createdAt).toString())
        addProperty("updated_at", Instant.now().toString())
    }

    private fun parseMillis(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrDefault(0L)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
