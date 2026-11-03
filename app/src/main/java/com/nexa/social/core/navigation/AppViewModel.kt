package com.nexa.social.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.isAccount
import com.nexa.social.domain.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppStartState {
    Onboarding,
    Auth,
    Main
}

class AppViewModel(
    private val preferenceStorage: PreferenceStorage,
    private val authRepository: AuthRepository
) : ViewModel() {

    val startState: StateFlow<AppStartState?> = combine(
        preferenceStorage.onboardingCompleted,
        authRepository.session,
        preferenceStorage.rememberMe
    ) { onboardingCompleted, session, rememberMe ->
        when {
            !onboardingCompleted -> AppStartState.Onboarding
            session.isAccount && !rememberMe -> AppStartState.Auth
            session is SessionState.Guest || session.isAccount -> AppStartState.Main
            else -> AppStartState.Auth
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Honor "remember me": a local account chosen not to be remembered is cleared on start.
        viewModelScope.launch {
            val session = authRepository.session.first()
            if (session is SessionState.LocalAccount && !preferenceStorage.rememberMe.first()) {
                authRepository.signOut()
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                AppViewModel(container.preferenceStorage, container.authRepository)
            }
        }
    }
}
