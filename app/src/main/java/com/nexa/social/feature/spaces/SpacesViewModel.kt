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

class SpacesViewModel(
    communityId: String,
    spaceRepository: SpaceRepository
) : ViewModel() {

    val spaces: StateFlow<List<Space>> = spaceRepository.observeSpaces(communityId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(communityId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SpacesViewModel(communityId, nexaApplication().container.spaceRepository)
            }
        }
    }
}
