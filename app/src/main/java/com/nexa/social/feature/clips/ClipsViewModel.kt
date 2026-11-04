package com.nexa.social.feature.clips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.domain.model.Clip
import com.nexa.social.domain.model.ClipComment
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.repository.ClipRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ClipsViewModel(
    private val clipRepository: ClipRepository,
    private val preferenceStorage: PreferenceStorage
) : ViewModel() {

    val clips: StateFlow<List<Clip>> = clipRepository.observeClips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentUserId: StateFlow<String?> = preferenceStorage.session
        .map { session -> when (session) { is SessionState.RemoteAccount -> session.userId; is SessionState.LocalAccount -> session.userId; else -> null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isRemote: StateFlow<Boolean> = preferenceStorage.session
        .map { it is SessionState.RemoteAccount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val videoAutoplay: StateFlow<Boolean> = preferenceStorage.videoAutoplay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val dataSaver: StateFlow<Boolean> = preferenceStorage.dataSaver
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val reduceMotion: StateFlow<Boolean> = preferenceStorage.reduceMotion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _comments = MutableStateFlow<List<ClipComment>>(emptyList())
    val comments = _comments.asStateFlow()
    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading = _commentsLoading.asStateFlow()
    private val _commentError = MutableStateFlow(false)
    val commentError = _commentError.asStateFlow()

    fun loadComments(clipId: String) = viewModelScope.launch {
        _commentsLoading.value = true
        when (val result = clipRepository.getComments(clipId)) {
            is Resource.Success -> { _comments.value = result.data; _commentError.value = false }
            is Resource.Failure -> _commentError.value = true
            Resource.Loading -> Unit
        }
        _commentsLoading.value = false
    }

    fun addComment(clipId: String, content: String, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        _commentError.value = false
        when (val result = clipRepository.addComment(clipId, content)) {
            is Resource.Success -> {
                _comments.value = _comments.value + result.data
                _commentError.value = false
                onResult(true)
            }
            is Resource.Failure -> {
                _commentError.value = true
                onResult(false)
            }
            Resource.Loading -> Unit
        }
    }

    fun deleteComment(commentId: String) = viewModelScope.launch {
        if (clipRepository.deleteComment(commentId)) _comments.update { list -> list.filterNot { it.id == commentId } }
    }

    fun recordView(clipId: String) = viewModelScope.launch { clipRepository.recordView(clipId) }
    fun toggleLike(clipId: String) = viewModelScope.launch { clipRepository.toggleLike(clipId) }

    fun toggleSave(clipId: String) = viewModelScope.launch { clipRepository.toggleSave(clipId) }

    fun deleteClip(clipId: String, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch { onResult(clipRepository.deleteClip(clipId)) }

    fun updateCaption(clipId: String, caption: String, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(clipRepository.updateCaption(clipId, caption))
    }

    fun addClip(videoUri: String, caption: String, coverUri: String? = null, onResult: (FailureReason?) -> Unit) {
        viewModelScope.launch {
            when (val result = clipRepository.addClip(videoUri, caption, coverUri)) {
                is Resource.Success -> onResult(null)
                is Resource.Failure -> onResult(result.reason)
                Resource.Loading -> Unit
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                ClipsViewModel(container.clipRepository, container.preferenceStorage)
            }
        }
    }
}
