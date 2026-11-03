package com.nexa.social.domain.repository

import com.nexa.social.domain.model.WellbeingPlan
import kotlinx.coroutines.flow.Flow

/** Persists the local focus and wellbeing plan. */
interface WellbeingRepository {
    val plan: Flow<WellbeingPlan>
    suspend fun save(plan: WellbeingPlan)
    suspend fun refresh() {}
}
