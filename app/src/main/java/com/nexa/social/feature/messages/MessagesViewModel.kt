package com.nexa.social.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Conversation
import com.nexa.social.domain.repository.MessageRepository
import com.nexa.social.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MessagesViewModel(
    messageRepository: MessageRepository,
    notificationRepository: NotificationRepository
) : ViewModel() {

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    val conversations: StateFlow<List<Conversation>> =
        combine(messageRepository.observeConversations(), _search) { list, query ->
            if (query.isBlank()) list
            else list.filter {
                it.participant.displayName.contains(query, ignoreCase = true) ||
                    it.participant.username.contains(query, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> =
        notificationRepository.observeNotifications()
            .map { list -> list.count { !it.isRead } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setSearch(value: String) {
        _search.value = value
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = nexaApplication()
                MessagesViewModel(
                    app.container.messageRepository,
                    app.container.notificationRepository
                )
            }
        }
    }
}
