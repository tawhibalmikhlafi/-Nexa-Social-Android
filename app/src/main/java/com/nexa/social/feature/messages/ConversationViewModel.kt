package com.nexa.social.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Conversation
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.isAccount
import com.nexa.social.domain.repository.MessageRepository
import com.nexa.social.domain.repository.SafetyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val conversationId: String,
    private val messageRepository: MessageRepository,
    private val safetyRepository: SafetyRepository,
    preferences: PreferenceStorage
) : ViewModel() {

    val conversation: StateFlow<Conversation?> = messageRepository.observeConversation(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Only a local account has an identity capable of sending; guests cannot. */
    val canSend: StateFlow<Boolean> = preferences.session
        .map { it.isAccount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val currentUserId: StateFlow<String?> = preferences.session
        .map { (it as? SessionState.RemoteAccount)?.userId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isRemote: StateFlow<Boolean> = preferences.session
        .map { it is SessionState.RemoteAccount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _failure = MutableStateFlow<FailureReason?>(null)
    val failure: StateFlow<FailureReason?> = _failure.asStateFlow()

    private val _attachmentUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val attachmentUrls: StateFlow<Map<String, String>> = _attachmentUrls.asStateFlow()

    private var markedUnread: Set<String> = emptySet()

    init {
        viewModelScope.launch { messageRepository.markConversationRead(conversationId) }
        viewModelScope.launch {
            conversation.collect { conversation ->
                val unread = conversation?.messages.orEmpty()
                    .filter { !it.isRead && !it.isMine }
                val fresh = unread.map { it.id }.toSet() - markedUnread
                if (fresh.isNotEmpty()) {
                    markedUnread = markedUnread + fresh
                    messageRepository.markConversationRead(conversationId)
                }
                val known = _attachmentUrls.value
                val missing = conversation?.messages.orEmpty()
                    .filter { !it.attachmentPath.isNullOrBlank() && it.id !in known }
                if (missing.isEmpty()) return@collect
                val resolved = buildMap {
                    missing.forEach { message ->
                        messageRepository.resolveAttachmentUrl(message)?.let { url ->
                            put(message.id, url)
                        }
                    }
                }
                if (resolved.isNotEmpty()) {
                    _attachmentUrls.value = known + resolved
                }
            }
        }
    }

    fun send(text: String, attachmentUri: String? = null) {
        if (text.isBlank() && attachmentUri.isNullOrBlank()) return
        viewModelScope.launch {
            when (val result = messageRepository.sendMessage(conversationId, text, attachmentUri)) {
                is Resource.Success -> _failure.value = null
                is Resource.Failure -> _failure.value = result.reason
                Resource.Loading -> Unit
            }
        }
    }

    fun edit(messageId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            apply(messageRepository.editMessage(messageId, text))
        }
    }

    fun deleteForMe(messageId: String) {
        viewModelScope.launch {
            apply(messageRepository.deleteMessageForMe(conversationId, messageId))
        }
    }

    fun deleteForEveryone(messageId: String) {
        viewModelScope.launch {
            apply(messageRepository.deleteMessageForEveryone(conversationId, messageId))
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        val mine = conversation.value?.messages
            ?.firstOrNull { it.id == messageId }
            ?.reactionList
            ?.firstOrNull { it.userId == currentUserId.value }
        viewModelScope.launch {
            val result = if (mine?.emoji == emoji) {
                messageRepository.unreactToMessage(messageId)
            } else {
                messageRepository.reactToMessage(messageId, emoji)
            }
            apply(result)
        }
    }

    fun toggleStar(messageId: String, starred: Boolean) {
        viewModelScope.launch {
            apply(messageRepository.setStarred(messageId, starred))
        }
    }

    private fun apply(result: Resource<Unit>) {
        when (result) {
            is Resource.Success -> _failure.value = null
            is Resource.Failure -> _failure.value = result.reason
            Resource.Loading -> Unit
        }
    }

    fun clearFailure() {
        _failure.value = null
    }

    fun blockUser(user: com.nexa.social.domain.model.UserSummary) {
        viewModelScope.launch {
            safetyRepository.block(user)
        }
    }

    companion object {
        fun factory(conversationId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                ConversationViewModel(
                    conversationId = conversationId,
                    messageRepository = container.messageRepository,
                    safetyRepository = container.safetyRepository,
                    preferences = container.preferenceStorage
                )
            }
        }
    }
}
