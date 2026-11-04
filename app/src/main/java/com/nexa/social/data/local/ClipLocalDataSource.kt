package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.Clip

/** Persists local clips. Seeded once from clearly-labelled sample clips (no bundled video files). */
class ClipLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "clips.json"
        const val OUTBOX_FILE = "clip_outbox.json"
    }

    fun seedIfEmpty() {
        if (!store.exists(FILE)) {
            store.writeList(FILE, SampleData.clips())
        }
    }

    fun readClips(): List<Clip> {
        seedIfEmpty()
        return store.readList(FILE)
    }

    fun writeClips(clips: List<Clip>) = store.writeList(FILE, clips)

    fun readOutbox(): List<PendingClipAction> = store.readList(OUTBOX_FILE)

    fun writeOutbox(actions: List<PendingClipAction>) = store.writeList(OUTBOX_FILE, actions)
}


/** Desired-state action; retries are idempotent and safe after process death. */
data class PendingClipAction(
    val id: String,
    val clipId: String,
    val userId: String,
    val relation: String,
    val enabled: Boolean,
    val createdAt: Long
)
