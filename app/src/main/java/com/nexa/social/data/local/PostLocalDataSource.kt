package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.PostDraft

/** Persists the local feed and the current draft. Seeded once from sample data. */
class PostLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "posts.json"
        const val DRAFT_FILE = "draft.json"
    }

    fun seedIfEmpty() {
        if (!store.exists(FILE)) {
            store.writeList(FILE, SampleData.posts)
        }
    }

    fun readPosts(): List<Post> {
        seedIfEmpty()
        return store.readList(FILE)
    }

    fun writePosts(posts: List<Post>) = store.writeList(FILE, posts)

    fun readDraft(): PostDraft? = store.readList<PostDraft>(DRAFT_FILE).firstOrNull()

    fun writeDraft(draft: PostDraft?) {
        store.writeList(DRAFT_FILE, if (draft == null) emptyList() else listOf(draft))
    }
}
