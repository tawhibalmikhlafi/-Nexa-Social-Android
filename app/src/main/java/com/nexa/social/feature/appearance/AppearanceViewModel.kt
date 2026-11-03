package com.nexa.social.feature.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.BackgroundStyle
import com.nexa.social.domain.model.NexaAccent
import com.nexa.social.domain.model.ThemeConfig
import com.nexa.social.domain.model.ThemeMode
import com.nexa.social.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppearanceViewModel(
    private val themeRepository: ThemeRepository
) : ViewModel() {

    private val _draft = MutableStateFlow<ThemeConfig?>(null)
    val draft: StateFlow<ThemeConfig?> = _draft.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch { _draft.value = themeRepository.themeConfig.first() }
    }

    fun setMode(mode: ThemeMode) = update { it.copy(mode = mode) }

    fun setAccent(accent: NexaAccent) = update { it.copy(accent = accent) }

    fun setCustomAccent(argb: Long) = update { it.copy(accent = NexaAccent.CUSTOM, customAccentArgb = argb) }

    fun setBackground(style: BackgroundStyle) = update { it.copy(background = style) }

    fun save() {
        val config = _draft.value ?: return
        viewModelScope.launch {
            themeRepository.save(config)
            _saved.value = true
        }
    }

    fun consumeSaved() {
        _saved.value = false
    }

    private inline fun update(transform: (ThemeConfig) -> ThemeConfig) {
        val current = _draft.value ?: return
        _draft.value = transform(current)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppearanceViewModel(nexaApplication().container.themeRepository)
            }
        }
    }
}
