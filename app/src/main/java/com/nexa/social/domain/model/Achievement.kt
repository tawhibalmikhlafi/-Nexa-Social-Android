package com.nexa.social.domain.model

/**
 * Local achievements. Every unlock is tied to a real, verifiable local event counter
 * (posts, comments, worlds, follows, clips, stories). There is no global ranking server-side.
 */
enum class AchievementId {
    FIRST_POST,
    ACTIVE_CREATOR,
    HELPFUL_VOICE,
    WORLD_EXPLORER,
    WORLD_BUILDER,
    SOCIAL_CONNECTOR,
    CLIP_MAKER,
    STORYTELLER,
    RISING_STAR
}

/** A countable local activity used as the trigger for achievements. */
enum class CounterKind {
    POSTS_CREATED,
    COMMENTS_CREATED,
    WORLDS_JOINED,
    WORLDS_CREATED,
    USERS_FOLLOWED,
    CLIPS_CREATED,
    STORIES_CREATED,
    POINTS_EARNED
}

data class AchievementDefinition(
    val id: AchievementId,
    val counter: CounterKind,
    val target: Int,
    val reward: Int
)

data class UnlockedAchievement(
    val id: AchievementId,
    val unlockedAt: Long
)

data class AchievementSnapshot(
    val counters: Map<CounterKind, Int> = emptyMap(),
    val points: Int = 0,
    val level: Int = 1,
    val unlocked: List<UnlockedAchievement> = emptyList()
) {
    fun progressOf(definition: AchievementDefinition): Int =
        (counters[definition.counter] ?: 0).coerceAtMost(definition.target)

    fun isUnlocked(definition: AchievementDefinition): Boolean =
        unlocked.any { it.id == definition.id }
}
