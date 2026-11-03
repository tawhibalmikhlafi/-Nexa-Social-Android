package com.nexa.social.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nexa.social.R
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.rememberImagePicker
import com.nexa.social.ui.theme.NexaRadius
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun GroupSettingsScreen(
    groupId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onLeft: () -> Unit,
    viewModel: GroupSettingsViewModel = viewModel(factory = GroupSettingsViewModel.factory(groupId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val group = state.group
    val snackbar = LocalSnackbarHostState.current

    var name by remember(group?.id) { mutableStateOf(group?.name.orEmpty()) }
    var description by remember(group?.id) { mutableStateOf(group?.description.orEmpty()) }
    var rules by remember(group?.id) { mutableStateOf(group?.rules.orEmpty()) }
    var category by remember(group?.id) { mutableStateOf(group?.category.orEmpty()) }
    var privacy by remember(group?.id) { mutableStateOf(group?.privacy?.wire ?: "public") }
    var avatarUrl by remember(group?.id, group?.avatarUrl) {
        mutableStateOf(group?.avatarUrl.orEmpty())
    }
    var coverUrl by remember(group?.id, group?.coverUrl) {
        mutableStateOf(group?.coverUrl.orEmpty())
    }
    var avatarLocalUri by remember(group?.id, group?.avatarUrl) { mutableStateOf<String?>(null) }
    var coverLocalUri by remember(group?.id, group?.coverUrl) { mutableStateOf<String?>(null) }
    var allowMemberPosts by remember(group?.id) { mutableStateOf(group?.allowMemberPosts ?: true) }
    var allowMemberInvites by remember(group?.id) { mutableStateOf(group?.allowMemberInvites ?: true) }
    var requireJoinApproval by remember(group?.id) { mutableStateOf(group?.requireJoinApproval ?: false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val pickAvatar = rememberImagePicker { uri -> avatarLocalUri = uri?.toString() }
    val pickCover = rememberImagePicker { uri -> coverLocalUri = uri?.toString() }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    LaunchedEffect(state.left) {
        if (state.left) onLeft()
    }

    val savedMessage = stringResource(R.string.groups_saved_success)
    val leftMessage = stringResource(R.string.groups_left_success)
    val archivedMessage = stringResource(R.string.groups_archived_success)
    val unarchivedMessage = stringResource(R.string.groups_unarchived_success)
    val saveFailedMessage = stringResource(R.string.groups_save_failed)
    val deleteFailedMessage = stringResource(R.string.groups_delete_failed)
    val leaveFailedMessage = stringResource(R.string.groups_leave_failed)
    val archiveFailedMessage = stringResource(R.string.groups_archive_failed)
    val incompleteMessage = stringResource(R.string.groups_operation_incomplete)
    val genericErrorMessage = stringResource(R.string.groups_error)
    val uploadFailedMessage = stringResource(R.string.groups_upload_failed)

    LaunchedEffect(state.successMessage) {
        val message = when (state.successMessage) {
            "saved" -> savedMessage
            "left" -> leftMessage
            "archived" -> archivedMessage
            "unarchived" -> unarchivedMessage
            else -> null
        }
        if (message != null) {
            snackbar?.showSnackbar(message)
            viewModel.clearTransient()
        }
    }

    LaunchedEffect(state.error) {
        val currentError = state.error ?: return@LaunchedEffect
        val message = when (currentError) {
            "save_failed" -> saveFailedMessage
            "delete_failed" -> deleteFailedMessage
            "leave_failed" -> leaveFailedMessage
            "archive_failed" -> archiveFailedMessage
            "operation_incomplete" -> incompleteMessage
            "upload_failed" -> uploadFailedMessage
            else -> genericErrorMessage
        }
        snackbar?.showSnackbar(message)
        viewModel.clearTransient()
    }

    val normalizedName = name.trim()
    val nameValid = normalizedName.length in 2..100
    val hasChanges = group != null && (
        normalizedName != group.name ||
            description.trim() != group.description ||
            rules.trim() != group.rules ||
            category.trim() != group.category.orEmpty() ||
            privacy != group.privacy.wire ||
            avatarUrl.trim() != group.avatarUrl.orEmpty() ||
            coverUrl.trim() != group.coverUrl.orEmpty() ||
            allowMemberPosts != group.allowMemberPosts ||
            allowMemberInvites != group.allowMemberInvites ||
            requireJoinApproval != group.requireJoinApproval ||
            avatarLocalUri != null ||
            coverLocalUri != null
        )
    val canSave = nameValid && hasChanges &&
        !state.isSaving && !state.isDeleting && !state.isArchiving

    Scaffold(
        topBar = {
            NexaTopBar(
                title = stringResource(R.string.groups_settings_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (group == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.groups_load_failed))
                Spacer(Modifier.size(NexaSpacing.md))
                Button(onClick = { viewModel.load() }) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.groups_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.groups_description_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rules,
                onValueChange = { rules = it },
                label = { Text(stringResource(R.string.groups_rules_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text(stringResource(R.string.groups_category_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.groups_privacy_label),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = privacy == "public",
                    onClick = { privacy = "public" },
                )
                Text(stringResource(R.string.groups_public))
                Spacer(Modifier.width(16.dp))
                RadioButton(
                    selected = privacy == "private",
                    onClick = { privacy = "private" },
                )
                Text(stringResource(R.string.groups_private))
            }
            CoverSetting(
                imageUri = coverLocalUri ?: coverUrl.takeIf { it.isNotBlank() },
                onPick = pickCover,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaAvatar(
                    displayName = name.ifBlank { "N" },
                    avatarUri = avatarLocalUri ?: avatarUrl.takeIf { it.isNotBlank() },
                    size = 64.dp,
                )
                Spacer(Modifier.width(NexaSpacing.md))
                TextButton(onClick = pickAvatar) {
                    Icon(
                        imageVector = Icons.Filled.AddAPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(NexaSpacing.xs))
                    Text(stringResource(R.string.groups_change_avatar))
                }
            }
            SwitchRow(
                label = stringResource(R.string.groups_allow_member_posts),
                checked = allowMemberPosts,
                onCheckedChange = { allowMemberPosts = it },
            )
            SwitchRow(
                label = stringResource(R.string.groups_allow_member_invites),
                checked = allowMemberInvites,
                onCheckedChange = { allowMemberInvites = it },
            )
            SwitchRow(
                label = stringResource(R.string.groups_require_join_approval),
                checked = requireJoinApproval,
                onCheckedChange = { requireJoinApproval = it },
            )
            SwitchRow(
                label = stringResource(
                    if (group.isArchived) {
                        R.string.groups_unarchive_action
                    } else {
                        R.string.groups_archive_action
                    }
                ),
                checked = group.isArchived,
                enabled = !state.isArchiving && !state.isSaving && !state.isDeleting,
                onCheckedChange = { viewModel.setArchived(it) },
            )
            Button(
                onClick = {
                    viewModel.save(
                        name = normalizedName,
                        description = description,
                        rules = rules,
                        category = category,
                        privacy = privacy,
                        avatarUrl = avatarUrl,
                        coverUrl = coverUrl,
                        allowMemberPosts = allowMemberPosts,
                        allowMemberInvites = allowMemberInvites,
                        requireJoinApproval = requireJoinApproval,
                        avatarLocalUri = avatarLocalUri,
                        coverLocalUri = coverLocalUri,
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.groups_save))
                }
            }
            if (group.isMember && !group.isOwner) {
                OutlinedButton(
                    onClick = { viewModel.leave() },
                    enabled = !state.isLeaving && !state.isSaving && !state.isDeleting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isLeaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.groups_leave))
                    }
                }
            }
            if (group.isOwner) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    enabled = !state.isDeleting && !state.isSaving && !state.isArchiving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.groups_delete))
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.groups_delete_confirm_title)) },
            text = { Text(stringResource(R.string.groups_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    },
                ) {
                    Text(stringResource(R.string.groups_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.groups_cancel))
                }
            },
        )
    }
}

@Composable
private fun CoverSetting(imageUri: String?, onPick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(NexaRadius.sheet))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AddAPhoto,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(NexaSpacing.sm)
                .clip(RoundedCornerShape(NexaRadius.pill))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AddAPhoto,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(NexaSpacing.xs))
            Text(
                text = stringResource(R.string.groups_change_cover),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
