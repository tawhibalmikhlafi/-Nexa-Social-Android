package com.nexa.social.feature.auth

import com.nexa.social.R
import com.nexa.social.core.common.Validators

/** Presentation-level field feedback built on the shared [Validators]. */
object AuthValidation {

    fun displayNameErrorRes(raw: String): Int? =
        if (raw.isNotBlank() && Validators.displayNameError(raw)) R.string.error_display_name_required else null

    fun usernameErrorRes(raw: String): Int? = when {
        raw.isBlank() -> null
        !Validators.isValidUsername(raw) -> R.string.error_username_invalid
        Validators.isReservedUsername(raw) -> R.string.error_username_reserved
        else -> null
    }

    fun emailErrorRes(raw: String): Int? =
        if (raw.isNotBlank() && !Validators.isValidEmail(raw)) R.string.error_email_invalid else null

    fun passwordErrorRes(raw: String): Int? =
        if (raw.isNotBlank() && !Validators.isValidPassword(raw)) R.string.error_password_short else null

    fun confirmErrorRes(password: String, confirm: String): Int? =
        if (confirm.isNotBlank() && password != confirm) R.string.error_password_mismatch else null
}
