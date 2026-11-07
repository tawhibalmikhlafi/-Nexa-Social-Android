package com.nexa.social.domain.model

/**
 * Public, shareable representation of an account.
 * It never carries credentials: passwords live only in the local credential store.
 */
data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val email: String?,
    val emailVerification: EmailVerificationStatus,
    val avatarUri: String?,
    val coverUri: String?,
    val bio: String,
    val createdAt: Long,
    val accountStatus: AccountStatus,
    val accountOrigin: AccountOrigin,
    val followersCount: Int,
    val followingCount: Int,
    val isFollowingLocally: Boolean = false,
    val city: String? = null,
    val website: String? = null,
    val isPrivateAccount: Boolean = false,
    val placeOfOrigin: String? = null,
    val currentResidence: String? = null,
    /** ISO-8601 calendar date (yyyy-MM-dd), independent of device timezone. */
    val birthDate: String? = null,
    val skills: List<String> = emptyList(),
    val hobbies: List<String> = emptyList()
)

/** Lightweight author reference embedded in feeds and messages. */
data class UserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUri: String?,
    val accountOrigin: AccountOrigin
) {
    companion object {
        fun from(user: User): UserSummary = UserSummary(
            id = user.id,
            username = user.username,
            displayName = user.displayName,
            avatarUri = user.avatarUri,
            accountOrigin = user.accountOrigin
        )
    }
}

/**
 * Email verification lifecycle.
 * [LOCALLY_CONFIRMED] only exists for development builds without a server.
 */
enum class EmailVerificationStatus {
    NOT_VERIFIED,
    LOCALLY_CONFIRMED,
    VERIFIED
}

/**
 * Where an account comes from. The server integration will add a remote origin.
 */
enum class AccountOrigin {
    /** Bundled sample account shipped with the app for local exploration. */
    SAMPLE,

    /** Account created on this device, stored locally only. */
    LOCAL_ACCOUNT,

    /** Account authenticated and stored with Supabase. */
    REMOTE_ACCOUNT,

    /** The anonymous guest session (no account exists). */
    GUEST
}

enum class AccountStatus {
    ACTIVE,
    PENDING_VERIFICATION,
    SUSPENDED,
    DELETED
}

/** Identity used for content created while browsing as a guest. No account backs it. */
object GuestIdentity {
    const val ID = "guest"
    const val USERNAME = "guest"

    val summary = UserSummary(
        id = ID,
        username = USERNAME,
        displayName = "Guest",
        avatarUri = null,
        accountOrigin = AccountOrigin.GUEST
    )
}
