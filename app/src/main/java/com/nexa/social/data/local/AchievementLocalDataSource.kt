package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.domain.model.AchievementSnapshot

/** Persists achievement counters, points and unlocked badges in one small file. */
class AchievementLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "achievements.json"
    }

    fun readSnapshot(): AchievementSnapshot = store.readList<AchievementSnapshot>(FILE).firstOrNull()
        ?: AchievementSnapshot()

    fun writeSnapshot(snapshot: AchievementSnapshot) = store.writeList(FILE, listOf(snapshot))
}
