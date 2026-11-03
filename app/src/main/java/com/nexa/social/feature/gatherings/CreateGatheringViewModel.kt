package com.nexa.social.feature.gatherings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.repository.GatheringRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateGatheringUiState(
    val saving: Boolean = false,
    val failure: FailureReason? = null,
    val createdId: String? = null
)

class CreateGatheringViewModel(
    private val repository: GatheringRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateGatheringUiState())
    val state: StateFlow<CreateGatheringUiState> = _state.asStateFlow()

    fun create(
        title: String,
        description: String,
        location: String,
        isVirtual: Boolean,
        startsAt: Long,
        durationMinutes: Int,
        capacity: Int,
        tags: List<String>
    ) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, failure = null)
        viewModelScope.launch {
            when (val result = repository.createGathering(
                title = title,
                description = description,
                location = location,
                isVirtual = isVirtual,
                startsAt = startsAt,
                durationMinutes = durationMinutes,
                capacity = capacity,
                tags = tags
            )) {
                is Resource.Success -> _state.value = _state.value.copy(
                    saving = false,
                    createdId = result.data.id
                )
                is Resource.Failure -> _state.value = _state.value.copy(
                    saving = false,
                    failure = result.reason
                )
                Resource.Loading -> Unit
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateGatheringViewModel(nexaApplication().container.gatheringRepository)
            }
        }
    }
}
