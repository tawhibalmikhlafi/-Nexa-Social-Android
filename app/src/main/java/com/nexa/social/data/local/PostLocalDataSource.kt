package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.PostDraft

/** Keeps the compose draft only. Feed posts are authoritative in Supabase. */
class PostLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "posts.json"
        const val DRAFT_FILE = "draft.json"
    }

    init {
        // Remove legacy demo/local feed posts without touching the saved compose draft.
        store.writeList(FILE, emptyList<Post>())
    }

    fun readPosts(): List<Post> = emptyList()

    fun writePosts(posts: List<Post>) = store.writeList(FILE, posts)

    fun readDraft(): PostDraft? = store.readList<PostDraft>(DRAFT_FILE).firstOrNull()

    fun writeDraft(draft: PostDraft?) {
        store.writeList(DRAFT_FILE, if (draft == null) emptyList() else listOf(draft))
    }
}
