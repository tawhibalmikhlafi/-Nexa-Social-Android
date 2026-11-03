package com.nexa.social.feature.settings.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.PrivacySettings
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.repository.AuthRepository
import com.nexa.social.domain.repository.PrivacyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PrivacyError {
    data class Network(val nonce: Long = System.currentTimeMillis()) : PrivacyError
    data class SessionExpired(val nonce: Long = System.currentTimeMillis()) : PrivacyError
    data class Permission(val nonce: Long = System.currentTimeMillis()) : PrivacyError
    data class Generic(val nonce: Long = System.currentTimeMillis()) : PrivacyError
}

data class PrivacyUiState(
    val isLoading: Boolean = true,
    val settings: PrivacySettings = PrivacySettings.safeDefaults(),
    val savingKeys: Set<String> = emptySet(),
    val error: PrivacyError? = null,
    val successNonce: Long? = null,
    val isRemote: Boolean = false,
)

class PrivacyViewModel(
    private val repository: PrivacyRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state.asStateFlow()

    private val inFlight: MutableSet<String> = mutableSetOf()

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                val remote = session is SessionState.RemoteAccount
                _state.update { it.copy(isRemote = remote) }
                if (remote) reload()
                else _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun retry() = viewModelScope.launch { reload() }

    fun clearTransientMessages() = _state.update {
        it.copy(error = null, successNonce = null)
    }

    fun updateSettings(key: String, transform: (PrivacySettings) -> PrivacySettings) {
        val current = _state.value
        if (!current.isRemote) return
        if (!inFlight.add(key)) return

        val previous = current.settings
        val next = transform(previous)
        if (next == previous) {
            inFlight.remove(key)
            return
        }

        _state.update {
            it.copy(
                settings = next,
                savingKeys = it.savingKeys + key,
                error = null,
                successNonce = null,
            )
        }

        viewModelScope.launch {
            when (val result = repository.save(next)) {
                is Resource.Success -> _state.update {
                    it.copy(
                        savingKeys = it.savingKeys - key,
                        settings = result.data,
                        successNonce = System.currentTimeMillis(),
                    )
                }
                is Resource.Failure -> _state.update {
                    it.copy(
                        savingKeys = it.savingKeys - key,
                        settings = previous,
                        error = result.reason.toPrivacyError(),
                    )
                }
                Resource.Loading -> Unit
            }
            inFlight.remove(key)
        }
    }

    private suspend fun reload() {
        _state.update { it.copy(isLoading = true, error = null) }
        when (val result = repository.load()) {
            is Resource.Success -> _state.update {
                it.copy(isLoading = false, settings = result.data)
            }
            is Resource.Failure -> _state.update {
                it.copy(isLoading = false, error = result.reason.toPrivacyError())
            }
            Resource.Loading -> Unit
        }
    }

    private fun FailureReason.toPrivacyError(): PrivacyError = when (this) {
        FailureReason.NETWORK -> PrivacyError.Network()
        FailureReason.NOT_AUTHENTICATED -> PrivacyError.SessionExpired()
        FailureReason.PERMISSION_DENIED -> PrivacyError.Permission()
        else -> PrivacyError.Generic()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = nexaApplication()
                PrivacyViewModel(
                    repository = app.container.privacyRepository,
                    authRepository = app.container.authRepository,
                )
            }
        }
    }
}
