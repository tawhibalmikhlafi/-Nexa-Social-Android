package com.nexa.social.domain.model

import com.nexa.social.core.common.FailureReason

/** Thrown by the remote post path so the use case can map upload/write failures to the UI. */
class PostCreationException(
    val reason: FailureReason,
    val details: String? = null
) : Exception(
    buildString {
        append("post creation failed: ")
        append(reason)
        if (!details.isNullOrBlank()) {
            append(" (")
            append(details.take(500))
            append(")")
        }
    }
)
