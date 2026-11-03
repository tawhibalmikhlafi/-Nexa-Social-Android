package com.nexa.social.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.User
import com.nexa.social.domain.model.accountUserId
import com.nexa.social.domain.model.NexaNotification
import com.nexa.social.domain.repository.MessageRepository
import com.nexa.social.domain.repository.NotificationRepository
import com.nexa.social.domain.repository.PostRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val targetUserId: String?,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val messageRepository: MessageRepository,
    private val notificationRepository: NotificationRepository,
    private val preferences: PreferenceStorage
) : ViewModel() {

    val isSelf: Boolean = targetUserId == null

    private val currentUserId: StateFlow<String?> = preferences.session
        .map { it.accountUserId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val user: StateFlow<User?> = (
        if (targetUserId == null) userRepository.currentUser
        else userRepository.observeUser(targetUserId)
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val posts: StateFlow<List<Post>> = user.flatMapLatest { resolved ->
        if (resolved == null) {
            flowOf(emptyList())
        } else {
            postRepository.observePostsByAuthor(resolved.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photos: StateFlow<List<Post>> = posts.map { list ->
        list.filter { !it.imageUri.isNullOrBlank() || it.imageUris.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedPosts: StateFlow<List<Post>> = (
        if (isSelf) postRepository.observeSavedPosts()
        else flowOf(emptyList())
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notifications: StateFlow<List<NexaNotification>> = (
        if (isSelf) notificationRepository.observeNotifications()
        else flowOf(emptyList())
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isGuest: StateFlow<Boolean> = preferences.session
        .map { it is SessionState.Guest }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isOwnProfile: StateFlow<Boolean> = combine(user, currentUserId) { resolved, id ->
        resolved != null && id != null && resolved.id == id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val canMessage: StateFlow<Boolean> = combine(user, currentUserId, isGuest) { resolved, id, guest ->
        !guest && resolved != null && id != null && resolved.id != id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _conversationOpened = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val conversationOpened: SharedFlow<String> = _conversationOpened.asSharedFlow()

    private val _messageBusy = MutableStateFlow(false)
    val messageBusy: StateFlow<Boolean> = _messageBusy.asStateFlow()

    private val _messageError = MutableStateFlow(false)
    val messageError: StateFlow<Boolean> = _messageError.asStateFlow()

    private val followingOverride = MutableStateFlow<Boolean?>(null)
    private val followRequestPending = MutableStateFlow(false)
    private val followersDelta = MutableStateFlow(0)

    private val _followBusy = MutableStateFlow(false)
    val followBusy: StateFlow<Boolean> = _followBusy.asStateFlow()

    private val _followError = MutableStateFlow(false)
    val followError: StateFlow<Boolean> = _followError.asStateFlow()

    val isFollowRequestPending: StateFlow<Boolean> = followRequestPending
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isFollowing: StateFlow<Boolean> = followingOverride
        .map { it == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val followingCount: StateFlow<Int> = user.flatMapLatest { resolved ->
        if (resolved == null) {
            flowOf(0)
        } else {
            userRepository.observeFollowingOf(resolved.id).map { list ->
                maxOf(resolved.followingCount, list.size)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val followersCount: StateFlow<Int> = combine(
        user,
        user.flatMapLatest { resolved ->
            if (resolved == null) flowOf(emptyList())
            else userRepository.observeFollowersOf(resolved.id)
        },
        followersDelta
    ) { resolved, followersList, delta ->
        val base = maxOf(resolved?.followersCount ?: 0, followersList.size)
        (base + delta).coerceAtLeast(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        if (!isSelf) {
            viewModelScope.launch {
                user.filterNotNull().collect { resolved ->
                    followingOverride.value = userRepository.isFollowing(resolved.id)
                    followRequestPending.value = userRepository.hasPendingFollowRequest(resolved.id)
                    followersDelta.value = 0
                }
            }
        }
    }

    fun toggleLike(postId: String, reaction: String? = null) {
        viewModelScope.launch { postRepository.toggleLike(postId, reaction) }
    }

    fun toggleSave(postId: String) {
        viewModelScope.launch { postRepository.toggleSave(postId) }
    }

    fun toggleFollow() {
        val resolved = user.value ?: return
        if (isSelf || resolved.id == currentUserId.value || _followBusy.value) return
        val cancelOrUnfollow = (followingOverride.value == true) || followRequestPending.value
        val follow = !cancelOrUnfollow
        viewModelScope.launch {
            _followBusy.value = true
            try {
                val updated = userRepository.setFollowing(resolved.id, follow)
                if (updated) {
                    if (follow) {
                        val pending = userRepository.hasPendingFollowRequest(resolved.id)
                        followRequestPending.value = pending
                        followingOverride.value = !pending
                        if (!pending) followersDelta.value += 1
                    } else {
                        followRequestPending.value = false
                        if (followingOverride.value == true) followersDelta.value -= 1
                        followingOverride.value = false
                    }
                } else {
                    _followError.value = true
                }
            } catch (_: Exception) {
                _followError.value = true
            } finally {
                _followBusy.value = false
            }
        }
    }

    fun consumeFollowError() {
        _followError.value = false
    }

    fun startConversation() {
        val resolved = user.value ?: return
        val uid = currentUserId.value ?: return
        if (isSelf || resolved.id == uid || _messageBusy.value) return
        viewModelScope.launch {
            _messageBusy.value = true
            val conversation = messageRepository.startConversation(resolved.id)
            if (conversation == null) {
                _messageError.value = true
            } else {
                _conversationOpened.tryEmit(conversation.id)
            }
            _messageBusy.value = false
        }
    }

    fun consumeMessageError() {
        _messageError.value = false
    }

    companion object {
        fun factory(targetUserId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                ProfileViewModel(
                    targetUserId = targetUserId,
                    userRepository = container.userRepository,
                    postRepository = container.postRepository,
                    messageRepository = container.messageRepository,
                    notificationRepository = container.notificationRepository,
                    preferences = container.preferenceStorage
                )
            }
        }
    }
}
