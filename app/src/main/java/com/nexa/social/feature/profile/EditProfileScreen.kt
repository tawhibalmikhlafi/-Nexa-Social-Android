
package com.nexa.social.feature.profile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.core.ui.rememberRemoteMode
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaCover
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.rememberImagePicker
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel =
        viewModel(factory = EditProfileViewModel.Factory)
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val remoteMode = rememberRemoteMode()
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.edit_profile_saved)
    val photoSavedMessage = stringResource(R.string.edit_profile_photo_saved)
    val coverSavedMessage = stringResource(R.string.edit_profile_cover_saved)

    var displayName by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var avatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var coverUri by rememberSaveable { mutableStateOf<String?>(null) }
    var originalAvatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var originalCoverUri by rememberSaveable { mutableStateOf<String?>(null) }
    var prefilled by remember { mutableStateOf(false) }

    LaunchedEffect(user) {
        val current = user
        if (!prefilled && current != null) {
            displayName = current.displayName
            bio = current.bio
            avatarUri = current.avatarUri
            coverUri = current.coverUri
            originalAvatarUri = current.avatarUri
            originalCoverUri = current.coverUri
            prefilled = true
        }
    }

    LaunchedEffect(saved) {
        if (saved) {
            val message = when {
                avatarUri != originalAvatarUri -> photoSavedMessage
                coverUri != originalCoverUri -> coverSavedMessage
                else -> savedMessage
            }
            scope.launch { snackbarHost?.showSnackbar(message) }
            delay(1600)
            viewModel.consumeSaved()
            onBack()
        }
    }

    val errorMessage = error?.let { stringResource(it) }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            snackbarHost?.showSnackbar(errorMessage)
            viewModel.consumeError()
        }
    }

    val pickAvatar = rememberImagePicker { uri ->
        uri?.let { avatarUri = it.toString() }
    }

    val pickCover = rememberImagePicker { uri ->
        uri?.let { coverUri = it.toString() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(
            title = stringResource(R.string.edit_profile_title),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            NexaCover(
                seed = (user?.id ?: "cover").hashCode().toLong(),
                coverUri = coverUri,
                height = 150.dp
            )

            NexaSecondaryButton(
                text = stringResource(R.string.edit_profile_pick_cover),
                onClick = pickCover
            )

            if (coverUri != null) {
                NexaSecondaryButton(
                    text = stringResource(R.string.edit_profile_remove_cover),
                    onClick = { coverUri = null }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaAvatar(
                    displayName = displayName.ifBlank { "N" },
                    avatarUri = avatarUri,
                    size = 72.dp
                )

                Spacer(
                    modifier = Modifier.padding(
                        start = NexaSpacing.md
                    )
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                ) {
                    NexaSecondaryButton(
                        text = stringResource(R.string.edit_profile_pick_image),
                        onClick = pickAvatar
                    )
                    if (avatarUri != null) {
                        NexaSecondaryButton(
                            text = stringResource(R.string.edit_profile_remove_image),
                            onClick = { avatarUri = null }
                        )
                    }
                }
            }

            NexaTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = stringResource(R.string.label_display_name)
            )

            NexaTextField(
                value = bio,
                onValueChange = { bio = it },
                label = stringResource(R.string.edit_profile_bio),
                singleLine = false,
                imeAction = ImeAction.Default
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "@${user?.username.orEmpty()}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(
                            start = NexaSpacing.sm
                        )
                    )
                }

                Text(
                    text = stringResource(
                        R.string.edit_profile_username_locked
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!remoteMode) {
                LocalDataBanner(
                    text = stringResource(R.string.notice_local_account)
                )
            }

            if (saving) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (uploadProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { uploadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(
                                R.string.edit_profile_upload_progress,
                                uploadProgress
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = NexaSpacing.xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.edit_profile_saving),
                                modifier = Modifier.padding(start = NexaSpacing.sm),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            NexaPrimaryButton(
                text = if (saving) {
                    "جارٍ الحفظ..."
                } else {
                    stringResource(R.string.action_save)
                },
                enabled = displayName.isNotBlank() && !saving && !saved,
                onClick = {
                    viewModel.save(
                        displayName = displayName,
                        bio = bio,
                        avatarUri = avatarUri,
                        coverUri = coverUri
                    )
                }
            )

            Spacer(Modifier.height(NexaSpacing.lg))
        }
    }
}
