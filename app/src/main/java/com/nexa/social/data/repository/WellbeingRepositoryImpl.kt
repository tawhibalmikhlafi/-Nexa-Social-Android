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
        val safe = sanitize(plan)
        val uid = remote?.currentUserId()
        val owner = local.readOwnerId()
        // Never copy one account's private wellbeing settings into another account.
        if (uid != null && owner != null && owner != uid) return
        withContext(Dispatchers.IO) { local.writePlan(safe) }
        _plan.value = safe
        if (uid != null && remote?.saveWellbeing(safe) == true) {
            local.writeOwnerId(uid)
        }
    }

    override suspend fun refresh() {
        val source = remote ?: return
        val uid = source.currentUserId() ?: return
        val owner = local.readOwnerId()

        if (owner == null) {
            // One-time migration of the pre-cloud local plan to the first signed-in account.
            if (source.saveWellbeing(_plan.value)) local.writeOwnerId(uid)
        } else if (owner != uid) {
            // Account changed: preserve the old local file unless the new account's cloud
            // snapshot was read successfully. Never upload the prior account's settings.
            val accountPlan = source.loadWellbeing().getOrElse { return } ?: WellbeingPlan()
            withContext(Dispatchers.IO) { local.writePlan(accountPlan) }
            local.writeOwnerId(uid)
            _plan.value = accountPlan
            return
        } else {
            // Same account: local-first edits are safely retried as an idempotent upsert.
            source.saveWellbeing(_plan.value)
        }

        val cloudPlan = source.loadWellbeing().getOrElse { return } ?: return
        withContext(Dispatchers.IO) { local.writePlan(cloudPlan) }
        _plan.value = cloudPlan
    }

    private fun sanitize(plan: WellbeingPlan) = plan.copy(
        dailyIntention = plan.dailyIntention.take(500),
        quietStartHour = plan.quietStartHour.coerceIn(0, 23),
        quietEndHour = plan.quietEndHour.coerceIn(0, 23),
        dailyGoalMinutes = plan.dailyGoalMinutes.coerceIn(5, 1440)
    )
}
