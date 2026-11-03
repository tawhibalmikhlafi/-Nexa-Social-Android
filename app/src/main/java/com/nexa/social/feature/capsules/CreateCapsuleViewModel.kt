package com.nexa.social.feature.capsules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.repository.CapsuleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class CreateCapsuleUiState(
    val saving: Boolean = false,
    val failure: FailureReason? = null,
    val created: Boolean = false
)

class CreateCapsuleViewModel(
    private val repository: CapsuleRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCapsuleUiState())
    val state: StateFlow<CreateCapsuleUiState> = _state.asStateFlow()

    fun create(title: String, message: String, days: Int) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, failure = null)
        val unlockAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days.toLong())
        viewModelScope.launch {
            when (val result = repository.createCapsule(title, message, unlockAt)) {
                is Resource.Success -> _state.value = _state.value.copy(
                    saving = false,
                    created = true
                )
                is Resource.Failure -> _state.value = _state.value.copy(
                    saving = false,
                    failure = result.reason
                )
                Resource.Loading -> Unit
            }
        }
    }

    val dayChoices: List<Int> = listOf(7, 30, 90, 365)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateCapsuleViewModel(nexaApplication().container.capsuleRepository)
            }
        }
    }
}
