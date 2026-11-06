package com.nexa.social.domain.repository

import com.nexa.social.domain.model.WellbeingPlan
import kotlinx.coroutines.flow.Flow

/** Persists a local-first wellbeing plan and synchronizes it to the signed-in Supabase account. */
interface WellbeingRepository {
    val plan: Flow<WellbeingPlan>
    suspend fun save(plan: WellbeingPlan)
    suspend fun refresh() {}
}
