package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.data.sample.SampleData
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.CommunityPost

/** Membership is a local flag; the sample member counts are illustrative, not server values. */
data class CommunityMembershipState(
    val communityId: String,
    val isJoined: Boolean
)

class CommunityLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val MEMBERSHIP_FILE = "community_membership.json"
        const val POSTS_FILE = "community_posts.json"
        const val LOCAL_COMMUNITIES_FILE = "local_communities.json"
        const val DELETED_FILE = "deleted_communities.json"
    }

    fun seedIfEmpty() {
        if (!store.exists(MEMBERSHIP_FILE)) {
            store.writeList(
                MEMBERSHIP_FILE,
                SampleData.communities.map { CommunityMembershipState(it.id, it.isJoinedLocally) }
            )
        }
        if (!store.exists(POSTS_FILE)) {
            store.writeList(POSTS_FILE, SampleData.communityPosts)
        }
        if (!store.exists(LOCAL_COMMUNITIES_FILE)) {
            store.writeList(LOCAL_COMMUNITIES_FILE, emptyList<Community>())
        }
    }

    fun readMembership(): List<CommunityMembershipState> {
        seedIfEmpty()
        return store.readList(MEMBERSHIP_FILE)
    }

    fun writeMembership(states: List<CommunityMembershipState>) = store.writeList(MEMBERSHIP_FILE, states)

    fun readPosts(): List<CommunityPost> {
        seedIfEmpty()
        return store.readList(POSTS_FILE)
    }

    fun writePosts(posts: List<CommunityPost>) = store.writeList(POSTS_FILE, posts)

    fun readLocalCommunities(): List<Community> {
        seedIfEmpty()
        return store.readList(LOCAL_COMMUNITIES_FILE)
    }

    fun writeLocalCommunities(communities: List<Community>) =
        store.writeList(LOCAL_COMMUNITIES_FILE, communities)

    fun readDeletedIds(): List<String> {
        seedIfEmpty()
        return store.readList<DeletedEntry>(DELETED_FILE).map { it.id }
    }

    fun writeDeletedIds(ids: List<String>) {
        store.writeList(DELETED_FILE, ids.map { DeletedEntry(it) })
    }

    data class DeletedEntry(val id: String)
}
