package com.nexa.social.domain.model

/**
 * Audience governing who can see or interact with a piece of content.
 * Ordered from most permissive (EVERYONE) to most restrictive (ONLY_ME).
 */
enum class AudienceType(val wire: String) {
    EVERYONE("everyone"),
    FOLLOWERS("followers"),
    MUTUALS("mutuals"),
    ONLY_ME("only_me");

    companion object {
        fun fromWire(value: String?): AudienceType =
            entries.firstOrNull { it.wire == value } ?: EVERYONE
    }
}

enum class MessagePolicy(val wire: String) {
    EVERYONE("everyone"),
    FOLLOWERS("followers"),
    MUTUALS("mutuals"),
    NOBODY("nobody");

    companion object {
        fun fromWire(value: String?): MessagePolicy =
            entries.firstOrNull { it.wire == value } ?: EVERYONE
    }
}

enum class GroupInvitePolicy(val wire: String) {
    EVERYONE("everyone"),
    FOLLOWERS("followers"),
    NOBODY("nobody");

    companion object {
        fun fromWire(value: String?): GroupInvitePolicy =
            entries.firstOrNull { it.wire == value } ?: EVERYONE
    }
}

/**
 * Full privacy & safety settings set. Stored server-side in [user_privacy_settings]
 * and cached locally in DataStore for offline / guest use.
 */
data class PrivacySettings(
    val privateAccount: Boolean = false,
    val defaultPostAudience: AudienceType = AudienceType.EVERYONE,
    val storyAudience: AudienceType = AudienceType.FOLLOWERS,
    val storyRepliesFrom: AudienceType = AudienceType.FOLLOWERS,
    val storySharingEnabled: Boolean = true,
    val showFollowersList: Boolean = true,
    val showFollowingList: Boolean = true,
    val showActivityStatus: Boolean = true,
    val showReadReceipts: Boolean = true,
    val messagesFrom: MessagePolicy = MessagePolicy.EVERYONE,
    val groupInvitesFrom: GroupInvitePolicy = GroupInvitePolicy.EVERYONE,
    val hideNotificationContent: Boolean = false,
    val whoCanComment: AudienceType = AudienceType.EVERYONE,
    val whoCanReact: AudienceType = AudienceType.EVERYONE,
    val whoCanMention: AudienceType = AudienceType.EVERYONE,
    val mentionApprovalRequired: Boolean = false,
    val showInSearch: Boolean = true,
    val allowSuggestions: Boolean = true,
) {
    val allowMessagesFromEveryone: Boolean get() = messagesFrom == MessagePolicy.EVERYONE
    val showInLocalSearch: Boolean get() = showInSearch

    companion object {
        fun safeDefaults(): PrivacySettings = PrivacySettings()
    }
}
