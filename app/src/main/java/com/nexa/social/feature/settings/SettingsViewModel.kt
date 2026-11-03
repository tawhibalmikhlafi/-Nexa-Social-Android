package com.nexa.social.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.LocaleManager
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.PrivacySettings
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferenceStorage: PreferenceStorage,
    private val authRepository: AuthRepository,
    private val appContext: Context
) : ViewModel() {

    val language: StateFlow<String> = preferenceStorage.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocaleManager.SYSTEM)

    val notificationsEnabled: StateFlow<Boolean> = preferenceStorage.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val privacySettings: StateFlow<PrivacySettings> = preferenceStorage.privacySettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacySettings())

    val privateAccount: StateFlow<Boolean> = preferenceStorage.privacySettings
        .map { it.privateAccount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val session: StateFlow<SessionState> = authRepository.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.None)

    fun setLanguage(tag: String) {
        viewModelScope.launch {
            preferenceStorage.setLanguage(tag)
            LocaleManager.persist(appContext, tag)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferenceStorage.setNotificationsEnabled(enabled) }
    }

    fun setPrivateAccount(enabled: Boolean) {
        viewModelScope.launch {
            val current = preferenceStorage.privacySettings.first()
            preferenceStorage.setPrivacySettings(current.copy(privateAccount = enabled))
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = nexaApplication()
                SettingsViewModel(
                    preferenceStorage = app.container.preferenceStorage,
                    authRepository = app.container.authRepository,
                    appContext = app.applicationContext
                )
            }
        }
    }
}
