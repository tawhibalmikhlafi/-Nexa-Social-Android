package com.nexa.social.feature.pulse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.repository.CommunityRepository
import com.nexa.social.domain.repository.PostRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class PulseTag(
    val name: String,
    val posts: Int,
    val engagement: Int
)

data class PulseCreator(
    val user: UserSummary,
    val posts: Int,
    val engagement: Int
)

data class PulseWorld(
    val community: Community,
    val posts: Int
)

data class PulseSnapshot(
    val totalPosts: Int = 0,
    val activeToday: Int = 0,
    val tags: List<PulseTag> = emptyList(),
    val creators: List<PulseCreator> = emptyList(),
    val worlds: List<PulseWorld> = emptyList()
)

/**
 * Pulse is a local signal processor over the real feed. It never invents trends or remote
 * statistics; every number is derived from posts already present on this device.
 */
class PulseViewModel(
    postRepository: PostRepository,
    communityRepository: CommunityRepository
) : ViewModel() {

    val snapshot: StateFlow<PulseSnapshot> = combine(
        postRepository.observeFeed(),
        communityRepository.observeCommunities()
    ) { posts, communities ->
        compute(posts, communities)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PulseSnapshot())

    private fun compute(posts: List<Post>, communities: List<Community>): PulseSnapshot {
        if (posts.isEmpty()) return PulseSnapshot()

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val tagScores = mutableMapOf<String, Pair<Int, Int>>()
        posts.forEach { post ->
            val engagement = engagementOf(post)
            val tags = (post.tags + hashtagsOf(post.text)).distinct()
            tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.forEach { tag ->
                val current = tagScores[tag] ?: (0 to 0)
                tagScores[tag] = (current.first + 1) to (current.second + engagement)
            }
        }

        val creatorScores = posts
            .groupBy { it.author.id }
            .map { (_, authored) ->
                PulseCreator(
                    user = authored.first().author,
                    posts = authored.size,
                    engagement = authored.sumOf { engagementOf(it) }
                )
            }
            .sortedWith(
                compareByDescending<PulseCreator> { it.engagement }
                    .thenByDescending { it.posts }
            )
            .take(5)

        val postCounts = posts.groupBy { it.communityId }.mapValues { it.value.size }
        val worldScores = communities
            .map { PulseWorld(it, postCounts[it.id] ?: 0) }
            .sortedWith(
                compareByDescending<PulseWorld> { it.posts }
                    .thenByDescending { it.community.sampleMemberCount }
            )
            .take(5)

        return PulseSnapshot(
            totalPosts = posts.size,
            activeToday = posts.count { it.createdAt >= startOfDay },
            tags = tagScores.entries
                .map { PulseTag(it.key, it.value.first, it.value.second) }
                .sortedWith(
                    compareByDescending<PulseTag> { it.engagement }
                        .thenByDescending { it.posts }
                )
                .take(12),
            creators = creatorScores,
            worlds = worldScores
        )
    }

    private fun engagementOf(post: Post): Int =
        post.likeCount + post.commentCount * 2 + post.shareCount * 3

    private fun hashtagsOf(text: String): List<String> =
        Regex("#([\\p{L}\\p{N}_]{2,30})")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                PulseViewModel(
                    postRepository = container.postRepository,
                    communityRepository = container.communityRepository
                )
            }
        }
    }
}
