package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.NexaNotification

/** Persists local sample notifications and their read state. */
class NotificationLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "notifications.json"
    }

    fun seedIfEmpty() {
        if (!store.exists(FILE)) {
            store.writeList(FILE, SampleData.notifications)
        }
    }

    fun readNotifications(): List<NexaNotification> {
        seedIfEmpty()
        return store.readList(FILE)
    }

    fun writeNotifications(notifications: List<NexaNotification>) = store.writeList(FILE, notifications)
}
