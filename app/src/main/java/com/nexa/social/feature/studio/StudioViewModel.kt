package com.nexa.social.feature.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.domain.model.AchievementSnapshot
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.User
import com.nexa.social.domain.repository.AchievementRepository
import com.nexa.social.domain.repository.PostRepository
import com.nexa.social.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class StudioStats(
    val user: User? = null,
    val posts: Int = 0,
    val likes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
    val engagement: Int = 0,
    val averageEngagement: Int = 0,
    val bestPost: Post? = null,
    val topTags: List<String> = emptyList(),
    val streakDays: Int = 0,
    val nexaScore: Int = 0,
    val level: Int = 1
)

/** Real, locally-derived creator analytics. No sample numbers are injected here. */
@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(
    userRepository: UserRepository,
    postRepository: PostRepository,
    achievementRepository: AchievementRepository
) : ViewModel() {

    private val myPosts = userRepository.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList()) else postRepository.observePostsByAuthor(user.id)
    }

    val stats: StateFlow<StudioStats> = combine(
        userRepository.currentUser,
        myPosts,
        achievementRepository.observeSnapshot()
    ) { user, posts, achievements ->
        compute(user, posts, achievements)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudioStats())

    private fun compute(
        user: User?,
        posts: List<Post>,
        achievements: AchievementSnapshot
    ): StudioStats {
        if (user == null) return StudioStats()
        val likes = posts.sumOf { it.likeCount }
        val comments = posts.sumOf { it.commentCount }
        val shares = posts.sumOf { it.shareCount }
        val engagement = likes + comments + shares
        val nexaScore = user.followersCount + posts.size * 10 + likes * 2 +
            comments * 3 + achievements.level * 25
        return StudioStats(
            user = user,
            posts = posts.size,
            likes = likes,
            comments = comments,
            shares = shares,
            engagement = engagement,
            averageEngagement = if (posts.isEmpty()) 0 else engagement / posts.size,
            bestPost = posts.maxByOrNull { it.likeCount + it.commentCount * 2 + it.shareCount * 3 },
            topTags = posts.flatMap { it.tags }
                .groupingBy { it.lowercase() }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(8)
                .map { it.key },
            streakDays = streak(posts),
            nexaScore = nexaScore,
            level = achievements.level
        )
    }

    private fun streak(posts: List<Post>): Int {
        if (posts.isEmpty()) return 0
        val zone = ZoneId.systemDefault()
        val days = posts
            .map { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .distinct()
            .sortedDescending()
        val today = LocalDate.now(zone)
        val newest = days.first()
        if (ChronoUnit.DAYS.between(newest, today) > 1) return 0
        var streak = 1
        var cursor = newest
        for (day in days.drop(1)) {
            if (ChronoUnit.DAYS.between(day, cursor) == 1L) {
                streak += 1
                cursor = day
            } else {
                break
            }
        }
        return streak
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = nexaApplication().container
                StudioViewModel(
                    userRepository = container.userRepository,
                    postRepository = container.postRepository,
                    achievementRepository = container.achievementRepository
                )
            }
        }
    }
}
