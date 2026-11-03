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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.NexaPasswordField
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.theme.NexaSpacing

/**
 * Shown after a Supabase password-recovery deep link has already been validated. The session is
 * signed out again once the password changes so the user signs in with the new credential.
 */
@Composable
fun ResetPasswordScreen(
    onDone: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
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
            text = stringResource(R.string.reset_password_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.reset_password_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (state.passwordResetDone) {
            Text(
                text = stringResource(R.string.reset_password_success),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            NexaPrimaryButton(
                text = stringResource(R.string.reset_password_go_sign_in),
                onClick = onDone
            )
            return@Column
        }

        NexaPasswordField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.label_password),
            imeAction = ImeAction.Next
        )
        NexaPasswordField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = stringResource(R.string.label_confirm_password),
            imeAction = ImeAction.Done
        )
        if (generalError != null) {
            AccountRequiredBanner(text = generalError)
        }
        NexaPrimaryButton(
            text = stringResource(R.string.reset_password_action),
            loading = state.loading,
            onClick = { viewModel.resetPassword(password, confirmation) }
        )
    }
}
