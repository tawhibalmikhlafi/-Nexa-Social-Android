package com.nexa.social.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Space
import com.nexa.social.domain.repository.SpaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpaceDetailViewModel(
    private val spaceId: String,
    private val spaceRepository: SpaceRepository
) : ViewModel() {

    val space: StateFlow<Space?> = spaceRepository.observeSpace(spaceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addResource(title: String, description: String, reference: String?) =
        viewModelScope.launch { spaceRepository.addResource(spaceId, title, description, reference) }

    fun removeResource(resourceId: String) =
        viewModelScope.launch { spaceRepository.removeResource(spaceId, resourceId) }

    fun addChallenge(prompt: String) =
        viewModelScope.launch { spaceRepository.addChallenge(spaceId, prompt) }

    fun toggleChallenge(challengeId: String) =
        viewModelScope.launch { spaceRepository.toggleChallenge(spaceId, challengeId) }

    companion object {
        fun factory(spaceId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SpaceDetailViewModel(spaceId, nexaApplication().container.spaceRepository)
            }
        }
    }
}
