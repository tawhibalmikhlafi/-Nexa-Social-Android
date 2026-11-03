package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.domain.model.PostComment

/** Persists locally authored comments. Starts empty; nothing is prefilled. */
class CommentLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "comments.json"
    }

    fun readComments(): List<PostComment> = store.readList(FILE)

    fun writeComments(comments: List<PostComment>) = store.writeList(FILE, comments)
}
