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
import com.nexa.social.domain.model.FollowRequest
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private val _followBusy = MutableStateFlow(false)
    val followBusy: StateFlow<Boolean> = _followBusy.asStateFlow()

    private val _followError = MutableStateFlow(false)
    val followError: StateFlow<Boolean> = _followError.asStateFlow()

    private val _incomingFollowRequests = MutableStateFlow<List<FollowRequest>>(emptyList())
    val incomingFollowRequests: StateFlow<List<FollowRequest>> = _incomingFollowRequests.asStateFlow()
    private val _followRequestsLoading = MutableStateFlow(false)
    val followRequestsLoading: StateFlow<Boolean> = _followRequestsLoading.asStateFlow()
    private val _followRequestError = MutableStateFlow(false)
    val followRequestError: StateFlow<Boolean> = _followRequestError.asStateFlow()
    private val _followRequestActionId = MutableStateFlow<String?>(null)
    val followRequestActionId: StateFlow<String?> = _followRequestActionId.asStateFlow()

    val isFollowRequestPending: StateFlow<Boolean> = followRequestPending
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isFollowing: StateFlow<Boolean> = followingOverride
        .map { it == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val followingList: StateFlow<List<User>> = user.flatMapLatest { resolved ->
        if (resolved == null) flowOf(emptyList())
        else userRepository.observeFollowingOf(resolved.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val followersList: StateFlow<List<User>> = user.flatMapLatest { resolved ->
        if (resolved == null) flowOf(emptyList())
        else userRepository.observeFollowersOf(resolved.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val followingCount: StateFlow<Int> = combine(user, currentUserId, followingList) { resolved, viewerId, list ->
        if (resolved != null && resolved.id == viewerId) maxOf(resolved.followingCount, list.size)
        else list.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val followersCount: StateFlow<Int> = combine(user, currentUserId, followersList) { resolved, viewerId, list ->
        if (resolved != null && resolved.id == viewerId) maxOf(resolved.followersCount, list.size)
        else resolved?.followersCount ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        if (!isSelf) {
            viewModelScope.launch {
                user.filterNotNull().collect { resolved ->
                    followingOverride.value = userRepository.isFollowing(resolved.id)
                    userRepository.hasPendingFollowRequest(resolved.id)?.let { pending ->
                        followRequestPending.value = pending
                    }
                }
            }
        } else {
            viewModelScope.launch {
                preferences.session.collectLatest { session ->
                    if (session is SessionState.RemoteAccount) {
                        // Keep incoming private-account requests current while the owner
                        // remains on their profile; otherwise an initially empty card
                        // would never appear when another user sends a request.
                        while (true) {
                            refreshIncomingFollowRequests()
                            delay(FOLLOW_REQUEST_REFRESH_MS)
                        }
                    } else {
                        _incomingFollowRequests.value = emptyList()
                    }
                }
            }
        }
    }

    fun refreshIncomingFollowRequests() {
        if (!isSelf || _followRequestsLoading.value) return
        viewModelScope.launch {
            _followRequestsLoading.value = true
            _followRequestError.value = false
            try {
                val requests = userRepository.loadIncomingFollowRequests()
                if (requests == null) _followRequestError.value = true
                else _incomingFollowRequests.value = requests
            } catch (_: Exception) {
                _followRequestError.value = true
            } finally {
                _followRequestsLoading.value = false
            }
        }
    }

    fun respondToFollowRequest(requestId: String, approve: Boolean) {
        if (!isSelf || _followRequestActionId.value != null) return
        viewModelScope.launch {
            _followRequestActionId.value = requestId
            _followRequestError.value = false
            try {
                val ok = userRepository.respondToFollowRequest(requestId, approve)
                if (ok) {
                    _incomingFollowRequests.update { list -> list.filterNot { it.id == requestId } }
                    userRepository.refresh()
                    refreshIncomingFollowRequests()
                } else {
                    _followRequestError.value = true
                }
            } catch (_: Exception) {
                _followRequestError.value = true
            } finally {
                _followRequestActionId.value = null
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
                        if (pending == null) {
                            _followError.value = true
                        } else {
                            followRequestPending.value = pending
                            followingOverride.value = !pending
                        }
                    } else {
                        followRequestPending.value = false
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
        private const val FOLLOW_REQUEST_REFRESH_MS = 15_000L

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
