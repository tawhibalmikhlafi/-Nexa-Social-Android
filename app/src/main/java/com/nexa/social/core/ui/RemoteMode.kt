package com.nexa.social.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexa.social.NexaApplication

/** True while the active session is backed by Supabase rather than a local account. */
@Composable
fun rememberRemoteMode(): Boolean {
    val app = LocalContext.current.applicationContext as NexaApplication
    val mode by app.container.authRepository.remoteMode
        .collectAsStateWithLifecycle(initialValue = false)
    return mode
}
