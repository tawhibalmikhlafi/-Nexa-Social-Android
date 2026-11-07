package com.nexa.social.feature.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.data.repository.AchievementRepositoryImpl
import com.nexa.social.domain.model.AchievementDefinition
import com.nexa.social.domain.model.AchievementSnapshot
import com.nexa.social.domain.model.CounterKind
import com.nexa.social.domain.repository.AchievementRepository
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AchievementsViewModel(
    achievementRepository: AchievementRepository
) : ViewModel() {

    val snapshot: StateFlow<AchievementSnapshot> = achievementRepository.observeSnapshot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementSnapshot())

    val definitions: List<AchievementDefinition> = AchievementRepositoryImpl.definitions

    init {
        viewModelScope.launch {
            // With Supabase configured, amount=0 refreshes the server-awarded achievement snapshot.
            achievementRepository.record(CounterKind.POSTS_CREATED, 0)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AchievementsViewModel(nexaApplication().container.achievementRepository)
            }
        }
    }
}
