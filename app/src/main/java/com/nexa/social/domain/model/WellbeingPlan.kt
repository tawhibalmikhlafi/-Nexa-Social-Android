package com.nexa.social.domain.model

/**
 * Local wellbeing configuration for the "Wellbeing OS" tools. These values are preferences, not
 * medical advice, and they are applied by the app UI rather than an OS-level screen-time service.
 */
data class WellbeingPlan(
    val dailyIntention: String = "",
    val focusMode: Boolean = false,
    val quietHoursEnabled: Boolean = true,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val dailyGoalMinutes: Int = 45,
    val mindfulReminders: Boolean = true,
    val weeklyReflection: Boolean = true
) {
    companion object {
        val goalChoices: List<Int> = listOf(15, 30, 45, 60, 90)
    }
}
