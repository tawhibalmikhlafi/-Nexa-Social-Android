package com.nexa.social.data.repository

import android.content.Context
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.common.Validators
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.data.local.AccountLocalDataSource
import com.nexa.social.data.remote.SupabaseAuthDataSource
import com.nexa.social.data.remote.SupabaseSignUpOutcome
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.SignUpRequest
import com.nexa.social.domain.model.User
import com.nexa.social.domain.model.accountUserId
import com.nexa.social.domain.repository.AuthRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Supabase-only authentication. Local development credentials are not accepted. */
class AuthRepositoryImpl(
    private val prefs: PreferenceStorage,
    private val accounts: AccountLocalDataSource,
    private val userRepository: UserRepository,
    private val supabase: SupabaseAuthDataSource? = null
) : AuthRepository {

    override val session: Flow<SessionState> = prefs.session

    override val currentUser: Flow<User?> = userRepository.currentUser

    override val remoteAuthEnabled: Boolean get() = supabase?.available == true

    override val googleSignInConfigured: Boolean get() = false

    @Volatile
    private var pendingVerification = false

    override val emailVerificationPending: Boolean get() = pendingVerification

    override val remoteMode: Flow<Boolean> =
        prefs.session.map { it is SessionState.RemoteAccount }

    override suspend fun signUp(request: SignUpRequest): Resource<User> {
        if (Validators.displayNameError(request.displayName)) {
            return Resource.Failure(FailureReason.DISPLAY_NAME_REQUIRED)
        }
        if (!Validators.isValidUsername(request.username)) {
            return Resource.Failure(FailureReason.USERNAME_INVALID)
        }
        if (Validators.isReservedUsername(request.username)) {
            return Resource.Failure(FailureReason.USERNAME_RESERVED)
        }
        if (!Validators.isValidEmail(request.email)) {
            return Resource.Failure(FailureReason.EMAIL_INVALID)
        }
        if (!Validators.isValidPassword(request.password)) {
            return Resource.Failure(FailureReason.PASSWORD_SHORT)
        }
        if (request.password != request.confirmPassword) {
            return Resource.Failure(FailureReason.PASSWORD_MISMATCH)
        }
        if (!request.acceptedTerms) {
            return Resource.Failure(FailureReason.TERMS_REQUIRED)
        }
        val remote = supabase?.takeIf { it.available }
            ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        return when (val result = remote.signUpWithEmail(request)) {
            is Resource.Success -> when (val outcome = result.data) {
                is SupabaseSignUpOutcome.SignedIn -> {
                    pendingVerification = false
                    prefs.clearPendingVerificationEmail()
                    prefs.setRememberMe(true)
                    prefs.setSession(SessionState.RemoteAccount(outcome.user.id))
                    Resource.Success(outcome.user)
                }
                is SupabaseSignUpOutcome.VerificationRequired -> {
                    pendingVerification = true
                    prefs.setPendingVerificationEmail(request.email.trim())
                    Resource.Success(outcome.user)
                }
            }
            is Resource.Failure -> result
            Resource.Loading -> Resource.Loading
        }
    }

    override suspend fun signIn(username: String, password: String, remember: Boolean): Resource<User> {
        val email = username.trim()
        if (!Validators.isValidEmail(email)) {
            return Resource.Failure(FailureReason.EMAIL_INVALID)
        }
        val remote = supabase?.takeIf { it.available }
            ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        return remote.signInWithEmail(email, password).also { result ->
            if (result is Resource.Success) {
                pendingVerification = false
                prefs.clearPendingVerificationEmail()
                prefs.setRememberMe(remember)
                prefs.setSession(SessionState.RemoteAccount(result.data.id))
            }
        }
    }

    override suspend fun signInWithGoogle(context: Context): Resource<User> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    override suspend fun enterGuestMode() {
        // Guest/local identities are disabled; return to the authenticated entry screen.
        prefs.setSession(SessionState.None)
    }

    override suspend fun signOut() {
        supabase?.signOut()
        pendingVerification = false
        prefs.clearPendingVerificationEmail()
        prefs.setSession(SessionState.None)
    }

    override suspend fun signOutEverywhere(): Resource<Unit> {
        val remote = supabase?.takeIf { it.available }
        val result = remote?.signOutEverywhere() ?: Resource.Success(Unit)
        if (result is Resource.Failure) return result
        pendingVerification = false
        prefs.clearPendingVerificationEmail()
        prefs.setSession(SessionState.None)
        return Resource.Success(Unit)
    }

    override suspend fun requestPasswordReset(email: String): Resource<Unit> {
        if (!Validators.isValidEmail(email)) return Resource.Failure(FailureReason.EMAIL_INVALID)
        val remote = supabase?.takeIf { it.available }
            ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        return remote.requestPasswordReset(email)
    }

    override suspend fun sendEmailVerification(): Resource<Unit> {
        val remote = supabase?.takeIf { it.available } ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        val pendingEmail = prefs.pendingVerificationEmail.first()
        return remote.sendEmailVerification(pendingEmail)
    }

    override suspend fun completeAuthCallback(
        accessToken: String,
        refreshToken: String
    ): Resource<User> {
        val remote = supabase?.takeIf { it.available }
            ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        return when (val result = remote.handleAuthCallback(accessToken, refreshToken)) {
            is Resource.Success -> {
                pendingVerification = false
                prefs.clearPendingVerificationEmail()
                prefs.setRememberMe(true)
                prefs.setSession(SessionState.RemoteAccount(result.data.id))
                result
            }
            is Resource.Failure -> result
            Resource.Loading -> Resource.Loading
        }
    }

    override suspend fun updatePassword(password: String): Resource<Unit> {
        val remote = supabase?.takeIf { it.available }
            ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        return remote.updatePassword(password)
    }

    override suspend fun changePasswordWithVerification(
        currentPassword: String,
        newPassword: String
    ): Resource<Unit> {
        if (currentPassword.isBlank()) {
            return Resource.Failure(FailureReason.INVALID_CREDENTIALS)
        }
        if (!Validators.isValidPassword(newPassword)) {
            return Resource.Failure(FailureReason.PASSWORD_SHORT)
        }
        val session = prefs.session.first()
        if (session !is SessionState.RemoteAccount) {
            return Resource.Failure(FailureReason.REQUIRES_ACCOUNT)
        }
        val remote = supabase?.takeIf { it.available }
            ?: return Resource.Failure(FailureReason.NOT_CONFIGURED)
        val email = remote.currentEmail()
            ?: userRepository.getUser(session.userId)?.email?.takeIf { it.isNotBlank() }
            ?: return Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        return when (val verified = remote.verifyPassword(email, currentPassword)) {
            is Resource.Failure -> verified
            is Resource.Success -> remote.updatePassword(newPassword)
            Resource.Loading -> Resource.Loading
        }
    }

    override suspend fun completeProfileSetup(displayName: String, bio: String, avatarUri: String?) {
        if (prefs.session.first() is SessionState.RemoteAccount) {
            supabase?.updateBasicProfile(displayName.trim(), bio.trim(), avatarUri)
        }
    }

    /**
     * Restores the Supabase session (refreshing the access token when needed) and keeps the
     * stored session aligned with token expiry and remote sign-out. Transient network failures
     * are retried in the background.
     */
    override fun startSessionSync(scope: CoroutineScope) {
        scope.launch {
            val previousSession = prefs.session.first()
            if (previousSession is SessionState.LocalAccount || previousSession is SessionState.Guest) {
                prefs.setSession(SessionState.None)
                prefs.setRememberMe(false)
            }
            val remote = supabase?.takeIf { it.available } ?: return@launch
            if (!prefs.rememberMe.first()) {
                remote.signOut()
                if (prefs.sessionBackend.first() == PreferenceStorage.SessionBackend.SUPABASE) {
                    prefs.setSession(SessionState.None)
                }
                return@launch
            }
            var attempt = 0
            while (isActive) {
                when (val restored = remote.restoreSession()) {
                    is Resource.Failure -> {
                        if (restored.reason == FailureReason.NETWORK) {
                            if (attempt < 5) {
                                attempt += 1
                                delay(15_000L * attempt)
                                continue
                            }
                            // Stay signed in locally while offline; the next launch retries.
                            break
                        }
                        if (prefs.sessionBackend.first() == PreferenceStorage.SessionBackend.SUPABASE) {
                            prefs.setSession(SessionState.None)
                        }
                        break
                    }
                    is Resource.Success -> {
                        val user = restored.data
                        if (user != null) {
                            if (prefs.session.first().accountUserId != user.id) {
                                prefs.setSession(SessionState.RemoteAccount(user.id))
                            }
                        } else if (prefs.sessionBackend.first() ==
                            PreferenceStorage.SessionBackend.SUPABASE &&
                            remote.currentUid() == null
                        ) {
                            prefs.setSession(SessionState.None)
                        }
                        break
                    }
                    Resource.Loading -> break
                }
            }

            remote.uid.catch { }.collect { uid ->
                if (prefs.sessionBackend.first() != PreferenceStorage.SessionBackend.SUPABASE) {
                    return@collect
                }
                if (uid == null) {
                    delay(1_500)
                    if (remote.currentUid() == null &&
                        prefs.sessionBackend.first() == PreferenceStorage.SessionBackend.SUPABASE
                    ) {
                        prefs.setSession(SessionState.None)
                    }
                } else {
                    val current = prefs.session.first()
                    if (current.accountUserId != uid) {
                        prefs.setSession(SessionState.RemoteAccount(uid))
                    }
                }
            }
        }
    }
}
