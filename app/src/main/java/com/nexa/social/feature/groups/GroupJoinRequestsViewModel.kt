package com.nexa.social.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.JoinRequest
import com.nexa.social.domain.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinRequestsUiState(
    val isLoading: Boolean = true,
    val requests: List<JoinRequest> = emptyList(),
    val error: String? = null,
)

class GroupJoinRequestsViewModel(
    private val repo: GroupRepository,
    private val groupId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinRequestsUiState())
    val state: StateFlow<JoinRequestsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        when (val result = repo.getJoinRequests(groupId)) {
            is Resource.Success -> _state.update {
                it.copy(requests = result.data, isLoading = false)
            }
            is Resource.Failure -> _state.update {
                it.copy(isLoading = false, error = "load_failed")
            }
            Resource.Loading -> Unit
        }
    }

    fun approve(id: String) = viewModelScope.launch {
        when (repo.approveJoinRequest(id, true)) {
            is Resource.Success -> load()
            else -> _state.update { it.copy(error = "action_failed") }
        }
    }

    fun reject(id: String) = viewModelScope.launch {
        when (repo.approveJoinRequest(id, false)) {
            is Resource.Success -> load()
            else -> _state.update { it.copy(error = "action_failed") }
        }
    }

    companion object {
        fun factory(groupId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GroupJoinRequestsViewModel(nexaApplication().container.groupRepository, groupId)
            }
        }
    }
}
