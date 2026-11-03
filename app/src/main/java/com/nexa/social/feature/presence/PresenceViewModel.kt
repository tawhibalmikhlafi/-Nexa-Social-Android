package com.nexa.social.feature.presence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.repository.PresenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * Shares one stream per user across composables so multiple avatars do not poll separately.
 * WhileSubscribed(5_000) stops polling when the last collector leaves the screen.
 */
class PresenceViewModel(
    private val repository: PresenceRepository,
) : ViewModel() {

    private val shared = mutableMapOf<String, Flow<Boolean>>()

    fun onlineFor(userId: String): Flow<Boolean> = shared.getOrPut(userId) {
        repository.observeOnline(userId)
            .distinctUntilChanged()
            .catch { emit(false) }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    fun pingNow() = viewModelScope.launch { repository.pingNow() }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PresenceViewModel(nexaApplication().container.presenceRepository)
            }
        }
    }
}
