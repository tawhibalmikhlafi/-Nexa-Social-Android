package com.nexa.social.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/** Shared snackbar host so any screen can surface short, localized messages. */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }
