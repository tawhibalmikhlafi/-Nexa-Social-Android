package com.nexa.social.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.GroupMember
import com.nexa.social.domain.model.GroupRole
import com.nexa.social.domain.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupMembersUiState(
    val isLoading: Boolean = true,
    val members: List<GroupMember> = emptyList(),
    val searchQuery: String = "",
    val myRole: GroupRole? = null,
    val error: String? = null,
)

class GroupMembersViewModel(
    private val repo: GroupRepository,
    private val groupId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupMembersUiState())
    val state: StateFlow<GroupMembersUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        when (val result = repo.getMembers(groupId)) {
            is Resource.Success -> _state.update {
                it.copy(members = result.data, isLoading = false)
            }
            is Resource.Failure -> _state.update {
                it.copy(isLoading = false, error = "load_failed")
            }
            Resource.Loading -> Unit
        }
        when (val result = repo.getGroup(groupId)) {
            is Resource.Success -> _state.update { it.copy(myRole = result.data.myRole) }
            else -> Unit
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(searchQuery = query) }

    fun setRole(userId: String, role: String) = viewModelScope.launch {
        when (repo.setMemberRole(groupId, userId, role)) {
            is Resource.Success -> load()
            else -> _state.update { it.copy(error = "role_failed") }
        }
    }

    fun remove(userId: String, ban: Boolean) = viewModelScope.launch {
        when (repo.removeMember(groupId, userId, ban)) {
            is Resource.Success -> load()
            else -> _state.update { it.copy(error = "remove_failed") }
        }
    }

    fun unban(userId: String) = viewModelScope.launch {
        when (repo.unbanMember(groupId, userId)) {
            is Resource.Success -> load()
            else -> _state.update { it.copy(error = "remove_failed") }
        }
    }

    companion object {
        fun factory(groupId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GroupMembersViewModel(nexaApplication().container.groupRepository, groupId)
            }
        }
    }
}
