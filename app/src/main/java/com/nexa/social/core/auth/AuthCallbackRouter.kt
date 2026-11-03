package com.nexa.social.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges the Android deep-link callback (Activity) with Compose navigation.
 *
 * A password-recovery link creates a valid Supabase session, so the user must be taken to the
 * "new password" screen instead of the home screen.
 */
object AuthCallbackRouter {

    private val _passwordResetRequired = MutableStateFlow(false)
    val passwordResetRequired = _passwordResetRequired.asStateFlow()

    fun requirePasswordReset() {
        _passwordResetRequired.value = true
    }

    fun consumePasswordReset() {
        _passwordResetRequired.value = false
    }
}
