package com.nexa.social.feature.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Story
import com.nexa.social.domain.model.StoryAudienceMember
import com.nexa.social.domain.model.StoryGroup
import com.nexa.social.domain.repository.MessageRepository
import com.nexa.social.domain.repository.StoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StoryAudienceUiState(
    val storyId: String? = null,
    val isLoading: Boolean = false,
    val members: List<StoryAudienceMember> = emptyList(),
    val error: FailureReason? = null
) {
    val viewerCount: Int get() = members.count { it.viewedAt != null }
    val interactionCount: Int get() = members.count { it.hasInteracted }
}

class StoriesViewModel(
    private val storyRepository: StoryRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {

    val groups: StateFlow<List<StoryGroup>> = storyRepository.observeStoryGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _audience = MutableStateFlow(StoryAudienceUiState())
    val audience: StateFlow<StoryAudienceUiState> = _audience.asStateFlow()

    fun markViewed(story: Story) {
        if (story.isMine) return
        viewModelScope.launch { storyRepository.markViewed(story.id) }
    }

    fun loadAudience(storyId: String, force: Boolean = false) {
        val current = _audience.value
        if (!force && current.storyId == storyId &&
            (current.isLoading || current.error == null)
        ) {
            return
        }
        val previous = current.takeIf { it.storyId == storyId }?.members.orEmpty()
        viewModelScope.launch {
            _audience.value = StoryAudienceUiState(
                storyId = storyId,
                isLoading = true,
                members = previous
            )
            when (val result = storyRepository.getStoryAudience(storyId)) {
                is Resource.Success -> {
                    if (_audience.value.storyId == storyId) {
                        _audience.value = StoryAudienceUiState(
                            storyId = storyId,
                            members = result.data
                        )
                    }
                }
                is Resource.Failure -> {
                    if (_audience.value.storyId == storyId) {
                        _audience.value = StoryAudienceUiState(
                            storyId = storyId,
                            members = previous,
                            error = result.reason
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun deleteStory(storyId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            if (storyRepository.deleteStory(storyId)) onDone()
        }
    }

    fun createStory(imageUri: String?, text: String?, onResult: (FailureReason?) -> Unit) {
        viewModelScope.launch {
            when (val result = storyRepository.createStory(imageUri, text)) {
                is Resource.Success -> onResult(null)
                is Resource.Failure -> onResult(result.reason)
                Resource.Loading -> Unit
            }
        }
    }

    fun replyToStory(story: Story, text: String, onResult: (FailureReason?) -> Unit) {
        if (story.isMine) {
            onResult(FailureReason.OPERATION_NOT_ALLOWED)
            return
        }
        viewModelScope.launch {
            val result = messageRepository.sendStoryReply(
                recipientId = story.author.id,
                storyId = story.id,
                storyPreview = story.text.orEmpty(),
                text = text
            )
            when (result) {
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
                StoriesViewModel(container.storyRepository, container.messageRepository)
            }
        }
    }
}
