package com.nexa.social.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.NexaNotification
import com.nexa.social.domain.model.NotificationType
import com.nexa.social.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    val notifications: StateFlow<List<NexaNotification>> = notificationRepository.observeNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val typeEnabled: StateFlow<Map<NotificationType, Boolean>> = notificationRepository.observeTypeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val isRemote: StateFlow<Boolean> = notificationRepository.observeIsRemote()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun markRead(id: String) {
        viewModelScope.launch { notificationRepository.markRead(id) }
    }

    fun markAllRead() {
        viewModelScope.launch { notificationRepository.markAllRead() }
    }

    fun delete(id: String) {
        viewModelScope.launch { notificationRepository.delete(id) }
    }

    fun clearAll() {
        viewModelScope.launch { notificationRepository.clearAll() }
    }

    fun setTypeEnabled(type: NotificationType, enabled: Boolean) {
        viewModelScope.launch { notificationRepository.setTypeEnabled(type, enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NotificationsViewModel(nexaApplication().container.notificationRepository)
            }
        }
    }
}
