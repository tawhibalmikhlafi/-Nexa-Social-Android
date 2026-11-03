package com.nexa.social.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nexa.social.NexaApplication
import com.nexa.social.core.common.AppContainer

/** Provides the single [AppContainer] instance to the composable tree. */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as NexaApplication).container }
}
