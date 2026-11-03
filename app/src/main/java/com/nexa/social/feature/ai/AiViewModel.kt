package com.nexa.social.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.AiRequest
import com.nexa.social.domain.model.AiResult
import com.nexa.social.domain.model.AiTask
import com.nexa.social.domain.repository.AiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Success(val result: AiResult) : AiUiState
    data class Error(val reason: FailureReason) : AiUiState
}

class AiViewModel(
    private val aiRepository: AiRepository
) : ViewModel() {

    val isConfigured: Boolean = aiRepository.isConfigured

    private val _state = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    fun run(task: AiTask, input: String) {
        if (input.isBlank()) {
            _state.value = AiUiState.Error(FailureReason.REQUIRED)
            return
        }
        _state.value = AiUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = aiRepository.run(AiRequest(task = task, input = input))) {
                is Resource.Success -> AiUiState.Success(result.data)
                is Resource.Failure -> AiUiState.Error(result.reason)
                Resource.Loading -> AiUiState.Loading
            }
        }
    }

    fun reset() {
        _state.value = AiUiState.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AiViewModel(nexaApplication().container.aiRepository)
            }
        }
    }
}
