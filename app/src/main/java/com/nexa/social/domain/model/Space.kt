package com.nexa.social.domain.model

/**
 * A Nexa Space: a structured learning / discussion area inside a world.
 * Spaces and their resources are stored locally; no cloud upload is performed.
 */
data class Space(
    val id: String,
    val communityId: String,
    val title: String,
    val description: String,
    val category: SpaceCategory,
    val creatorId: String,
    val creatorName: String,
    val createdAt: Long,
    val isMine: Boolean = false,
    val isLocal: Boolean = true,
    val resources: List<SpaceResource> = emptyList(),
    val challenges: List<SpaceChallenge> = emptyList(),
    val discussionCount: Int = 0
)

data class SpaceResource(
    val id: String,
    val title: String,
    val description: String,
    val kind: ResourceKind,
    val reference: String?
)

data class SpaceChallenge(
    val id: String,
    val prompt: String,
    val doneLocally: Boolean = false
)

enum class SpaceCategory {
    PROGRAMMING,
    DESIGN,
    EDUCATION,
    TECH,
    CREATIVE,
    DISCUSSION
}

enum class ResourceKind {
    NOTE,
    LINK,
    IMAGE_REF
}
