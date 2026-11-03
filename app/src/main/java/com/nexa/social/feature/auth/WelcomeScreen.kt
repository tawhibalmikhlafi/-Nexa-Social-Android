package com.nexa.social.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTextButton
import com.nexa.social.ui.components.NexaWordmark
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onContinue: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.guest) {
        if (state.guest) onContinue()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NexaSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NexaWordmark(markSize = 76.dp, showYear = true)
        Spacer(Modifier.height(NexaSpacing.xxl))
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(NexaSpacing.sm))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(NexaSpacing.xxxl))
        NexaPrimaryButton(
            text = stringResource(R.string.signin_action),
            onClick = onSignIn,
            icon = Icons.AutoMirrored.Filled.Login
        )
        Spacer(Modifier.height(NexaSpacing.md))
        NexaSecondaryButton(
            text = stringResource(R.string.signup_action),
            onClick = onSignUp,
            icon = Icons.Filled.PersonAdd
        )
        Spacer(Modifier.height(NexaSpacing.lg))
        NexaTextButton(
            text = stringResource(R.string.skip_login),
            onClick = { viewModel.continueAsGuest() }
        )
        Spacer(Modifier.height(NexaSpacing.lg))
        if (!viewModel.remoteAuthEnabled) {
            LocalDataBanner(text = stringResource(R.string.notice_local_account))
        }
    }
}
