package com.nexa.social.data.remote

import com.google.gson.JsonObject
import com.nexa.social.core.auth.AuthSession
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Ids
import com.nexa.social.core.common.Resource
import com.nexa.social.core.common.Validators
import com.nexa.social.core.supabase.StoredSupabaseSession
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.SupabaseTokenStore
import com.nexa.social.core.supabase.long
import com.nexa.social.core.supabase.objectOrNull
import com.nexa.social.core.supabase.string
import com.nexa.social.data.media.MediaBucket
import com.nexa.social.data.media.MediaStorage
import com.nexa.social.domain.model.AccountOrigin
import com.nexa.social.domain.model.AccountStatus
import com.nexa.social.domain.model.EmailVerificationStatus
import com.nexa.social.domain.model.SignUpRequest
import com.nexa.social.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Result of a sign-up: either a usable session or an account waiting for email confirmation. */
sealed interface SupabaseSignUpOutcome {
    data class SignedIn(val user: User) : SupabaseSignUpOutcome
    data class VerificationRequired(val user: User) : SupabaseSignUpOutcome
}

/**
 * Supabase Auth (GoTrue) over its documented REST API.
 *
 * Supabase is the only authentication and data source. The Supabase user id is the identity used
 * by PostgREST row-level security and by every repository.
 */
class SupabaseAuthDataSource(
    private val config: SupabaseConfig,
    private val http: SupabaseHttp,
    private val tokens: SupabaseTokenStore,
    private val users: UserRemoteDataSource?,
    private val storage: MediaStorage?
) : AuthSession {

    /** True when the project URL/key are present. */
    val configured: Boolean = config.configured

    /** Authentication is available whenever Supabase is configured. */
    override val available: Boolean = config.configured && users != null

    private val _uid = MutableStateFlow<String?>(null)
    override val uid: Flow<String?> = _uid.asStateFlow()

    private val restoreMutex = Mutex()

    override fun currentUid(): String? = _uid.value

    suspend fun currentAccessToken(): String? = tokens.currentSession()?.accessToken

    suspend fun restoreSession(): Resource<User?> = restoreMutex.withLock {
        val stored = tokens.read() ?: run {
            _uid.value = null
            return@withLock Resource.Success(null)
        }
        val current = when (val refreshed = ensureFreshSession(stored)) {
            is Resource.Failure -> return@withLock refreshed
            is Resource.Success -> refreshed.data ?: run {
                _uid.value = null
                return@withLock Resource.Success(null)
            }
            Resource.Loading -> return@withLock Resource.Loading
        }
        _uid.value = current.userId
        val remoteUser = fetchUser(current.accessToken).getOrNull()
        val user = if (remoteUser != null) ensureProfile(remoteUser) else null
        Resource.Success(user)
    }

    suspend fun signUpWithEmail(request: SignUpRequest): Resource<SupabaseSignUpOutcome> {
        val registrationCheck = http.execute(
            http.request("${config.restUrl}/rpc/nexa_signup_allowed")
                .post(http.emptyJsonBody)
                .build()
        ).getOrElse { error -> return Resource.Failure(http.transportFailure(error)) }
        if (!registrationCheck.successful) return Resource.Failure(http.failureReason(registrationCheck))
        val registrationAllowed = runCatching {
            com.google.gson.JsonParser.parseString(registrationCheck.body).asBoolean
        }.getOrDefault(true)
        if (!registrationAllowed) return Resource.Failure(FailureReason.OPERATION_NOT_ALLOWED)
        val username = request.username.trim()
        // A failed availability probe must not be reported as a network error: Supabase still
        // enforces uniqueness when the profile row is inserted right after sign-up.
        if (users?.isUsernameAvailable(username) == false) {
            return Resource.Failure(FailureReason.USERNAME_TAKEN)
        }

        val payload = JsonObject().apply {
            addProperty("email", request.email.trim())
            addProperty("password", request.password)
            addProperty("redirect_to", AUTH_REDIRECT_URL)
            add("data", JsonObject().apply {
                addProperty("display_name", request.displayName.trim())
                addProperty("username", username)
                addProperty("bio", "")
            })
        }
        val response = http.execute(
            http.request("${config.authUrl}/signup")
                .post(http.jsonBody(payload))
                .build()
        ).getOrElse { error -> return Resource.Failure(http.transportFailure(error)) }
        if (!response.successful) return Resource.Failure(http.failureReason(response))
        val json = response.jsonOrNull() ?: return Resource.Failure(FailureReason.GENERIC)

        val session = parseSession(json)
        if (session == null) {
            val remote = parseRemoteUser(json.objectOrNull("user") ?: json)
                ?: return Resource.Failure(FailureReason.GENERIC)
            return Resource.Success(
                SupabaseSignUpOutcome.VerificationRequired(
                    remote.toDomain(preferredUsername = username, verified = false)
                )
            )
        }

        if (!tokens.save(session)) return Resource.Failure(FailureReason.GENERIC)
        _uid.value = session.userId
        val remote = json.objectOrNull("user")?.let { parseRemoteUser(it) }
            ?: fetchUser(session.accessToken).getOrNull()
        val user = remote?.let { ensureProfile(it, preferredUsername = username) }
            ?: return Resource.Failure(FailureReason.GENERIC)
        return Resource.Success(SupabaseSignUpOutcome.SignedIn(user))
    }

    suspend fun signInWithEmail(email: String, password: String): Resource<User> {
        val payload = JsonObject().apply {
            addProperty("email", email.trim())
            addProperty("password", password)
        }
        val response = http.execute(
            http.request("${config.authUrl}/token?grant_type=password")
                .post(http.jsonBody(payload))
                .build()
        ).getOrElse { error -> return Resource.Failure(http.transportFailure(error)) }
        if (!response.successful) return Resource.Failure(http.failureReason(response))
        val json = response.jsonOrNull() ?: return Resource.Failure(FailureReason.GENERIC)
        val session = parseSession(json) ?: return Resource.Failure(FailureReason.GENERIC)
        if (!tokens.save(session)) return Resource.Failure(FailureReason.GENERIC)
        _uid.value = session.userId
        val remote = json.objectOrNull("user")?.let { parseRemoteUser(it) }
            ?: fetchUser(session.accessToken).getOrNull()
            ?: return Resource.Failure(FailureReason.GENERIC)
        val user = ensureProfile(remote) ?: return Resource.Failure(FailureReason.GENERIC)
        val activeUser = reactivateIfNeeded(user, session.accessToken)
            ?: return Resource.Failure(FailureReason.GENERIC)
        // Login alerts are best-effort: never fail authentication if notification storage is offline.
        runCatching {
            http.execute(
                http.authenticatedRequest("${config.restUrl}/rpc/nexa_record_login_notification", session.accessToken)
                    .post(http.emptyJsonBody).build()
            )
        }
        return Resource.Success(activeUser)
    }

    suspend fun requestPasswordReset(email: String): Resource<Unit> {
        val payload = JsonObject().apply { addProperty("email", email.trim()) }
        val response = http.execute(
            http.request("${config.authUrl}/recover?redirect_to=${redirectParam()}")
                .post(http.jsonBody(payload))
                .build()
        ).getOrElse { error -> return Resource.Failure(http.transportFailure(error)) }
        return if (response.successful) {
            Resource.Success(Unit)
        } else {
            Resource.Failure(http.failureReason(response))
        }
    }

    /** Email stored with the active Supabase session, when the provider exposed one. */
    fun currentEmail(): String? = tokens.currentSession()?.email?.takeIf { it.isNotBlank() }

    /**
     * Re-authenticates with the password grant to prove the caller knows the current password,
     * then immediately revokes the temporary session so it does not linger on the account.
     */
    suspend fun verifyPassword(email: String, password: String): Resource<Unit> {
        if (email.isBlank() || password.isBlank()) {
            return Resource.Failure(FailureReason.INVALID_CREDENTIALS)
        }
        val payload = JsonObject().apply {
            addProperty("email", email.trim())
            addProperty("password", password)
        }
        val response = http.execute(
            http.request("${config.authUrl}/token?grant_type=password")
                .post(http.jsonBody(payload))
                .build()
        ).getOrElse { error -> return Resource.Failure(http.transportFailure(error)) }
        if (!response.successful) return Resource.Failure(http.failureReason(response))
        val temporaryToken = response.jsonOrNull()
            ?.get("access_token")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
        if (!temporaryToken.isNullOrBlank()) {
            http.execute(
                http.request("${config.authUrl}/logout?scope=local")
                    .header("Authorization", "Bearer $temporaryToken")
                    .post(http.emptyJsonBody)
                    .build()
            )
        }
        return Resource.Success(Unit)
    }

    suspend fun updatePassword(password: String): Resource<Unit> {
        if (!Validators.isValidPassword(password)) {
            return Resource.Failure(FailureReason.PASSWORD_SHORT)
        }
        val session = tokens.currentSession()
            ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val payload = JsonObject().apply { addProperty("password", password) }
        val response = http.execute(
            http.authenticatedRequest("${config.authUrl}/user", session.accessToken)
                .put(http.jsonBody(payload))
                .build()
        ).getOrElse { error -> return Resource.Failure(http.transportFailure(error)) }
        return if (response.successful) {
            Resource.Success(Unit)
        } else {
            Resource.Failure(http.failureReason(response))
        }
    }

    suspend fun handleAuthCallback(accessToken: String, refreshToken: String): Resource<User> {
        if (accessToken.isBlank() || refreshToken.isBlank()) {
            return Resource.Failure(FailureReason.INVALID_CODE)
        }
        val payload = JsonObject().apply { addProperty("refresh_token", refreshToken) }
        val response = http.execute(
            http.request("${config.authUrl}/token?grant_type=refresh_token")
                .post(http.jsonBody(payload))
                .build()
        ).getOrElse { error -> return Resource.Failure(http.transportFailure(error)) }
        if (!response.successful) {
            val reason = http.failureReason(response)
            return Resource.Failure(
                if (reason == FailureReason.NETWORK) reason else FailureReason.INVALID_CODE
            )
        }
        val json = response.jsonOrNull() ?: return Resource.Failure(FailureReason.INVALID_CODE)
        val session = parseSession(json) ?: StoredSupabaseSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = System.currentTimeMillis() +
                (json.long("expires_in") ?: 3_600L) * 1_000L,
            userId = json.objectOrNull("user")?.string("id").orEmpty(),
            email = json.objectOrNull("user")?.string("email")
        )
        if (session.userId.isBlank()) return Resource.Failure(FailureReason.INVALID_CODE)

        // The server, not the local profile row, is the source of truth for email confirmation.
        val remote = json.objectOrNull("user")?.let { parseRemoteUser(it) }
            ?: fetchUser(session.accessToken).getOrNull()
            ?: return Resource.Failure(FailureReason.INVALID_CODE)
        if (!remote.emailVerified) {
            return Resource.Failure(FailureReason.EMAIL_NOT_VERIFIED)
        }
        if (!tokens.save(session)) return Resource.Failure(FailureReason.GENERIC)
        _uid.value = session.userId
        val user = ensureProfile(remote) ?: return Resource.Failure(FailureReason.GENERIC)
        val activeUser = reactivateIfNeeded(user, session.accessToken)
            ?: return Resource.Failure(FailureReason.GENERIC)
        return Resource.Success(activeUser)
    }

    suspend fun sendEmailVerification(fallbackEmail: String?): Resource<Unit> {
        val email = tokens.currentSession()?.email?.takeIf { it.isNotBlank() }
            ?: fallbackEmail?.trim()?.takeIf { it.isNotBlank() }
            ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val payload = JsonObject().apply {
            addProperty("type", "signup")
            addProperty("email", email)
            addProperty("redirect_to", AUTH_REDIRECT_URL)
        }
        val response = http.execute(
            http.request("${config.authUrl}/resend?redirect_to=${redirectParam()}")
                .post(http.jsonBody(payload))
                .build()
        ).getOrElse { error -> return Resource.Failure(http.transportFailure(error)) }
        return if (response.successful) {
            Resource.Success(Unit)
        } else {
            Resource.Failure(http.failureReason(response))
        }
    }

    suspend fun updateBasicProfile(
        displayName: String,
        bio: String,
        avatarUri: String?
    ): Resource<Unit> {
        val session = tokens.currentSession() ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        var avatarUrl: String? = avatarUri
        if (!avatarUri.isNullOrBlank() && !avatarUri.startsWith("http")) {
            val storageStore = storage ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
            val path = "${session.userId}/avatar-${Ids.newId()}"
            when (val upload = storageStore.uploadImage(avatarUri, MediaBucket.PROFILES, path)) {
                is Resource.Success -> avatarUrl = upload.data
                is Resource.Failure -> return upload
                Resource.Loading -> Unit
            }
        }
        val data = JsonObject().apply {
            addProperty("display_name", displayName.trim())
            addProperty("bio", bio.trim())
            if (!avatarUrl.isNullOrBlank()) addProperty("avatar_url", avatarUrl)
        }
        val payload = JsonObject().apply { add("data", data) }
        val response = http.execute(
            http.authenticatedRequest("${config.authUrl}/user", session.accessToken)
                .put(http.jsonBody(payload))
                .build()
        ).getOrElse { error -> return Resource.Failure(http.transportFailure(error)) }
        if (!response.successful) return Resource.Failure(http.failureReason(response))
        runCatching {
            users?.updateBasicProfile(session.userId, displayName.trim(), bio.trim(), avatarUrl)
        }
        return Resource.Success(Unit)
    }

    suspend fun signOut() {
        val session = tokens.currentSession()
        if (session != null) {
            val request = http.authenticatedRequest("${config.authUrl}/logout", session.accessToken)
                .post(http.emptyJsonBody)
                .build()
            http.execute(request)
        }
        tokens.clear()
        _uid.value = null
    }

    /**
     * Revokes every refresh token for this account, then clears the local session.
     * Supabase only accepts `scope=global` on the logout endpoint.
     */
    suspend fun signOutEverywhere(): Resource<Unit> {
        val session = tokens.currentSession()
            ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val request = http
            .authenticatedRequest("${config.authUrl}/logout?scope=global", session.accessToken)
            .post(http.emptyJsonBody)
            .build()
        val response = http.execute(request).getOrElse { error ->
            return Resource.Failure(http.transportFailure(error))
        }
        if (!response.successful) {
            return Resource.Failure(http.failureReason(response))
        }
        tokens.clear()
        _uid.value = null
        return Resource.Success(Unit)
    }

    private suspend fun ensureFreshSession(
        stored: StoredSupabaseSession
    ): Resource<StoredSupabaseSession?> {
        if (stored.expiresAtMillis - System.currentTimeMillis() > REFRESH_SKEW_MILLIS) {
            return Resource.Success(stored)
        }
        val payload = JsonObject().apply {
            addProperty("refresh_token", stored.refreshToken)
        }
        val response = http.execute(
            http.request("${config.authUrl}/token?grant_type=refresh_token")
                .post(http.jsonBody(payload))
                .build()
        ).getOrElse { error -> return Resource.Failure(http.transportFailure(error)) }
        if (!response.successful) {
            val reason = http.failureReason(response)
            if (reason != FailureReason.NETWORK) {
                tokens.clear()
            }
            return Resource.Failure(reason)
        }
        val json = response.jsonOrNull() ?: return Resource.Failure(FailureReason.GENERIC)
        val refreshed = parseSession(json) ?: return Resource.Failure(FailureReason.GENERIC)
        if (!tokens.save(refreshed)) return Resource.Failure(FailureReason.GENERIC)
        return Resource.Success(refreshed)
    }



    private suspend fun fetchUser(accessToken: String): Result<RemoteUser?> {
        val response = http.execute(
            http.authenticatedRequest("${config.authUrl}/user", accessToken).get().build()
        ).getOrElse { return Result.failure(it) }
        if (!response.successful) {
            return Result.failure(IllegalStateException("auth user ${response.code}"))
        }
        val remote = response.jsonOrNull()?.let { parseRemoteUser(it) }
        return Result.success(remote)
    }

    private suspend fun ensureProfile(
        remote: RemoteUser,
        preferredUsername: String? = null
    ): User? {
        val store = users ?: return remote.toDomain(preferredUsername, remote.emailVerified)
        store.getProfile(remote.id)?.let { existing ->
            return existing
        }
        val preferred = preferredUsername
            ?: remote.username
            ?: remote.email?.substringBefore('@')
            ?: "nexa_user"
        var username = remote.username?.takeIf { Validators.isValidUsername(it) }
            ?: preferred.takeIf { Validators.isValidUsername(it) }
                ?.takeIf { store.isUsernameAvailable(it) != false }
            ?: store.uniqueUsername(preferred)
        repeat(MAX_PROFILE_ATTEMPTS) {
            if (store.reserveUsername(remote.id, username)) {
                val user = remote.toDomain(username, remote.emailVerified)
                if (store.createProfile(user)) return user
            }
            username = store.uniqueUsername(preferred)
        }
        return null
    }

    private suspend fun reactivateIfNeeded(user: User, accessToken: String): User? {
        if (user.accountStatus != AccountStatus.DEACTIVATED) return user
        val body = JsonObject().apply { addProperty("account_status", "ACTIVE") }
        val response = http.execute(
            http.authenticatedRequest(
                "${config.restUrl}/profiles?id=eq.${URLEncoder.encode(user.id, StandardCharsets.UTF_8.name())}",
                accessToken
            )
                .header("Prefer", "return=minimal")
                .patch(http.jsonBody(body))
                .build()
        ).getOrElse { return null }
        if (!response.successful) return null
        return user.copy(accountStatus = AccountStatus.ACTIVE)
    }

    private fun parseSession(json: JsonObject): StoredSupabaseSession? {
        val accessToken = json.string("access_token") ?: return null
        val refreshToken = json.string("refresh_token") ?: return null
        val user = json.objectOrNull("user") ?: return null
        val userId = user.string("id") ?: return null
        val expiresAt = json.long("expires_at")?.times(1_000L)
            ?: (System.currentTimeMillis() + (json.long("expires_in") ?: 3_600L) * 1_000L)
        return StoredSupabaseSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiresAt,
            userId = userId,
            email = user.string("email")
        )
    }

    private fun parseRemoteUser(json: JsonObject): RemoteUser? {
        val id = json.string("id") ?: return null
        val metadata = json.objectOrNull("user_metadata")
        return RemoteUser(
            id = id,
            email = json.string("email"),
            emailVerified = !json.string("email_confirmed_at").isNullOrBlank() ||
                !json.string("confirmed_at").isNullOrBlank(),
            displayName = metadata?.string("display_name") ?: metadata?.string("full_name"),
            username = metadata?.string("username"),
            bio = metadata?.string("bio"),
            avatarUrl = metadata?.string("avatar_url")
        )
    }

    private data class RemoteUser(
        val id: String,
        val email: String?,
        val emailVerified: Boolean,
        val displayName: String?,
        val username: String?,
        val bio: String?,
        val avatarUrl: String?
    ) {
        fun toDomain(preferredUsername: String?, verified: Boolean): User {
            val finalUsername = preferredUsername
                ?: username
                ?: email?.substringBefore('@')
                ?: "nexa_user"
            return User(
                id = id,
                username = finalUsername,
                displayName = displayName?.takeIf { it.isNotBlank() } ?: finalUsername,
                email = email,
                emailVerification = if (verified) {
                    EmailVerificationStatus.VERIFIED
                } else {
                    EmailVerificationStatus.NOT_VERIFIED
                },
                avatarUri = avatarUrl,
                coverUri = null,
                bio = bio.orEmpty(),
                createdAt = System.currentTimeMillis(),
                accountStatus = AccountStatus.ACTIVE,
                accountOrigin = AccountOrigin.REMOTE_ACCOUNT,
                followersCount = 0,
                followingCount = 0
            )
        }
    }

    private fun redirectParam(): String =
        URLEncoder.encode(AUTH_REDIRECT_URL, StandardCharsets.UTF_8.name())

    private companion object {
        const val REFRESH_SKEW_MILLIS = 60_000L
        const val MAX_PROFILE_ATTEMPTS = 3

        /** Must match the intent-filter in AndroidManifest.xml and the Supabase redirect list. */
        const val AUTH_REDIRECT_URL = "nexasocial://auth/callback"
    }
}
