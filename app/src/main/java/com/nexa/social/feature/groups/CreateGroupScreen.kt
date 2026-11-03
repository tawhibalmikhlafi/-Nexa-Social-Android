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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nexa.social.R
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaChoiceChip
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.rememberImagePicker
import com.nexa.social.ui.theme.NexaRadius
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateGroupViewModel = viewModel(factory = CreateGroupViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdId) {
        state.createdId?.let(onCreated)
    }

    val pickAvatar = rememberImagePicker { uri -> viewModel.onAvatarPicked(uri?.toString()) }
    val pickCover = rememberImagePicker { uri -> viewModel.onCoverPicked(uri?.toString()) }

    Scaffold(
        topBar = {
            NexaTopBar(
                title = stringResource(R.string.groups_create_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md),
        ) {
            CoverPicker(
                coverUri = state.coverUri,
                onPick = pickCover,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexaAvatar(
                    displayName = state.name.ifBlank { "N" },
                    avatarUri = state.avatarUri,
                    size = 72.dp,
                )
                Spacer(Modifier.width(NexaSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.groups_avatar_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            }
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.groups_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(NexaRadius.chip),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = { Text(stringResource(R.string.groups_description_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(NexaRadius.chip),
                minLines = 3,
            )
            OutlinedTextField(
                value = state.category.orEmpty(),
                onValueChange = viewModel::onCategory,
                label = { Text(stringResource(R.string.groups_category_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(NexaRadius.chip),
                singleLine = true,
            )
            Text(
                text = stringResource(R.string.groups_privacy_label),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                NexaChoiceChip(
                    label = stringResource(R.string.groups_public),
                    selected = state.privacy == "public",
                    onClick = { viewModel.onPrivacy("public") },
                    modifier = Modifier.weight(1f),
                )
                NexaChoiceChip(
                    label = stringResource(R.string.groups_private),
                    selected = state.privacy == "private",
                    onClick = { viewModel.onPrivacy("private") },
                    modifier = Modifier.weight(1f),
                )
            }
            PrivacyHint(privacy = state.privacy)
            if (state.error != null) {
                Text(
                    text = stringResource(
                        if (state.error == "upload_failed") {
                            R.string.groups_upload_failed
                        } else {
                            R.string.groups_create_failed
                        }
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = viewModel::submit,
                enabled = !state.isSaving && state.name.trim().length >= 2,
                shape = RoundedCornerShape(NexaRadius.pill),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.groups_create_action))
                }
            }
        }
    }
}

@Composable
private fun CoverPicker(coverUri: String?, onPick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(NexaRadius.sheet))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    )
                )
            )
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                        )
                    )
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.height(NexaSpacing.xs))
                Text(
                    text = stringResource(R.string.groups_change_cover),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (coverUri != null) {
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
}

@Composable
private fun PrivacyHint(privacy: String) {
    val isPublic = privacy == "public"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexaRadius.chip))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isPublic) Icons.Filled.Public else Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(NexaSpacing.sm))
        Text(
            text = stringResource(
                if (isPublic) {
                    R.string.groups_privacy_public_hint
                } else {
                    R.string.groups_privacy_private_hint
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
