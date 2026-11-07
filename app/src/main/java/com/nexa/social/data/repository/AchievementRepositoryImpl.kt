package com.nexa.social.data.repository

import com.nexa.social.data.remote.SupabaseProfileExperienceDataSource
import com.nexa.social.domain.model.AchievementDefinition
import com.nexa.social.domain.model.AchievementId
import com.nexa.social.domain.model.AchievementSnapshot
import com.nexa.social.domain.model.CounterKind
import com.nexa.social.domain.model.UnlockedAchievement
import com.nexa.social.domain.repository.AchievementRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Supabase is the source of truth. The database awards badges from actual inserted activity rows;
 * this repository only reads the server-calculated progress and unlock timestamps.
 */
class AchievementRepositoryImpl(
    private val remote: SupabaseProfileExperienceDataSource?,
    private val currentUid: () -> String?
) : AchievementRepository {

    private val _snapshot = MutableStateFlow(AchievementSnapshot())
    override fun observeSnapshot(): Flow<AchievementSnapshot> = _snapshot.asStateFlow()

    override suspend fun record(kind: CounterKind, amount: Int) {
        val source = remote ?: return
        val uid = currentUid() ?: return
        val rows = source.loadRows("nexa_profile_achievements", uid)
        val byCode = rows.associateBy { it.get("achievement_code")?.asString.orEmpty() }
        fun progress(code: String): Int = byCode[code]?.get("progress")?.asInt ?: 0

        val unlocked = rows.mapNotNull { row ->
            val code = row.get("achievement_code")?.asString ?: return@mapNotNull null
            val id = runCatching { AchievementId.valueOf(code) }.getOrNull() ?: return@mapNotNull null
            val timestamp = row.get("unlocked_at")?.takeIf { !it.isJsonNull }?.asString
                ?: return@mapNotNull null
            val millis = runCatching { Instant.parse(timestamp).toEpochMilli() }.getOrDefault(0L)
            UnlockedAchievement(id, millis)
        }
        val points = rows.filter { it.get("unlocked_at")?.takeIf { value -> !value.isJsonNull } != null }
            .sumOf { it.get("reward_points")?.asInt ?: 0 }
        _snapshot.value = AchievementSnapshot(
            counters = mapOf(
                CounterKind.POSTS_CREATED to progress("ACTIVE_CREATOR"),
                CounterKind.COMMENTS_CREATED to progress("HELPFUL_VOICE"),
                CounterKind.WORLDS_JOINED to progress("WORLD_EXPLORER"),
                CounterKind.WORLDS_CREATED to progress("WORLD_BUILDER"),
                CounterKind.USERS_FOLLOWED to progress("SOCIAL_CONNECTOR"),
                CounterKind.CLIPS_CREATED to progress("CLIP_MAKER"),
                CounterKind.STORIES_CREATED to progress("STORYTELLER"),
                CounterKind.POINTS_EARNED to points
            ),
            points = points,
            level = 1 + points / pointsPerLevel,
            unlocked = unlocked
        )
    }

    companion object {
        const val pointsPerLevel = 100
        val definitions: List<AchievementDefinition> = listOf(
            AchievementDefinition(AchievementId.FIRST_POST, CounterKind.POSTS_CREATED, 1, 10),
            AchievementDefinition(AchievementId.ACTIVE_CREATOR, CounterKind.POSTS_CREATED, 10, 50),
            AchievementDefinition(AchievementId.HELPFUL_VOICE, CounterKind.COMMENTS_CREATED, 5, 25),
            AchievementDefinition(AchievementId.WORLD_EXPLORER, CounterKind.WORLDS_JOINED, 3, 30),
            AchievementDefinition(AchievementId.WORLD_BUILDER, CounterKind.WORLDS_CREATED, 1, 40),
            AchievementDefinition(AchievementId.SOCIAL_CONNECTOR, CounterKind.USERS_FOLLOWED, 5, 35),
            AchievementDefinition(AchievementId.CLIP_MAKER, CounterKind.CLIPS_CREATED, 1, 20),
            AchievementDefinition(AchievementId.STORYTELLER, CounterKind.STORIES_CREATED, 1, 15),
            AchievementDefinition(AchievementId.RISING_STAR, CounterKind.POINTS_EARNED, 150, 0)
        )
    }
}
