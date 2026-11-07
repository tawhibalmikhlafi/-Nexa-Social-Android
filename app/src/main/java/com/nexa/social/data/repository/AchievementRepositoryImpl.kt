package com.nexa.social.data.repository

import com.nexa.social.data.local.AchievementLocalDataSource
import com.nexa.social.data.remote.SupabaseProfileExperienceDataSource
import java.time.Instant
import com.nexa.social.domain.model.AchievementDefinition
import com.nexa.social.domain.model.AchievementId
import com.nexa.social.domain.model.AchievementSnapshot
import com.nexa.social.domain.model.CounterKind
import com.nexa.social.domain.model.UnlockedAchievement
import com.nexa.social.domain.repository.AchievementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Builds a local achievement/points state from real local activity counters.
 * It never awards points without a matching recorded event and never claims a global ranking.
 */
class AchievementRepositoryImpl(
    private val local: AchievementLocalDataSource,
    private val remote: SupabaseProfileExperienceDataSource? = null,
    private val currentUid: () -> String? = { null }
) : AchievementRepository {

    private val _snapshot = MutableStateFlow(if (remote == null) initial() else AchievementSnapshot())
    override fun observeSnapshot(): Flow<AchievementSnapshot> = _snapshot.asStateFlow()

    override suspend fun record(kind: CounterKind, amount: Int) {
        val remoteSource = remote
        if (remoteSource != null) {
            val uid = currentUid() ?: return
            val rows = remoteSource.loadRows("nexa_profile_achievements", uid)
            val progressByCode = rows.associateBy(
                { it.get("achievement_code")?.asString.orEmpty() },
                { it }
            )
            fun progress(code: String): Int = progressByCode[code]?.get("progress")?.asInt ?: 0
            val counters = mapOf(
                CounterKind.POSTS_CREATED to progress("ACTIVE_CREATOR"),
                CounterKind.COMMENTS_CREATED to progress("HELPFUL_VOICE"),
                CounterKind.WORLDS_JOINED to progress("WORLD_EXPLORER"),
                CounterKind.WORLDS_CREATED to progress("WORLD_BUILDER"),
                CounterKind.USERS_FOLLOWED to progress("SOCIAL_CONNECTOR"),
                CounterKind.CLIPS_CREATED to progress("CLIP_MAKER"),
                CounterKind.STORIES_CREATED to progress("STORYTELLER")
            )
            val unlocked = rows.mapNotNull { row ->
                val code = row.get("achievement_code")?.asString ?: return@mapNotNull null
                val id = runCatching { AchievementId.valueOf(code) }.getOrNull() ?: return@mapNotNull null
                val timestamp = row.get("unlocked_at")?.takeIf { !it.isJsonNull }?.asString
                    ?: return@mapNotNull null
                val millis = runCatching { Instant.parse(timestamp).toEpochMilli() }.getOrDefault(0L)
                UnlockedAchievement(id, millis)
            }
            val points = rows.filter { it.get("unlocked_at")?.takeIf { v -> !v.isJsonNull } != null }
                .sumOf { it.get("reward_points")?.asInt ?: 0 }
            _snapshot.value = AchievementSnapshot(
                counters = counters,
                points = points,
                level = 1 + points / pointsPerLevel,
                unlocked = unlocked
            )
            return
        }
        val current = _snapshot.value
        val updatedCounters = current.counters + (kind to ((current.counters[kind] ?: 0) + amount))
        val recomputed = recompute(updatedCounters, current.unlocked)
        _snapshot.value = recomputed
        withContext(Dispatchers.IO) { local.writeSnapshot(recomputed) }
    }

    private fun initial(): AchievementSnapshot =
        recompute(local.readSnapshot().counters, local.readSnapshot().unlocked)

    private fun recompute(
        counters: Map<CounterKind, Int>,
        unlocked: List<UnlockedAchievement>
    ): AchievementSnapshot {
        var points = counters.entries.sumOf { (kind, count) -> count * (pointsPerEvent[kind] ?: 0) }
        val unlockedMutable = unlocked.toMutableList()
        val now = System.currentTimeMillis()

        var changed: Boolean
        do {
            changed = false
            definitions.forEach { def ->
                val reached = (counters[def.counter] ?: 0) >= def.target
                if (reached && unlockedMutable.none { it.id == def.id }) {
                    unlockedMutable += UnlockedAchievement(def.id, now)
                    points += def.reward
                    changed = true
                }
            }
        } while (changed)

        if (points >= pointsAchievementTarget && unlockedMutable.none { it.id == AchievementId.RISING_STAR }) {
            unlockedMutable += UnlockedAchievement(AchievementId.RISING_STAR, now)
        }

        return AchievementSnapshot(
            counters = counters,
            points = points,
            level = 1 + points / pointsPerLevel,
            unlocked = unlockedMutable
        )
    }

    companion object {
        const val pointsPerLevel = 100
        const val pointsAchievementTarget = 150

        private val pointsPerEvent = mapOf(
            CounterKind.POSTS_CREATED to 5,
            CounterKind.COMMENTS_CREATED to 2,
            CounterKind.WORLDS_JOINED to 3,
            CounterKind.WORLDS_CREATED to 8,
            CounterKind.USERS_FOLLOWED to 2,
            CounterKind.CLIPS_CREATED to 6,
            CounterKind.STORIES_CREATED to 4
        )

        val definitions: List<AchievementDefinition> = listOf(
            AchievementDefinition(AchievementId.FIRST_POST, CounterKind.POSTS_CREATED, 1, 10),
            AchievementDefinition(AchievementId.ACTIVE_CREATOR, CounterKind.POSTS_CREATED, 10, 50),
            AchievementDefinition(AchievementId.HELPFUL_VOICE, CounterKind.COMMENTS_CREATED, 5, 25),
            AchievementDefinition(AchievementId.WORLD_EXPLORER, CounterKind.WORLDS_JOINED, 3, 30),
            AchievementDefinition(AchievementId.WORLD_BUILDER, CounterKind.WORLDS_CREATED, 1, 40),
            AchievementDefinition(AchievementId.SOCIAL_CONNECTOR, CounterKind.USERS_FOLLOWED, 5, 35),
            AchievementDefinition(AchievementId.CLIP_MAKER, CounterKind.CLIPS_CREATED, 1, 20),
            AchievementDefinition(AchievementId.STORYTELLER, CounterKind.STORIES_CREATED, 1, 15)
        )
    }
}
