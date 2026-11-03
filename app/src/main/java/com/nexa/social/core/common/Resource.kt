package com.nexa.social.core.common

/** Minimal result wrapper for repository operations that can fail. */
sealed interface Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>
    data class Failure(val reason: FailureReason) : Resource<Nothing>
    data object Loading : Resource<Nothing>
}

/** Reasons surfaced to the UI as localized messages. */
enum class FailureReason {
    REQUIRED,
    DISPLAY_NAME_REQUIRED,
    USERNAME_INVALID,
    USERNAME_RESERVED,
    USERNAME_TAKEN_LOCAL,
    EMAIL_INVALID,
    PASSWORD_SHORT,
    PASSWORD_MISMATCH,
    TERMS_REQUIRED,
    INVALID_CREDENTIALS,
    INVALID_CODE,
    REQUIRES_ACCOUNT,
    NOT_CONFIGURED,
    GENERIC,

    // Authentication
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    TOO_MANY_REQUESTS,
    NETWORK,
    ACCOUNT_DISABLED,
    EMAIL_NOT_VERIFIED,
    GOOGLE_SIGN_IN_FAILED,
    CANCELLED,
    OPERATION_NOT_ALLOWED,
    NOT_AUTHENTICATED,
    PERMISSION_DENIED,
    USERNAME_TAKEN,

    // Media uploads
    UPLOAD_FAILED,
    FILE_TOO_LARGE,
    UNSUPPORTED_FILE
}
