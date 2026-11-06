package com.nexa.social.data.repository

import com.nexa.social.data.local.WellbeingLocalDataSource
import com.nexa.social.data.remote.SupabaseLifeDataSource
import com.nexa.social.domain.model.WellbeingPlan
import com.nexa.social.domain.repository.WellbeingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class WellbeingRepositoryImpl(
    private val local: WellbeingLocalDataSource,
    private val remote: SupabaseLifeDataSource? = null
) : WellbeingRepository {

    private val _plan = MutableStateFlow(local.readPlan())
    override val plan: Flow<WellbeingPlan> = _plan.asStateFlow()

    override suspend fun save(plan: WellbeingPlan) {
        val safe = plan.copy(
            dailyIntention = plan.dailyIntention.take(500),
            quietStartHour = plan.quietStartHour.coerceIn(0, 23),
            quietEndHour = plan.quietEndHour.coerceIn(0, 23),
            dailyGoalMinutes = plan.dailyGoalMinutes.coerceIn(5, 1440)
        )
        // Persist locally first so a network outage never discards a preference change.
        withContext(Dispatchers.IO) { local.writePlan(safe) }
        _plan.value = safe
        remote?.saveWellbeing(safe)
    }

    override suspend fun refresh() {
        val source = remote ?: return
        val localPlan = _plan.value
        // Idempotent upsert migrates the existing device plan before cloud data is applied.
        source.saveWellbeing(localPlan)
        val cloudPlan = source.loadWellbeing() ?: return
        withContext(Dispatchers.IO) { local.writePlan(cloudPlan) }
        _plan.value = cloudPlan
    }
}
