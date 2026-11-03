package com.nexa.social.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Message
import com.nexa.social.domain.repository.MessageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StarredMessagesViewModel(
    private val messageRepository: MessageRepository
) : ViewModel() {

    val starredMessages: StateFlow<List<Message>> = messageRepository.observeStarredMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unstar(messageId: String) {
        viewModelScope.launch {
            messageRepository.setStarred(messageId, false)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                StarredMessagesViewModel(container.messageRepository)
            }
        }
    }
}
