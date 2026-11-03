package com.nexa.social.core.common

/**
 * Input validation shared by the auth and profile flows.
 * The rules mirror the future server-side rules so the UI does not have to change later.
 */
object Validators {

    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 20
    const val PASSWORD_MIN = 8

    // Letters, numbers and underscore only, must start with a letter.
    private val usernameRegex = Regex("^[A-Za-z][A-Za-z0-9_]{${USERNAME_MIN - 1},${USERNAME_MAX - 1}}$")
    private val emailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    /** Reserved names that must not be claimable even locally. */
    val reservedUsernames: Set<String> = setOf(
        "admin", "administrator", "root", "nexa", "support", "official",
        "system", "help", "helpdesk", "moderator", "mod", "staff", "team",
        "null", "undefined", "api", "www", "mail", "security", "about", "guest"
    )

    /** Canonical form used for uniqueness comparison (case-insensitive, ignores a leading @). */
    fun canonicalUsername(raw: String): String = raw.trim().removePrefix("@").lowercase()

    fun isValidUsername(raw: String): Boolean = usernameRegex.matches(raw.trim())

    fun isReservedUsername(raw: String): Boolean = canonicalUsername(raw) in reservedUsernames

    fun isValidEmail(raw: String): Boolean = emailRegex.matches(raw.trim())

    fun isValidPassword(raw: String): Boolean = raw.length >= PASSWORD_MIN

    fun displayNameError(raw: String): Boolean = raw.trim().isEmpty()
}
