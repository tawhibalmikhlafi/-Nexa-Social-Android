package com.nexa.social.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.CommunityPost
import com.nexa.social.domain.repository.CommunityRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CommunityDetailsViewModel(
    private val communityId: String,
    private val communityRepository: CommunityRepository
) : ViewModel() {

    val community: StateFlow<Community?> = communityRepository.observeCommunity(communityId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val posts: StateFlow<List<CommunityPost>> = communityRepository.observeCommunityPosts(communityId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleMembership() {
        viewModelScope.launch { communityRepository.toggleMembership(communityId) }
    }

    fun togglePostLike(postId: String, reaction: String? = null) {
        viewModelScope.launch { communityRepository.toggleCommunityPostLike(communityId, postId, reaction) }
    }

    fun createPost(text: String, imageUris: List<String>, onCreated: () -> Unit) {
        if (text.isBlank() && imageUris.isEmpty()) return
        viewModelScope.launch {
            communityRepository.createCommunityPost(communityId, text, imageUris)
            onCreated()
        }
    }

    companion object {
        fun factory(communityId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CommunityDetailsViewModel(
                    communityId = communityId,
                    communityRepository = nexaApplication().container.communityRepository
                )
            }
        }
    }
}
