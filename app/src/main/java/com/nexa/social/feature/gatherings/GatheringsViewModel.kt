package com.nexa.social.feature.gatherings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Gathering
import com.nexa.social.domain.model.accountUserId
import com.nexa.social.domain.repository.AuthRepository
import com.nexa.social.domain.repository.GatheringRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GatheringsViewModel(
    private val repository: GatheringRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val gatherings: StateFlow<List<Gathering>> = repository.observeGatherings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userId: StateFlow<String?> = authRepository.session
        .map { it.accountUserId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _failure = MutableStateFlow<FailureReason?>(null)
    val failure: StateFlow<FailureReason?> = _failure.asStateFlow()

    fun toggleAttendance(id: String) {
        viewModelScope.launch {
            when (val result = repository.toggleAttendance(id)) {
                is Resource.Success -> _failure.value = null
                is Resource.Failure -> _failure.value = result.reason
                Resource.Loading -> Unit
            }
        }
    }

    fun delete(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (repository.deleteGathering(id)) onDeleted()
        }
    }

    fun clearFailure() {
        _failure.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                GatheringsViewModel(container.gatheringRepository, container.authRepository)
            }
        }
    }
}
