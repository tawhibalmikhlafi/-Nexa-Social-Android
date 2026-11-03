package com.nexa.social.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.GroupComment
import com.nexa.social.domain.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupCommentsUiState(
    val isLoading: Boolean = true,
    val comments: List<GroupComment> = emptyList(),
    val isSaving: Boolean = false,
    val canManage: Boolean = false,
    val error: String? = null,
)

class GroupCommentsViewModel(
    private val repo: GroupRepository,
    private val groupId: String,
    private val postId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupCommentsUiState())
    val state: StateFlow<GroupCommentsUiState> = _state.asStateFlow()

    init {
        load()
        loadPermissions()
    }

    private fun loadPermissions() = viewModelScope.launch {
        when (val result = repo.getGroup(groupId)) {
            is Resource.Success -> _state.update {
                it.copy(canManage = result.data.canManage)
            }
            else -> Unit
        }
    }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        when (val result = repo.getPostComments(postId)) {
            is Resource.Success -> _state.update {
                it.copy(comments = result.data, isLoading = false)
            }
            is Resource.Failure -> _state.update {
                it.copy(isLoading = false, error = "load_failed")
            }
            Resource.Loading -> Unit
        }
    }

    fun add(content: String, parentId: String?) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (repo.addComment(postId, content, parentId)) {
                is Resource.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    load()
                }
                else -> _state.update { it.copy(isSaving = false, error = "comment_failed") }
            }
        }
    }

    fun delete(commentId: String) = viewModelScope.launch {
        when (repo.deleteComment(commentId)) {
            is Resource.Success -> load()
            else -> _state.update { it.copy(error = "delete_failed") }
        }
    }

    companion object {
        fun factory(groupId: String, postId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GroupCommentsViewModel(
                    nexaApplication().container.groupRepository,
                    groupId,
                    postId,
                )
            }
        }
    }
}
