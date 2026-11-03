package com.nexa.social.feature.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.PostComment
import com.nexa.social.domain.model.PostReaction
import com.nexa.social.domain.model.ReportReason
import com.nexa.social.domain.model.ReportTargetType
import com.nexa.social.domain.model.User
import com.nexa.social.domain.model.accountUserId
import com.nexa.social.domain.repository.AuthRepository
import com.nexa.social.domain.repository.CommentRepository
import com.nexa.social.domain.repository.PostRepository
import com.nexa.social.domain.repository.SafetyRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PostReactionsUiState(
    val isLoading: Boolean = false,
    val reactions: List<PostReaction> = emptyList(),
    val error: FailureReason? = null
)

class PostDetailViewModel(
    private val postId: String,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val safetyRepository: SafetyRepository,
    private val userRepository: UserRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val post: StateFlow<Post?> = postRepository.observePost(postId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val comments: StateFlow<List<PostComment>> = commentRepository.observeComments(postId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val suggestedUsers: StateFlow<List<User>> = userRepository.observeAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val currentUserId: StateFlow<String?> = authRepository.session
        .map { it.accountUserId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val canManage: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(post, currentUserId) { p, id ->
            p != null && id != null && p.author.id == id
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _reactions = MutableStateFlow(PostReactionsUiState())
    val reactions: StateFlow<PostReactionsUiState> = _reactions.asStateFlow()
    private var reactionsLoaded = false
    private val _commentReactions = MutableStateFlow(PostReactionsUiState())
    val commentReactions: StateFlow<PostReactionsUiState> = _commentReactions.asStateFlow()

    fun loadCommentReactions(commentId: String) {
        viewModelScope.launch {
            _commentReactions.value = PostReactionsUiState(isLoading = true)
            runCatching { commentRepository.getCommentReactions(commentId) }
                .onSuccess { _commentReactions.value = PostReactionsUiState(reactions = it) }
                .onFailure { _commentReactions.value = PostReactionsUiState(error = FailureReason.NETWORK) }
        }
    }


    fun loadReactions(force: Boolean = false) {
        if (_reactions.value.isLoading) return
        if (!force && reactionsLoaded) return
        viewModelScope.launch {
            _reactions.value = _reactions.value.copy(isLoading = true, error = null)
            try {
                val list = postRepository.getPostReactions(postId)
                reactionsLoaded = true
                _reactions.value = PostReactionsUiState(reactions = list)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _reactions.value = _reactions.value.copy(isLoading = false, error = FailureReason.NETWORK)
            }
        }
    }

    fun toggleLike(reaction: String? = null) = viewModelScope.launch {
        runCatching { postRepository.toggleLike(postId, reaction) }
    }

    fun toggleSave() = viewModelScope.launch {
        runCatching { postRepository.toggleSave(postId) }
    }

    fun togglePin(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(runCatching { postRepository.togglePin(postId) }.isSuccess)
        }
    }

    fun recordShare() {
        viewModelScope.launch {
            runCatching { postRepository.incrementShare(postId) }
        }
    }

    fun addComment(
        text: String,
        parentId: String? = null,
        imageUri: String? = null,
        onResult: (FailureReason?) -> Unit
    ) {
        viewModelScope.launch {
            when (val result = commentRepository.addComment(postId, text, parentId, imageUri)) {
                is Resource.Success -> onResult(null)
                is Resource.Failure -> onResult(result.reason)
                Resource.Loading -> Unit
            }
        }
    }

    fun updatePost(
        text: String,
        imageUris: List<String>,
        tags: List<String>,
        audience: com.nexa.social.domain.model.Audience,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val updated = postRepository.updatePost(postId, text, imageUris, tags, audience)
            onResult(updated != null)
        }
    }

    val canLoadMore: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        comments,
        commentRepository.observeCommentPageSize()
    ) { list, pageSize ->
        list.size >= pageSize
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun loadMoreComments() = viewModelScope.launch { commentRepository.loadMoreComments() }

    fun editComment(commentId: String, text: String, onResult: (FailureReason?) -> Unit) {
        viewModelScope.launch {
            when (val result = commentRepository.editComment(commentId, text)) {
                is Resource.Success -> onResult(null)
                is Resource.Failure -> onResult(result.reason)
                Resource.Loading -> Unit
            }
        }
    }

    fun deleteComment(commentId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(commentRepository.deleteComment(commentId)) }
    }

    fun toggleCommentLike(commentId: String, emoji: String = "❤️") {
        viewModelScope.launch { runCatching { commentRepository.toggleCommentLike(commentId, emoji) } }
    }

    fun unfollowAuthor(authorId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(runCatching { userRepository.setFollowing(authorId, false) }.getOrDefault(false))
        }
    }

    fun blockAuthor(author: com.nexa.social.domain.model.UserSummary, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = safetyRepository.block(author)
            onResult(result is Resource.Success)
        }
    }

    fun delete(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(postRepository.deletePost(postId))
        }
    }

    fun hide(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { postRepository.hidePost(postId) }.isSuccess)
        }
    }

    fun report(reason: ReportReason, note: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = safetyRepository.submitReport(ReportTargetType.POST, postId, reason, note)
            onResult(result is Resource.Success)
        }
    }

    companion object {
        fun factory(postId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                PostDetailViewModel(
                    postId = postId,
                    postRepository = container.postRepository,
                    commentRepository = container.commentRepository,
                    safetyRepository = container.safetyRepository,
                    userRepository = container.userRepository,
                    authRepository = container.authRepository
                )
            }
        }
    }
}
