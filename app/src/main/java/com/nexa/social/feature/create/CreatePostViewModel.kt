package com.nexa.social.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Audience
import com.nexa.social.domain.model.PostDraft
import com.nexa.social.domain.model.User
import com.nexa.social.domain.repository.AuthRepository
import com.nexa.social.domain.repository.PostRepository
import com.nexa.social.domain.repository.UserRepository
import com.nexa.social.domain.usecase.CreatePostUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CreatePostStatus {
    data object Idle : CreatePostStatus
    data object DraftSaved : CreatePostStatus
    data object Published : CreatePostStatus
    data class Error(val reason: FailureReason) : CreatePostStatus
}

class CreatePostViewModel(
    private val createPost: CreatePostUseCase,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val draft: StateFlow<PostDraft?> = postRepository.observeDraft()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _mentionSuggestions = MutableStateFlow<List<User>>(emptyList())
    val mentionSuggestions: StateFlow<List<User>> = _mentionSuggestions.asStateFlow()

    private var mentionSearchJob: kotlinx.coroutines.Job? = null

    fun searchMentionUsers(query: String) {
        val cleanQuery = query.trim().removePrefix("@")
        mentionSearchJob?.cancel()
        if (cleanQuery.isBlank()) {
            _mentionSuggestions.value = emptyList()
            return
        }
        mentionSearchJob = viewModelScope.launch {
            val results = runCatching { userRepository.searchUsers(cleanQuery) }.getOrDefault(emptyList())
            _mentionSuggestions.value = results.take(5)
        }
    }

    fun clearMentionSuggestions() {
        mentionSearchJob?.cancel()
        _mentionSuggestions.value = emptyList()
    }

    val remoteMode: StateFlow<Boolean> = authRepository.remoteMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val uploadProgress: StateFlow<Int> = postRepository.uploadProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _audience = MutableStateFlow(Audience.PUBLIC_LOCAL)
    val audience: StateFlow<Audience> = _audience.asStateFlow()

    private val _status = MutableStateFlow<CreatePostStatus>(CreatePostStatus.Idle)
    val status: StateFlow<CreatePostStatus> = _status.asStateFlow()

    private val _publishing = MutableStateFlow(false)
    val publishing: StateFlow<Boolean> = _publishing.asStateFlow()

    fun setAudience(value: Audience) {
        _audience.value = value
    }

    fun saveDraft(text: String, imageUris: List<String>, tags: List<String> = emptyList()) {
        viewModelScope.launch {
            if (text.isBlank() && imageUris.isEmpty()) {
                _status.value = CreatePostStatus.Error(FailureReason.REQUIRED)
                return@launch
            }
            postRepository.saveDraft(
                PostDraft(
                    text = text,
                    imageUri = imageUris.firstOrNull(),
                    updatedAt = System.currentTimeMillis(),
                    imageUris = imageUris,
                    tags = tags,
                    audience = _audience.value
                )
            )
            _status.value = CreatePostStatus.DraftSaved
        }
    }

    fun publish(text: String, imageUris: List<String>, tags: List<String> = emptyList()) {
        if (_publishing.value) return
        viewModelScope.launch {
            _publishing.value = true
            _status.value = CreatePostStatus.Idle
            try {
                when (val result = createPost(text, imageUris, tags, _audience.value)) {
                    is Resource.Success -> {
                        postRepository.clearDraft()
                        _status.value = CreatePostStatus.Published
                    }
                    is Resource.Failure -> _status.value = CreatePostStatus.Error(result.reason)
                    Resource.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _status.value = CreatePostStatus.Error(FailureReason.GENERIC)
            } finally {
                _publishing.value = false
            }
        }
    }

    fun consumeStatus() {
        _status.value = CreatePostStatus.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                CreatePostViewModel(
                    container.createPost,
                    container.postRepository,
                    container.userRepository,
                    container.authRepository
                )
            }
        }
    }
}
