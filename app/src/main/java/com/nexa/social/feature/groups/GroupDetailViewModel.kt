package com.nexa.social.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Group
import com.nexa.social.domain.model.GroupMember
import com.nexa.social.domain.model.GroupPost
import com.nexa.social.domain.model.GroupStats
import com.nexa.social.domain.model.ReactionKind
import com.nexa.social.domain.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

data class GroupDetailUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val posts: List<GroupPost> = emptyList(),
    val members: List<GroupMember> = emptyList(),
    val stats: GroupStats? = null,
    val isSaving: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null,
)

class GroupDetailViewModel(
    private val repo: GroupRepository,
    private val groupId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupDetailUiState())
    val state: StateFlow<GroupDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        var group: Group? = null
        when (val result = repo.getGroup(groupId)) {
            is Resource.Success -> {
                group = result.data
                _state.update { it.copy(group = result.data) }
            }
            is Resource.Failure -> _state.update { it.copy(error = "load_failed") }
            Resource.Loading -> Unit
        }
        when (val result = repo.getMembers(groupId)) {
            is Resource.Success -> _state.update { it.copy(members = result.data) }
            else -> Unit
        }
        loadPosts(reset = true)
        if (group?.canManage == true) {
            when (val result = repo.getGroupStats(groupId)) {
                is Resource.Success -> _state.update { it.copy(stats = result.data) }
                else -> Unit
            }
        }
        _state.update { it.copy(isLoading = false) }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoadingMore || !current.hasMore || current.isLoading) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch { loadPosts(reset = false) }
    }

    fun joinOrRequest(message: String?) = viewModelScope.launch {
        when (val result = repo.joinOrRequest(groupId, message)) {
            is Resource.Success -> {
                _state.update { it.copy(successMessage = result.data) }
                load()
            }
            is Resource.Failure -> _state.update { it.copy(error = "join_failed") }
            Resource.Loading -> Unit
        }
    }

    fun leave() = viewModelScope.launch {
        when (repo.leaveGroup(groupId)) {
            is Resource.Success -> load()
            else -> _state.update { it.copy(error = "leave_failed") }
        }
    }

    fun createPost(content: String, mediaUrl: String?) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (repo.createPost(groupId, content, mediaUrl)) {
                is Resource.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    refreshPosts()
                }
                else -> _state.update { it.copy(isSaving = false, error = "post_failed") }
            }
        }
    }

    fun react(postId: String, kind: ReactionKind?) = viewModelScope.launch {
        val previous = _state.value.posts.firstOrNull { it.id == postId }
        if (previous != null && previous.myReaction != kind) {
            _state.update { state ->
                state.copy(
                    posts = state.posts.map { post ->
                        if (post.id != postId) {
                            post
                        } else {
                            val delta = when {
                                previous.myReaction == null && kind != null -> 1
                                previous.myReaction != null && kind == null -> -1
                                else -> 0
                            }
                            post.copy(
                                myReaction = kind,
                                reactionCount = (post.reactionCount + delta).coerceAtLeast(0),
                            )
                        }
                    }
                )
            }
        }
        when (repo.reactToPost(postId, kind)) {
            is Resource.Success -> refreshPosts()
            else -> {
                if (previous != null) {
                    _state.update { state ->
                        state.copy(
                            posts = state.posts.map { post ->
                                if (post.id == postId) previous else post
                            }
                        )
                    }
                }
                _state.update { it.copy(error = "reaction_failed") }
            }
        }
    }

    fun deletePost(postId: String) = viewModelScope.launch {
        when (repo.deletePost(postId)) {
            is Resource.Success -> refreshPosts()
            else -> _state.update { it.copy(error = "delete_failed") }
        }
    }

    fun clearMessage() = _state.update { it.copy(successMessage = null, error = null) }

    private suspend fun loadPosts(reset: Boolean) {
        val current = _state.value
        val offset = if (reset) 0 else current.posts.size
        when (val result = repo.getGroupPosts(groupId, offset, PAGE_SIZE)) {
            is Resource.Success -> _state.update { state ->
                val posts = if (reset) result.data else state.posts + result.data
                state.copy(
                    posts = posts,
                    hasMore = result.data.size == PAGE_SIZE,
                    isLoadingMore = false,
                )
            }
            is Resource.Failure -> _state.update {
                it.copy(isLoadingMore = false, error = if (reset) "load_failed" else "posts_failed")
            }
            Resource.Loading -> _state.update { it.copy(isLoadingMore = false) }
        }
    }

    private suspend fun refreshPosts() {
        val limit = _state.value.posts.size.coerceAtLeast(PAGE_SIZE)
        when (val result = repo.getGroupPosts(groupId, 0, limit)) {
            is Resource.Success -> _state.update {
                it.copy(posts = result.data, hasMore = result.data.size == limit)
            }
            is Resource.Failure -> _state.update { it.copy(error = "posts_failed") }
            Resource.Loading -> Unit
        }
    }

    companion object {
        fun factory(groupId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GroupDetailViewModel(nexaApplication().container.groupRepository, groupId)
            }
        }
    }
}
