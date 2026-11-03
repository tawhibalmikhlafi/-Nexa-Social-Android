package com.nexa.social.feature.capsules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.TimeCapsule
import com.nexa.social.domain.model.accountUserId
import com.nexa.social.domain.repository.AuthRepository
import com.nexa.social.domain.repository.CapsuleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CapsulesViewModel(
    private val repository: CapsuleRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val capsules: StateFlow<List<TimeCapsule>> = repository.observeCapsules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userId: StateFlow<String?> = authRepository.session
        .map { it.accountUserId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _opened = MutableStateFlow<TimeCapsule?>(null)
    val opened: StateFlow<TimeCapsule?> = _opened.asStateFlow()

    private val _failure = MutableStateFlow<FailureReason?>(null)
    val failure: StateFlow<FailureReason?> = _failure.asStateFlow()

    fun open(id: String) {
        viewModelScope.launch {
            when (val result = repository.openCapsule(id)) {
                is Resource.Success -> {
                    _failure.value = null
                    _opened.value = result.data
                }
                is Resource.Failure -> _failure.value = result.reason
                Resource.Loading -> Unit
            }
        }
    }

    fun dismissOpened() {
        _opened.value = null
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteCapsule(id) }
    }

    fun clearFailure() {
        _failure.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                CapsulesViewModel(container.capsuleRepository, container.authRepository)
            }
        }
    }
}
