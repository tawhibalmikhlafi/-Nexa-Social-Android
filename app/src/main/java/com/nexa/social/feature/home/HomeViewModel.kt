package com.nexa.social.feature.home

import com.nexa.social.domain.repository.AuthRepository
import com.nexa.social.domain.repository.CommentRepository
import com.nexa.social.domain.repository.FeedRepository
import com.nexa.social.domain.repository.GatheringRepository
import com.nexa.social.domain.repository.NotificationRepository
import com.nexa.social.domain.repository.PostRepository
import com.nexa.social.domain.repository.StoryRepository
import com.nexa.social.domain.model.accountUserId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.FeedType
import com.nexa.social.domain.model.Gathering
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.PostComment
import com.nexa.social.domain.model.StoryGroup

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val feedRepository: FeedRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val preferenceStorage: PreferenceStorage,
    notificationRepository: NotificationRepository,
    storyRepository: StoryRepository,
    gatheringRepository: GatheringRepository,
    authRepository: AuthRepository
) : ViewModel() {

    private val commentPreviewFlows = mutableMapOf<String, Flow<List<PostComment>>>()

    val userId: StateFlow<String?> = authRepository.session
        .map { it.accountUserId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _feedType = MutableStateFlow(FeedType.LATEST)
    val feedType: StateFlow<FeedType> = _feedType.asStateFlow()

    val feed: StateFlow<List<Post>> = _feedType
        .flatMapLatest { feedRepository.observeFeed(it) }
        .catch {
            Log.e("HomeViewModel", "Feed flow failed; showing an empty feed", it)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount: StateFlow<Int> = notificationRepository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val storyGroups: StateFlow<List<StoryGroup>> = storyRepository.observeStoryGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nextGathering: StateFlow<Gathering?> = gatheringRepository.observeUpcoming()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { _feedType.value = preferenceStorage.feedType.first() }
    }

    fun setFeedType(type: FeedType) {
        _feedType.value = type
        viewModelScope.launch { preferenceStorage.setFeedType(type) }
    }

    fun observeCommentPreview(postId: String): Flow<List<PostComment>> = commentPreviewFlows.getOrPut(postId) {
        flow { emit(commentRepository.getCommentPreview(postId)) }
    }

    fun loadMore() {
        viewModelScope.launch { postRepository.loadMoreFeed() }
    }

    fun toggleLike(postId: String, reaction: String? = null) {
        viewModelScope.launch { postRepository.toggleLike(postId, reaction) }
    }

    fun toggleSave(postId: String) {
        viewModelScope.launch { postRepository.toggleSave(postId) }
    }

    fun recordShare(postId: String) {
        viewModelScope.launch {
            runCatching { postRepository.incrementShare(postId) }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch { postRepository.deletePost(postId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                HomeViewModel(
                    feedRepository = container.feedRepository,
                    postRepository = container.postRepository,
                    commentRepository = container.commentRepository,
                    preferenceStorage = container.preferenceStorage,
                    notificationRepository = container.notificationRepository,
                    storyRepository = container.storyRepository,
                    gatheringRepository = container.gatheringRepository,
                    authRepository = container.authRepository
                )
            }
        }
    }
}
