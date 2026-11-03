package com.nexa.social.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.common.LocaleManager
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.NotificationType
import com.nexa.social.domain.model.FeedType
import com.nexa.social.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(
    private val preferenceStorage: PreferenceStorage,
    private val authRepository: AuthRepository,
    private val appContext: Context
) : ViewModel() {

    val language: StateFlow<String> = preferenceStorage.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocaleManager.SYSTEM)

    val notificationsEnabled: StateFlow<Boolean> = preferenceStorage.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val notificationCategories: StateFlow<Map<NotificationType, Boolean>> = preferenceStorage.notificationTypeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val feedType: StateFlow<FeedType> = preferenceStorage.feedType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedType.LATEST)

    val videoAutoplay: StateFlow<Boolean> = preferenceStorage.videoAutoplay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val dataSaver: StateFlow<Boolean> = preferenceStorage.dataSaver
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val reduceMotion: StateFlow<Boolean> = preferenceStorage.reduceMotion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val session: StateFlow<SessionState> = authRepository.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.None)

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    init { refreshCacheSize() }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSizeBytes.value = withContext(Dispatchers.IO) {
                cacheDirectories().sumOf { it.recursiveSize() }
            }
        }
    }

    fun clearCache(onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cacheDirectories().forEach { directory ->
                    directory.listFiles()?.forEach { file -> file.deleteRecursively() }
                }
            }
            refreshCacheSize()
            onComplete()
        }
    }

    private fun cacheDirectories(): List<File> = listOfNotNull(
        appContext.cacheDir,
        appContext.externalCacheDir
    )

    private fun File.recursiveSize(): Long =
        if (isFile) length() else listFiles()?.sumOf { it.recursiveSize() } ?: 0L

    fun setLanguage(tag: String) {
        viewModelScope.launch {
            preferenceStorage.setLanguage(tag)
            LocaleManager.persist(appContext, tag)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferenceStorage.setNotificationsEnabled(enabled) }
    }

    fun setNotificationCategory(type: NotificationType, enabled: Boolean) {
        viewModelScope.launch { preferenceStorage.setNotificationTypeEnabled(type, enabled) }
    }

    fun setFeedType(type: FeedType) {
        viewModelScope.launch { preferenceStorage.setFeedType(type) }
    }

    fun setVideoAutoplay(enabled: Boolean) {
        viewModelScope.launch { preferenceStorage.setVideoAutoplay(enabled) }
    }

    fun setDataSaver(enabled: Boolean) {
        viewModelScope.launch { preferenceStorage.setDataSaver(enabled) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { preferenceStorage.setReduceMotion(enabled) }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = nexaApplication()
                SettingsViewModel(
                    preferenceStorage = app.container.preferenceStorage,
                    authRepository = app.container.authRepository,
                    appContext = app.applicationContext
                )
            }
        }
    }
}
