package com.nexa.social.feature.clips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.core.ui.failureMessageRes
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaBottomSheet
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.SheetTitle
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.launch

@Composable
fun ClipsScreen(
    onBack: () -> Unit,
    onOpenClip: (String) -> Unit,
    viewModel: ClipsViewModel = viewModel(factory = ClipsViewModel.Factory)
) {
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val isRemote by viewModel.isRemote.collectAsStateWithLifecycle()
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    val addedMessage = stringResource(R.string.clip_added)

    var pendingVideo by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf("") }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingVideo = uri?.toString()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(
            title = stringResource(R.string.clips_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.clip_add_title))
                }
            }
        )
        if (!isRemote) {
            LocalDataBanner(
                text = stringResource(R.string.clips_local_notice),
                modifier = Modifier.padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.sm)
            )
        }

        if (clips.isEmpty()) {
            NexaEmptyState(
                icon = Icons.Filled.Videocam,
                title = stringResource(R.string.clips_empty_title),
                body = stringResource(R.string.clips_empty_body)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = NexaSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
            ) {
                items(clips, key = { it.id }) { clip ->
                    NexaCard(onClick = { onOpenClip(clip.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NexaAvatar(
                                displayName = clip.author.displayName,
                                avatarUri = clip.author.avatarUri,
                                size = 36.dp
                            )
                            Spacer(Modifier.width(NexaSpacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = clip.author.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = TimeUtils.relativeFromNow(clip.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (clip.caption.isNotBlank()) {
                            Spacer(Modifier.height(NexaSpacing.sm))
                            Text(text = clip.caption, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            modifier = Modifier.padding(top = NexaSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.toggleLike(clip.id) }) {
                                Icon(
                                    imageVector = if (clip.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = stringResource(R.string.post_like)
                                )
                            }
                            Text(text = clip.likeCount.toString(), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(NexaSpacing.sm))
                            IconButton(onClick = { viewModel.toggleSave(clip.id) }) {
                                Icon(
                                    imageVector = if (clip.savedByMe) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = stringResource(R.string.post_save)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        NexaBottomSheet(onDismiss = {
            showAdd = false
            pendingVideo = null
            caption = ""
        }) {
            SheetTitle(stringResource(R.string.clip_add_title))
            AccountRequiredBanner(text = stringResource(R.string.clip_requires_account))
            Text(
                text = stringResource(R.string.clip_video_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(NexaSpacing.sm))
            TextButton(onClick = { videoPicker.launch("video/*") }) {
                Text(stringResource(R.string.clip_pick_video))
            }
            if (pendingVideo != null) {
                Text(
                    text = stringResource(R.string.clip_video_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(NexaSpacing.sm))
            NexaTextField(
                value = caption,
                onValueChange = { caption = it },
                label = stringResource(R.string.clip_caption_hint)
            )
            Spacer(Modifier.height(NexaSpacing.md))
            TextButton(onClick = {
                val video = pendingVideo
                if (video != null) {
                    viewModel.addClip(video, caption) { reason ->
                        if (reason == null) {
                            snackbarHost?.let { host -> scope.launch { host.showSnackbar(addedMessage) } }
                            showAdd = false
                            pendingVideo = null
                            caption = ""
                        } else {
                            val message = context.getString(failureMessageRes(reason))
                            snackbarHost?.let { host -> scope.launch { host.showSnackbar(message) } }
                        }
                    }
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
