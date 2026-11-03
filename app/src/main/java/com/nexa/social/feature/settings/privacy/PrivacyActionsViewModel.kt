package com.nexa.social.feature.settings.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.UserSession
import com.nexa.social.domain.repository.AccountRepository
import com.nexa.social.domain.repository.AuthRepository
import com.nexa.social.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrivacyActionsState(
    val busy: Boolean = false,
    val error: FailureReason? = null,
    val passwordUpdated: Boolean = false,
    val sessionsRevoked: Boolean = false,
    val accountDeleted: Boolean = false,
    val exportPayload: String? = null,
    val sessions: List<UserSession> = emptyList(),
    val loadingSessions: Boolean = false,
    val revokingSessionId: String? = null,
    val sessionsLoaded: Boolean = false,
)

class PrivacyActionsViewModel(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacyActionsState())
    val state: StateFlow<PrivacyActionsState> = _state.asStateFlow()

    fun changePassword(currentPassword: String, newPassword: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, passwordUpdated = false) }
        viewModelScope.launch {
            val result = authRepository.changePasswordWithVerification(
                currentPassword = currentPassword,
                newPassword = newPassword,
            )
            _state.update {
                it.copy(
                    busy = false,
                    error = (result as? Resource.Failure)?.reason,
                    passwordUpdated = result is Resource.Success,
                )
            }
        }
    }

    fun loadSessions() {
        if (_state.value.loadingSessions) return
        _state.update { it.copy(loadingSessions = true, error = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.getActiveSessions()) {
                is Resource.Success -> _state.update {
                    it.copy(
                        loadingSessions = false,
                        sessions = result.data,
                        sessionsLoaded = true,
                    )
                }
                is Resource.Failure -> _state.update {
                    it.copy(
                        loadingSessions = false,
                        error = result.reason,
                        sessionsLoaded = true,
                    )
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun revokeSession(sessionId: String) {
        if (_state.value.revokingSessionId != null) return
        _state.update { it.copy(revokingSessionId = sessionId, error = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.revokeSession(sessionId)) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(
                            revokingSessionId = null,
                            sessions = it.sessions.filterNot { session -> session.id == sessionId },
                        )
                    }
                    loadSessions()
                }
                is Resource.Failure -> _state.update {
                    it.copy(revokingSessionId = null, error = result.reason)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun revokeOtherSessions() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, sessionsRevoked = false) }
        viewModelScope.launch {
            when (val result = sessionRepository.revokeOtherSessions()) {
                is Resource.Success -> {
                    _state.update { it.copy(busy = false, sessionsRevoked = true) }
                    loadSessions()
                }
                is Resource.Failure -> _state.update {
                    it.copy(busy = false, error = result.reason)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun exportData() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = accountRepository.exportData()) {
                is Resource.Success -> _state.update {
                    it.copy(busy = false, exportPayload = result.data)
                }
                is Resource.Failure -> _state.update {
                    it.copy(busy = false, error = result.reason)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun consumeExport() = _state.update { it.copy(exportPayload = null) }

    fun deleteAccount() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, accountDeleted = false) }
        viewModelScope.launch {
            when (val result = accountRepository.deleteAccount()) {
                is Resource.Success -> {
                    authRepository.signOut()
                    _state.update { it.copy(busy = false, accountDeleted = true) }
                }
                is Resource.Failure -> _state.update {
                    it.copy(busy = false, error = result.reason)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun consumePasswordUpdated() = _state.update { it.copy(passwordUpdated = false) }

    fun consumeSessionsRevoked() = _state.update { it.copy(sessionsRevoked = false) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = nexaApplication()
                PrivacyActionsViewModel(
                    authRepository = app.container.authRepository,
                    accountRepository = app.container.accountRepository,
                    sessionRepository = app.container.sessionRepository,
                )
            }
        }
    }
}
