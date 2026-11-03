package com.nexa.social.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.NexaPasswordField
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTextButton
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun SignInScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(state.authenticated) {
        if (state.authenticated) onSignedIn()
    }

    val generalError = failureMessage(state.failure)

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.signin_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.xxl, vertical = NexaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.signin_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(NexaSpacing.sm))
            NexaTextField(
                value = username,
                onValueChange = { username = it },
                label = stringResource(
                    if (viewModel.remoteAuthEnabled) {
                        R.string.label_email_or_username
                    } else {
                        R.string.label_username
                    }
                )
            )
            NexaPasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.label_password),
                imeAction = ImeAction.Done
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = remember, onCheckedChange = { remember = it })
                Spacer(Modifier.width(NexaSpacing.xs))
                Text(text = stringResource(R.string.remember_me), style = MaterialTheme.typography.bodyMedium)
            }
            if (generalError != null) {
                AccountRequiredBanner(text = generalError)
            }
            NexaPrimaryButton(
                text = stringResource(R.string.signin_action),
                loading = state.loading,
                onClick = { viewModel.signIn(username, password, remember) }
            )
            if (state.googleAvailable) {
                NexaSecondaryButton(
                    text = stringResource(R.string.auth_continue_with_google),
                    onClick = { viewModel.signInWithGoogle(context) }
                )
            }
            NexaTextButton(
                text = stringResource(R.string.forgot_password),
                onClick = onForgotPassword
            )
            NexaTextButton(
                text = stringResource(R.string.signin_no_account),
                onClick = onSignUp
            )
            Spacer(Modifier.height(NexaSpacing.lg))
        }
    }
}
