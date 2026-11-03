package com.nexa.social.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.SignUpRequest
import com.nexa.social.domain.model.User
import com.nexa.social.domain.repository.AuthRepository
import com.nexa.social.domain.usecase.ValidateUsernameUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

data class AuthUiState(
    val loading: Boolean = false,
    val failure: FailureReason? = null,
    val authenticated: Boolean = false,
    val guest: Boolean = false,
    val resetRequested: Boolean = false,
    val verificationCode: String? = null,
    val verifiedLocally: Boolean = false,
    val verificationSent: Boolean = false,
    val profileCompleted: Boolean = false,
    val googleAvailable: Boolean = false,
    val awaitingEmailVerification: Boolean = false,
    val passwordResetDone: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val validateUsername: ValidateUsernameUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthUiState(googleAvailable = authRepository.googleSignInConfigured)
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val remoteAuthEnabled: Boolean = authRepository.remoteAuthEnabled

    val currentUser: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun signUp(request: SignUpRequest) {
        _state.value = _state.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            when (val result = authRepository.signUp(request)) {
                is Resource.Success -> {
                    val awaitingVerification = authRepository.emailVerificationPending
                    _state.value = _state.value.copy(
                        loading = false,
                        authenticated = !awaitingVerification,
                        verificationSent = awaitingVerification,
                        awaitingEmailVerification = awaitingVerification
                    )
                }
                is Resource.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    failure = result.reason
                )
                Resource.Loading -> Unit
            }
        }
    }

    fun signIn(username: String, password: String, remember: Boolean = true) {
        _state.value = _state.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            when (val result = authRepository.signIn(username, password, remember)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loading = false,
                    authenticated = true
                )
                is Resource.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    failure = result.reason
                )
                Resource.Loading -> Unit
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        _state.value = _state.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(context)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loading = false,
                    authenticated = true
                )
                is Resource.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    failure = result.reason
                )
                Resource.Loading -> Unit
            }
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            authRepository.enterGuestMode()
            _state.value = _state.value.copy(guest = true)
        }
    }

    fun requestPasswordReset(email: String) {
        _state.value = _state.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            when (val result = authRepository.requestPasswordReset(email)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loading = false,
                    resetRequested = true
                )
                is Resource.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    failure = result.reason
                )
                Resource.Loading -> Unit
            }
        }
    }

    /** Generates a local development code. It is not sent anywhere. */
    fun generateVerificationCode() {
        val code = Random.nextInt(100000, 999999).toString()
        _state.value = _state.value.copy(
            verificationCode = code,
            verifiedLocally = false,
            failure = null
        )
    }

    fun verifyCode(input: String) {
        val expected = _state.value.verificationCode
        if (expected != null && input.trim() == expected) {
            _state.value = _state.value.copy(verifiedLocally = true, failure = null)
        } else {
            _state.value = _state.value.copy(failure = FailureReason.INVALID_CODE)
        }
    }

    fun sendVerificationEmail() {
        _state.value = _state.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            when (val result = authRepository.sendEmailVerification()) {
                is Resource.Success -> _state.value = _state.value.copy(
                    loading = false,
                    verificationSent = true
                )
                is Resource.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    failure = result.reason
                )
                Resource.Loading -> Unit
            }
        }
    }

    fun resetPassword(password: String, confirmation: String) {
        if (password != confirmation) {
            _state.value = _state.value.copy(failure = FailureReason.PASSWORD_MISMATCH)
            return
        }
        _state.value = _state.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            when (val result = authRepository.updatePassword(password)) {
                is Resource.Success -> {
                    // The recovery token already authenticated the user; force a fresh sign-in
                    // with the new password.
                    authRepository.signOut()
                    _state.value = _state.value.copy(
                        loading = false,
                        passwordResetDone = true
                    )
                }
                is Resource.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    failure = result.reason
                )
                Resource.Loading -> Unit
            }
        }
    }

    fun saveProfile(displayName: String, bio: String, avatarUri: String?) {
        _state.value = _state.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            authRepository.completeProfileSetup(displayName, bio, avatarUri)
            _state.value = _state.value.copy(loading = false, profileCompleted = true)
        }
    }

    fun checkUsernameAvailability(raw: String, onResult: (FailureReason?) -> Unit) {
        viewModelScope.launch { onResult(validateUsername(raw)) }
    }

    fun clearFailure() {
        _state.value = _state.value.copy(failure = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = nexaApplication()
                AuthViewModel(app.container.authRepository, app.container.validateUsername)
            }
        }
    }
}
