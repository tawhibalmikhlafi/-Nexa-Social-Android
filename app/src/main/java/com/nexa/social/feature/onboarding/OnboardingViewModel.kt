package com.nexa.social.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.ui.nexaApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val preferenceStorage: PreferenceStorage
) : ViewModel() {

    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    fun markCompleted() {
        viewModelScope.launch {
            preferenceStorage.setOnboardingCompleted(true)
            _completed.value = true
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OnboardingViewModel(nexaApplication().container.preferenceStorage)
            }
        }
    }
}
