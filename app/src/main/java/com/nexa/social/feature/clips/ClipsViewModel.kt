package com.nexa.social.feature.clips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Clip
import com.nexa.social.domain.repository.ClipRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClipsViewModel(
    private val clipRepository: ClipRepository
) : ViewModel() {

    val clips: StateFlow<List<Clip>> = clipRepository.observeClips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleLike(clipId: String) = viewModelScope.launch { clipRepository.toggleLike(clipId) }

    fun toggleSave(clipId: String) = viewModelScope.launch { clipRepository.toggleSave(clipId) }

    fun deleteClip(clipId: String) = viewModelScope.launch { clipRepository.deleteClip(clipId) }

    fun addClip(videoUri: String, caption: String, onResult: (FailureReason?) -> Unit) {
        viewModelScope.launch {
            when (val result = clipRepository.addClip(videoUri, caption)) {
                is Resource.Success -> onResult(null)
                is Resource.Failure -> onResult(result.reason)
                Resource.Loading -> Unit
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ClipsViewModel(nexaApplication().container.clipRepository)
            }
        }
    }
}
