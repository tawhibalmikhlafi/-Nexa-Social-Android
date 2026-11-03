package com.nexa.social.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.SpaceCategory
import com.nexa.social.domain.repository.SpaceRepository
import kotlinx.coroutines.launch

class CreateSpaceViewModel(
    private val communityId: String,
    private val spaceRepository: SpaceRepository
) : ViewModel() {

    fun create(
        title: String,
        description: String,
        category: SpaceCategory,
        onResult: (FailureReason?, String?) -> Unit
    ) {
        viewModelScope.launch {
            when (val result = spaceRepository.createSpace(communityId, title, description, category)) {
                is Resource.Success -> onResult(null, result.data.id)
                is Resource.Failure -> onResult(result.reason, null)
                Resource.Loading -> Unit
            }
        }
    }

    companion object {
        fun factory(communityId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateSpaceViewModel(communityId, nexaApplication().container.spaceRepository)
            }
        }
    }
}
