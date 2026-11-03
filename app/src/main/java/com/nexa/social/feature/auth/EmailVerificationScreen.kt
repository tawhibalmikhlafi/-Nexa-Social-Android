package com.nexa.social.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTextButton
import com.nexa.social.ui.components.ServerPendingBanner
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun EmailVerificationScreen(
    onVerified: () -> Unit,
    onSkip: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.verifiedLocally) {
        if (state.verifiedLocally) onVerified()
    }
    val generalError = failureMessage(state.failure)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NexaSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
    ) {
        Spacer(Modifier.height(NexaSpacing.xxxl))
        Text(
            text = stringResource(R.string.verify_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(
                if (viewModel.remoteAuthEnabled) {
                    R.string.verify_supabase_subtitle
                } else {
                    R.string.verify_subtitle
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (viewModel.remoteAuthEnabled) {
            if (state.verificationSent) {
                Text(
                    text = stringResource(R.string.verify_email_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            if (generalError != null) {
                AccountRequiredBanner(text = generalError)
            }
            NexaPrimaryButton(
                text = stringResource(R.string.verify_sign_in),
                loading = state.loading,
                onClick = onSignIn
            )
            NexaTextButton(
                text = stringResource(R.string.verify_resend_email),
                onClick = { viewModel.sendVerificationEmail() }
            )
            NexaTextButton(
                text = stringResource(R.string.verify_skip),
                onClick = onSkip
            )
            return@Column
        }

        ServerPendingBanner(text = stringResource(R.string.verify_local_notice))

        LaunchedEffect(Unit) {
            if (state.verificationCode == null) viewModel.generateVerificationCode()
        }

        val devCode = state.verificationCode
        if (devCode != null) {
            Text(
                text = devCode,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        NexaTextField(
            value = code,
            onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
            label = stringResource(R.string.verify_code_label),
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        )

        if (generalError != null) {
            AccountRequiredBanner(text = generalError)
        }

        NexaPrimaryButton(
            text = stringResource(R.string.verify_action),
            onClick = { viewModel.verifyCode(code) }
        )
        NexaTextButton(
            text = stringResource(R.string.verify_resend),
            onClick = { viewModel.generateVerificationCode() }
        )
        NexaTextButton(
            text = stringResource(R.string.verify_skip),
            onClick = onSkip
        )
    }
}
