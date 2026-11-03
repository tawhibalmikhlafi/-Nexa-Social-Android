package com.nexa.social.feature.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.BlockedUser
import com.nexa.social.domain.model.HiddenContent
import com.nexa.social.domain.model.MutedUser
import com.nexa.social.domain.model.PrivacySettings
import com.nexa.social.domain.repository.SafetyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SafetyViewModel(
    private val safetyRepository: SafetyRepository
) : ViewModel() {

    val blocked: StateFlow<List<BlockedUser>> = safetyRepository.observeBlockedUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val muted: StateFlow<List<MutedUser>> = safetyRepository.observeMutedUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hidden: StateFlow<List<HiddenContent>> = safetyRepository.observeHiddenContent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val privacy: StateFlow<PrivacySettings> = safetyRepository.observePrivacySettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacySettings())

    fun unblock(userId: String) = viewModelScope.launch { safetyRepository.unblock(userId) }
    fun unmute(userId: String) = viewModelScope.launch { safetyRepository.unmute(userId) }
    fun unhide(contentId: String) = viewModelScope.launch { safetyRepository.unhide(contentId) }

    fun updatePrivacy(transform: (PrivacySettings) -> PrivacySettings) {
        viewModelScope.launch {
            val current = safetyRepository.observePrivacySettings().first()
            safetyRepository.updatePrivacy(transform(current))
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SafetyViewModel(nexaApplication().container.safetyRepository)
            }
        }
    }
}
