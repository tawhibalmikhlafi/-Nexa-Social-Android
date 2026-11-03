package com.nexa.social.domain.usecase

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Validators
import com.nexa.social.domain.repository.UserRepository

/**
 * Validates a username against the shared rules and local uniqueness.
 * Returns null when acceptable, otherwise the reason to display.
 */
class ValidateUsernameUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(raw: String): FailureReason? {
        if (!Validators.isValidUsername(raw)) return FailureReason.USERNAME_INVALID
        if (Validators.isReservedUsername(raw)) return FailureReason.USERNAME_RESERVED
        if (userRepository.usernameTakenLocally(raw)) return FailureReason.USERNAME_TAKEN_LOCAL
        return null
    }
}
