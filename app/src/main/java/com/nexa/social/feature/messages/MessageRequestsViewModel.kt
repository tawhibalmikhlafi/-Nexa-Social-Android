package com.nexa.social.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Conversation
import com.nexa.social.domain.repository.MessageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MessageRequestsViewModel(
    private val messageRepository: MessageRepository
) : ViewModel() {

    val requests: StateFlow<List<Conversation>> = messageRepository.observeMessageRequests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun acceptRequest(conversationId: String) {
        viewModelScope.launch {
            messageRepository.acceptMessageRequest(conversationId)
        }
    }

    fun deleteRequest(conversationId: String) {
        viewModelScope.launch {
            messageRepository.acceptMessageRequest(conversationId)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                MessageRequestsViewModel(container.messageRepository)
            }
        }
    }
}
