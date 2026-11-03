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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.NexaTextButton
import com.nexa.social.ui.components.ServerPendingBanner
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onBackToSignIn: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }

    val generalError = failureMessage(state.failure)

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.forgot_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NexaSpacing.xxl, vertical = NexaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            Text(
                text = stringResource(
                    if (viewModel.remoteAuthEnabled) {
                        R.string.forgot_supabase_subtitle
                    } else {
                        R.string.forgot_subtitle
                    }
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!viewModel.remoteAuthEnabled) {
                ServerPendingBanner(text = stringResource(R.string.forgot_local_notice))
            }
            NexaTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = stringResource(R.string.label_email),
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            )
            if (state.resetRequested) {
                Text(
                    text = stringResource(
                        if (viewModel.remoteAuthEnabled) {
                            R.string.forgot_sent_email
                        } else {
                            R.string.forgot_sent_local
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (generalError != null) {
                AccountRequiredBanner(text = generalError)
            }
            NexaPrimaryButton(
                text = stringResource(R.string.forgot_action),
                loading = state.loading,
                onClick = { viewModel.requestPasswordReset(email) }
            )
            Spacer(Modifier.height(NexaSpacing.xs))
            NexaTextButton(
                text = stringResource(R.string.action_back),
                onClick = onBackToSignIn,
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
        }
    }
}
