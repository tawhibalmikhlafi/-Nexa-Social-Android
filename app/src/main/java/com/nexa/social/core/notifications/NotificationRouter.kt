package com.nexa.social.core.notifications

import android.content.Intent
import com.nexa.social.domain.model.NotificationTargetKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a pending navigation request from a push notification tap to the Compose navigator.
 * MainActivity feeds intents here; NexaRoot consumes the value once.
 */
object NotificationRouter {

    data class Target(val kind: NotificationTargetKind, val id: String)

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target.asStateFlow()

    fun handleIntent(intent: Intent?) {
        // Push payloads come from outside the app: never let an unexpected extra type crash
        // the launcher activity while it is resolving a notification tap.
        runCatching {
            val extras = intent?.extras ?: return
            val rawKind = extras.getString(EXTRA_TARGET_KIND) ?: extras.getString(EXTRA_KIND)
            val id = extras.getString(EXTRA_TARGET_ID)
                ?: extras.getString(EXTRA_ID)
                ?: extras.getString(EXTRA_POST_ID)
            if (rawKind.isNullOrBlank() || id.isNullOrBlank()) return
            val kind = NotificationTargetKind.valueOf(rawKind.trim().uppercase())
            if (kind == NotificationTargetKind.NONE) return
            _target.value = Target(kind, id)
        }
    }

    fun consume() {
        _target.value = null
    }

    const val EXTRA_TARGET_KIND = "targetKind"
    const val EXTRA_TARGET_ID = "targetId"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "id"
    const val EXTRA_POST_ID = "postId"
}
