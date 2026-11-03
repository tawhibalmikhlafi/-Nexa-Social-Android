package com.nexa.social.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.ui.components.NexaWordmark
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onReady: (AppStartState) -> Unit,
    viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)
) {
    val state by viewModel.startState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val target = state ?: return@LaunchedEffect
        // Short, brand-only delay; the app starts as soon as the session is known.
        delay(600)
        onReady(target)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NexaWordmark(markSize = 96.dp, showYear = true)
        Spacer(Modifier.height(NexaSpacing.lg))
        Text(
            text = stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(NexaSpacing.xxxl))
        CircularProgressIndicator()
    }
}
