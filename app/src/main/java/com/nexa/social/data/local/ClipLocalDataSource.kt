package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.Clip
import com.nexa.social.domain.model.UserSummary

/** Persists local clips. Seeded once from clearly-labelled sample clips (no bundled video files). */
class ClipLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "clips.json"
        const val OUTBOX_FILE = "clip_outbox.json"
        const val COMMENT_OUTBOX_FILE = "clip_comment_outbox.json"
        const val UPLOAD_OUTBOX_FILE = "clip_upload_outbox.json"
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

    fun readCommentOutbox(): List<PendingClipComment> = store.readList(COMMENT_OUTBOX_FILE)

    fun writeCommentOutbox(comments: List<PendingClipComment>) = store.writeList(COMMENT_OUTBOX_FILE, comments)
    fun readUploadOutbox(): List<PendingClipUpload> = store.readList(UPLOAD_OUTBOX_FILE)
    fun writeUploadOutbox(items: List<PendingClipUpload>) = store.writeList(UPLOAD_OUTBOX_FILE, items)
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


data class PendingClipComment(
    val id: String,
    val userId: String,
    val clipId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val content: String,
    val createdAt: Long
)


data class PendingClipUpload(val id: String, val userId: String, val localVideoPath: String, val caption: String, val createdAt: Long, val author: UserSummary, val localCoverPath: String? = null)
