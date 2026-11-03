package com.nexa.social.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nexa.social.R
import com.nexa.social.core.common.FailureReason

/** Maps a repository failure to a localized message id. */
fun failureMessageRes(reason: FailureReason): Int = when (reason) {
    FailureReason.REQUIRED -> R.string.error_required
    FailureReason.DISPLAY_NAME_REQUIRED -> R.string.error_display_name_required
    FailureReason.USERNAME_INVALID -> R.string.error_username_invalid
    FailureReason.USERNAME_RESERVED -> R.string.error_username_reserved
    FailureReason.USERNAME_TAKEN_LOCAL -> R.string.error_username_taken_local
    FailureReason.EMAIL_INVALID -> R.string.error_email_invalid
    FailureReason.PASSWORD_SHORT -> R.string.error_password_short
    FailureReason.PASSWORD_MISMATCH -> R.string.error_password_mismatch
    FailureReason.TERMS_REQUIRED -> R.string.error_terms_required
    FailureReason.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
    FailureReason.INVALID_CODE -> R.string.error_invalid_code
    FailureReason.REQUIRES_ACCOUNT -> R.string.notice_requires_account_body
    FailureReason.NOT_CONFIGURED -> R.string.error_not_configured
    FailureReason.GENERIC -> R.string.error_generic
    FailureReason.EMAIL_IN_USE -> R.string.error_email_in_use
    FailureReason.WEAK_PASSWORD -> R.string.error_weak_password
    FailureReason.TOO_MANY_REQUESTS -> R.string.error_too_many_requests
    FailureReason.NETWORK -> R.string.error_network
    FailureReason.ACCOUNT_DISABLED -> R.string.error_account_disabled
    FailureReason.EMAIL_NOT_VERIFIED -> R.string.error_email_not_verified
    FailureReason.GOOGLE_SIGN_IN_FAILED -> R.string.error_google_sign_in
    FailureReason.CANCELLED -> R.string.error_cancelled
    FailureReason.OPERATION_NOT_ALLOWED -> R.string.error_operation_not_allowed
    FailureReason.NOT_AUTHENTICATED -> R.string.error_not_authenticated
    FailureReason.PERMISSION_DENIED -> R.string.error_permission_denied
    FailureReason.USERNAME_TAKEN -> R.string.error_username_taken
    FailureReason.UPLOAD_FAILED -> R.string.error_upload_failed
    FailureReason.FILE_TOO_LARGE -> R.string.error_file_too_large
    FailureReason.UNSUPPORTED_FILE -> R.string.error_unsupported_file
}

@Composable
fun failureMessage(reason: FailureReason?): String? =
    reason?.let { stringResource(failureMessageRes(it)) }
