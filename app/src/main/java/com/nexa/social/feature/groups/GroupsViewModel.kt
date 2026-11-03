package com.nexa.social.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Group
import com.nexa.social.domain.model.GroupInvite
import com.nexa.social.domain.model.GroupSearchResult
import com.nexa.social.domain.repository.GroupRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val isLoading: Boolean = true,
    val myGroups: List<Group> = emptyList(),
    val invites: List<GroupInvite> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<GroupSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
)

class GroupsViewModel(private val repo: GroupRepository) : ViewModel() {

    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repo.observeMyGroups().collect { groups ->
                _state.update { it.copy(myGroups = groups, isLoading = false) }
            }
        }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        when (repo.refreshMyGroups()) {
            is Resource.Failure -> _state.update { it.copy(error = "network") }
            else -> Unit
        }
        when (val result = repo.getMyInvites()) {
            is Resource.Success -> _state.update { it.copy(invites = result.data) }
            else -> Unit
        }
        _state.update { it.copy(isLoading = false) }
    }

    fun onSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(isSearching = true) }
            when (val result = repo.searchGroups(query)) {
                is Resource.Success -> _state.update {
                    it.copy(searchResults = result.data, isSearching = false)
                }
                is Resource.Failure -> _state.update {
                    it.copy(searchResults = emptyList(), isSearching = false)
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun respondToInvite(id: String, accept: Boolean) = viewModelScope.launch {
        repo.respondToInvite(id, accept)
        refresh()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { GroupsViewModel(nexaApplication().container.groupRepository) }
        }
    }
}
