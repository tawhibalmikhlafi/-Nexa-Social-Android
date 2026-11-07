
package com.nexa.social.feature.profile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
    var username by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var avatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var coverUri by rememberSaveable { mutableStateOf<String?>(null) }
    var originalAvatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var originalCoverUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showCoverMenu by remember { mutableStateOf(false) }
    var showCoverPreview by remember { mutableStateOf(false) }
    var shareCoverToProfile by rememberSaveable { mutableStateOf(false) }
    var coverCaption by rememberSaveable { mutableStateOf("") }
    var coverSelectedForPost by rememberSaveable { mutableStateOf(false) }
    var prefilled by remember { mutableStateOf(false) }

    LaunchedEffect(user) {
        val current = user
        if (!prefilled && current != null) {
            displayName = current.displayName
            username = current.username
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
        uri?.let {
            coverUri = it.toString()
            coverSelectedForPost = true
            shareCoverToProfile = false
            coverCaption = ""
        }
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
            Box(modifier = Modifier.fillMaxWidth()) {
                NexaCover(
                    seed = (user?.id ?: "cover").hashCode().toLong(),
                    coverUri = coverUri,
                    height = 170.dp
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(NexaSpacing.sm)
                ) {
                    IconButton(
                        onClick = { showCoverMenu = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "خيارات صورة الغلاف",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = showCoverMenu,
                        onDismissRequest = { showCoverMenu = false }
                    ) {
                        if (!coverUri.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("عرض الصورة") },
                                leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                                onClick = {
                                    showCoverMenu = false
                                    showCoverPreview = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("إضافة صورة") },
                            leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null) },
                            onClick = {
                                showCoverMenu = false
                                pickCover()
                            }
                        )
                        if (!coverUri.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("حذف الصورة") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showCoverMenu = false
                                    coverUri = null
                                    coverSelectedForPost = false
                                    shareCoverToProfile = false
                                    coverCaption = ""
                                }
                            )
                        }
                    }
                }
            }

            if (coverSelectedForPost && coverUri != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(NexaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = shareCoverToProfile,
                            onCheckedChange = { shareCoverToProfile = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "مشاركة الصورة في الملف الشخصي",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "عند التفعيل ستُحفظ كغلاف وتُنشر أيضاً كمنشور",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (shareCoverToProfile) {
                        NexaTextField(
                            value = coverCaption,
                            onValueChange = { coverCaption = it },
                            label = "اكتب نصاً مع الصورة (اختياري)",
                            singleLine = false,
                            imeAction = ImeAction.Default
                        )
                    }
                }
            }

            if (showCoverPreview && !coverUri.isNullOrBlank()) {
                Dialog(onDismissRequest = { showCoverPreview = false }) {
                    androidx.compose.material3.Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(NexaSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                        ) {
                            AsyncImage(
                                model = coverUri,
                                contentDescription = "صورة الغلاف",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .clip(MaterialTheme.shapes.medium),
                                contentScale = ContentScale.Fit
                            )
                            TextButton(
                                onClick = { showCoverPreview = false },
                                modifier = Modifier.align(Alignment.End)
                            ) { Text("إغلاق") }
                        }
                    }
                }
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

            NexaTextField(
                value = username,
                onValueChange = { username = it.removePrefix("@").filter { ch -> ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_' }.take(20) },
                label = stringResource(R.string.label_username),
                singleLine = true,
                imeAction = ImeAction.Next
            )
            Text(
                text = stringResource(R.string.edit_profile_username_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                        username = username,
                        bio = bio,
                        avatarUri = avatarUri,
                        coverUri = coverUri,
                        shareCoverToProfile = shareCoverToProfile,
                        coverCaption = coverCaption
                    )
                }
            )

            Spacer(Modifier.height(NexaSpacing.lg))
        }
    }
}
