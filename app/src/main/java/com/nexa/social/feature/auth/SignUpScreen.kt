package com.nexa.social.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.domain.model.SignUpRequest
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.LegalDialog
import com.nexa.social.ui.components.NexaPasswordField
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.NexaTextButton
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onAccountCreated: () -> Unit,
    onSignedIn: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var legalDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.authenticated) {
        if (state.authenticated) {
            if (viewModel.remoteAuthEnabled) onSignedIn() else onAccountCreated()
        }
    }
    LaunchedEffect(state.awaitingEmailVerification) {
        if (state.awaitingEmailVerification) onAccountCreated()
    }

    val usernameError = AuthValidation.usernameErrorRes(username)?.let { stringResource(it) }
    val emailError = AuthValidation.emailErrorRes(email)?.let { stringResource(it) }
    val passwordError = AuthValidation.passwordErrorRes(password)?.let { stringResource(it) }
    val confirmError = AuthValidation.confirmErrorRes(password, confirm)?.let { stringResource(it) }
    val generalError = failureMessage(state.failure)

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.signup_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.xxl, vertical = NexaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.signup_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NexaTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = stringResource(R.string.label_display_name)
            )
            NexaTextField(
                value = username,
                onValueChange = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                label = stringResource(R.string.label_username),
                placeholder = stringResource(R.string.hint_username),
                errorText = usernameError
            )
            NexaTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = stringResource(R.string.label_email),
                placeholder = stringResource(R.string.hint_email),
                errorText = emailError,
                keyboardType = KeyboardType.Email
            )
            NexaPasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.label_password),
                errorText = passwordError
            )
            NexaPasswordField(
                value = confirm,
                onValueChange = { confirm = it },
                label = stringResource(R.string.label_confirm_password),
                errorText = confirmError,
                imeAction = ImeAction.Done
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                Spacer(Modifier.width(NexaSpacing.xs))
                Text(
                    text = stringResource(R.string.agree_terms_prefix),
                    style = MaterialTheme.typography.bodyMedium
                )
                NexaTextButton(
                    text = stringResource(R.string.agree_terms_link),
                    onClick = { legalDialog = "terms" }
                )
                Text(text = stringResource(R.string.agree_and), style = MaterialTheme.typography.bodyMedium)
                NexaTextButton(
                    text = stringResource(R.string.agree_privacy_link),
                    onClick = { legalDialog = "privacy" }
                )
            }

            if (generalError != null) {
                AccountRequiredBanner(text = generalError)
            }

            NexaPrimaryButton(
                text = stringResource(R.string.signup_action),
                loading = state.loading,
                onClick = {
                    viewModel.signUp(
                        SignUpRequest(
                            displayName = displayName,
                            username = username,
                            email = email,
                            password = password,
                            confirmPassword = confirm,
                            acceptedTerms = accepted
                        )
                    )
                }
            )
            if (state.googleAvailable) {
                NexaSecondaryButton(
                    text = stringResource(R.string.auth_continue_with_google),
                    onClick = { viewModel.signInWithGoogle(context) }
                )
            }
            NexaTextButton(
                text = stringResource(R.string.signup_have_account),
                onClick = onSignIn
            )
            Spacer(Modifier.height(NexaSpacing.lg))
        }
    }

    legalDialog?.let { which ->
        LegalDialog(
            title = stringResource(if (which == "terms") R.string.settings_terms else R.string.settings_privacy_policy),
            body = stringResource(if (which == "terms") R.string.settings_terms_body else R.string.settings_privacy_policy_body),
            onDismiss = { legalDialog = null }
        )
    }
}
