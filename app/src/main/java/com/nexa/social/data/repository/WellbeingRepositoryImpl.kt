package com.nexa.social.data.repository

import com.nexa.social.data.local.WellbeingLocalDataSource
import com.nexa.social.domain.model.WellbeingPlan
import com.nexa.social.domain.repository.WellbeingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class WellbeingRepositoryImpl(
    private val local: WellbeingLocalDataSource
) : WellbeingRepository {

    private val _plan = MutableStateFlow(local.readPlan())
    override val plan: Flow<WellbeingPlan> = _plan.asStateFlow()

    override suspend fun save(plan: WellbeingPlan) {
        _plan.value = plan
        withContext(Dispatchers.IO) { local.writePlan(plan) }
    }
}
