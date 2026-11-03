package com.nexa.social.feature.follow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.User
import com.nexa.social.domain.model.accountUserId
import com.nexa.social.domain.repository.PresenceRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FollowListMode { FOLLOWERS, FOLLOWING, SUGGESTIONS, ACTIVE }

@OptIn(ExperimentalCoroutinesApi::class)
class FollowListViewModel(
    private val targetUserId: String,
    mode: FollowListMode,
    private val userRepository: UserRepository,
    private val presenceRepository: PresenceRepository,
    preferences: PreferenceStorage
) : ViewModel() {

    val users: StateFlow<List<User>> = (
        when (mode) {
            FollowListMode.FOLLOWERS -> userRepository.observeFollowersOf(targetUserId)
            FollowListMode.FOLLOWING -> userRepository.observeFollowingOf(targetUserId)
            FollowListMode.SUGGESTIONS -> combine(
                userRepository.observeAllUsers(),
                userRepository.observeFollowingIds(),
                preferences.session.map { it.accountUserId }
            ) { all, following, currentUserId ->
                all.asSequence()
                    .filter { it.id != currentUserId && it.id !in following }
                    .distinctBy { it.id }
                    .take(20)
                    .toList()
            }
            FollowListMode.ACTIVE -> combine(
                userRepository.observeAllUsers(),
                preferences.session.map { it.accountUserId }
            ) { all, currentUserId ->
                all.asSequence()
                    .filter { it.id != currentUserId }
                    .distinctBy { it.id }
                    .toList()
            }.flatMapLatest { candidates ->
                if (candidates.isEmpty()) {
                    flowOf(emptyList<User>())
                } else {
                    flow {
                        var lastActive = emptyList<User>()
                        while (true) {
                            val online = presenceRepository.areOnline(candidates.map { it.id })
                            val active = if (online.isEmpty()) {
                                lastActive
                            } else {
                                candidates.filter { online[it.id] == true }
                            }
                            emit(active)
                            lastActive = active
                            delay(ACTIVE_REFRESH_MS)
                        }
                    }
                }
            }
        }
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val followingIds: StateFlow<Set<String>> = userRepository.observeFollowingIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val currentUserId: StateFlow<String?> = preferences.session
        .map { it.accountUserId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _overrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val overrides: StateFlow<Map<String, Boolean>> = _overrides.asStateFlow()

    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    fun toggleFollowing(userId: String, follow: Boolean) {
        if (userId in _pending.value) return
        viewModelScope.launch {
            _pending.value = _pending.value + userId
            val updated = userRepository.setFollowing(userId, follow)
            if (updated) {
                _overrides.value = _overrides.value + (userId to follow)
            } else {
                _error.value = true
            }
            _pending.value = _pending.value - userId
        }
    }

    fun consumeError() {
        _error.value = false
    }

    companion object {
        private const val ACTIVE_REFRESH_MS = 30_000L

        fun factory(targetUserId: String, mode: FollowListMode): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = nexaApplication().container
                    FollowListViewModel(
                        targetUserId = targetUserId,
                        mode = mode,
                        userRepository = container.userRepository,
                        presenceRepository = container.presenceRepository,
                        preferences = container.preferenceStorage
                    )
                }
            }
    }
}
