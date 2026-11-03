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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.rememberImagePicker
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun ProfileSetupScreen(
    onCompleted: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var displayName by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var avatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var prefilled by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        val user = currentUser
        if (!prefilled && user != null) {
            displayName = user.displayName
            bio = user.bio
            prefilled = true
        }
    }

    LaunchedEffect(state.profileCompleted) {
        if (state.profileCompleted) onCompleted()
    }

    val pickImage = rememberImagePicker { uri -> avatarUri = uri?.toString() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NexaSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
    ) {
        Spacer(Modifier.height(NexaSpacing.xxl))
        Text(
            text = stringResource(R.string.profile_setup_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.profile_setup_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NexaAvatar(
            displayName = displayName.ifBlank { "N" },
            avatarUri = avatarUri,
            size = 96.dp
        )
        NexaSecondaryButton(
            text = stringResource(R.string.profile_setup_avatar),
            onClick = pickImage
        )
        NexaTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = stringResource(R.string.label_display_name)
        )
        NexaTextField(
            value = bio,
            onValueChange = { bio = it },
            label = stringResource(R.string.label_bio_optional),
            singleLine = false,
            imeAction = ImeAction.Done
        )
        LocalDataBanner(text = stringResource(R.string.notice_email_not_verified))
        NexaPrimaryButton(
            text = stringResource(R.string.profile_setup_action),
            loading = state.loading,
            enabled = displayName.isNotBlank(),
            onClick = { viewModel.saveProfile(displayName, bio, avatarUri) }
        )
    }
}
