package com.nexa.social.feature.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.repository.PostRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedPostsViewModel(
    private val postRepository: PostRepository
) : ViewModel() {

    val saved: StateFlow<List<Post>> = postRepository.observeSavedPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hidden: StateFlow<List<Post>> = postRepository.observeHiddenPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleLike(postId: String, reaction: String? = null) = viewModelScope.launch {
        runCatching { postRepository.toggleLike(postId, reaction) }
    }
    fun toggleSave(postId: String) = viewModelScope.launch {
        runCatching { postRepository.toggleSave(postId) }
    }
    fun unhide(postId: String) = viewModelScope.launch { postRepository.unhidePost(postId) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SavedPostsViewModel(nexaApplication().container.postRepository)
            }
        }
    }
}
