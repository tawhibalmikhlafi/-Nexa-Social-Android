package com.nexa.social.feature.wellbeing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.WellbeingPlan
import com.nexa.social.domain.repository.WellbeingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WellbeingViewModel(
    private val repository: WellbeingRepository
) : ViewModel() {

    val plan: StateFlow<WellbeingPlan> = repository.plan
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WellbeingPlan())

    fun saveIntention(text: String) = update { it.copy(dailyIntention = text.trim()) }

    fun setFocusMode(enabled: Boolean) = update { it.copy(focusMode = enabled) }

    fun setQuietHours(enabled: Boolean) = update { it.copy(quietHoursEnabled = enabled) }

    fun setGoal(minutes: Int) = update { it.copy(dailyGoalMinutes = minutes) }

    fun setMindfulReminders(enabled: Boolean) = update { it.copy(mindfulReminders = enabled) }

    fun setWeeklyReflection(enabled: Boolean) = update { it.copy(weeklyReflection = enabled) }

    private fun update(transform: (WellbeingPlan) -> WellbeingPlan) {
        val next = transform(plan.value)
        viewModelScope.launch { repository.save(next) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WellbeingViewModel(nexaApplication().container.wellbeingRepository)
            }
        }
    }
}
