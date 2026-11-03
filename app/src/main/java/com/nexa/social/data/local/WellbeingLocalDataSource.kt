package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.domain.model.WellbeingPlan

class WellbeingLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "wellbeing.json"
    }

    fun readPlan(): WellbeingPlan = store.readList<WellbeingPlan>(FILE).firstOrNull()
        ?: WellbeingPlan()

    fun writePlan(plan: WellbeingPlan) = store.writeList(FILE, listOf(plan))
}
