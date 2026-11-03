package com.nexa.social.data.repository

import com.nexa.social.domain.model.FeedType
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.repository.CommunityRepository
import com.nexa.social.domain.repository.FeedRepository
import com.nexa.social.domain.repository.PostRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Deterministic feed ranking based on the session's follow graph and joined worlds.
 * This is rule-based ordering, NOT an AI recommender, and it says so in the UI.
 */
class FeedRepositoryImpl(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val communityRepository: CommunityRepository
) : FeedRepository {

    override fun observeFeed(type: FeedType): Flow<List<Post>> = combine(
        postRepository.observeFeed(),
        userRepository.observeFollowingIds(),
        communityRepository.observeJoinedCommunities()
    ) { posts, followingIds, joinedWorlds ->
        rank(posts, type, followingIds, joinedWorlds.map { it.id }.toSet())
    }

    private fun rank(
        posts: List<Post>,
        type: FeedType,
        followingIds: Set<String>,
        joinedWorldIds: Set<String>
    ): List<Post> {
        val pinned = posts.filter { it.pinned }.sortedByDescending { it.createdAt }
        val rest = posts.filterNot { it.pinned }
        val ranked = when (type) {
            FeedType.LATEST -> rest.sortedByDescending { it.createdAt }
            FeedType.ENGAGING -> rest.sortedWith(
                compareByDescending<Post> { it.likeCount + it.commentCount * 2 + it.shareCount * 3 }
                    .thenByDescending { it.createdAt }
            )
            FeedType.WORLDS -> rest.sortedWith(
                compareByDescending<Post> { it.communityId != null && it.communityId in joinedWorldIds }
                    .thenByDescending { it.createdAt }
            )
            FeedType.FOLLOWING -> {
                val followed = rest.filter { it.author.id in followingIds }.sortedByDescending { it.createdAt }
                followed.ifEmpty { rest.sortedByDescending { it.createdAt } }
            }
        }
        return pinned + ranked
    }
}
