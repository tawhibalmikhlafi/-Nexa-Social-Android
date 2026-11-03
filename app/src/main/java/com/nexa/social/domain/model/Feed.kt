package com.nexa.social.domain.model

/**
 * Feed orderings. These are deterministic local rankings, not a machine-learning recommender.
 * A networked recommendation service can replace this behind [com.nexa.social.domain.repository.FeedRepository].
 */
enum class FeedType {
    LATEST,
    ENGAGING,
    WORLDS,
    FOLLOWING
}
