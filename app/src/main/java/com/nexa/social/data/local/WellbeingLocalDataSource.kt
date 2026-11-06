package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.domain.model.WellbeingPlan

class WellbeingLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "wellbeing.json"
        const val OWNER_FILE = "wellbeing_owner.json"
    }

    fun readPlan(): WellbeingPlan = store.readList<WellbeingPlan>(FILE).firstOrNull()
        ?: WellbeingPlan()

    fun writePlan(plan: WellbeingPlan) = store.writeList(FILE, listOf(plan))

    fun readOwnerId(): String? = store.readList<String>(OWNER_FILE).firstOrNull()

    fun writeOwnerId(userId: String) = store.writeList(OWNER_FILE, listOf(userId))
}
