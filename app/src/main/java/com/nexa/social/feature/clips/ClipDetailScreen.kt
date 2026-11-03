package com.nexa.social.feature.clips

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun ClipDetailScreen(
    clipId: String,
    onBack: () -> Unit,
    viewModel: ClipsViewModel = viewModel(factory = ClipsViewModel.Factory)
) {
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val clip = clips.firstOrNull { it.id == clipId }

    Column(modifier = Modifier.fillMaxWidth()) {
        NexaTopBar(title = stringResource(R.string.clip_detail_title), onBack = onBack)
        if (clip == null) {
            NexaEmptyState(
                icon = Icons.Filled.Videocam,
                title = stringResource(R.string.state_empty_generic),
                body = stringResource(R.string.clips_local_notice)
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            LocalDataBanner(text = stringResource(R.string.clip_playback_notice))
            if (clip.videoUri.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.clip_no_video),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ClipVideo(uri = clip.videoUri)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaAvatar(
                    displayName = clip.author.displayName,
                    avatarUri = clip.author.avatarUri,
                    size = 40.dp
                )
                Column(modifier = Modifier.weight(1f).padding(start = NexaSpacing.sm)) {
                    Text(text = clip.author.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = TimeUtils.relativeFromNow(clip.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.toggleLike(clip.id) }) {
                    Icon(
                        imageVector = if (clip.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(R.string.post_like)
                    )
                }
                Text(text = clip.likeCount.toString(), style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { viewModel.toggleSave(clip.id) }) {
                    Icon(
                        imageVector = if (clip.savedByMe) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(R.string.post_save)
                    )
                }
            }
            if (clip.caption.isNotBlank()) {
                Text(text = clip.caption, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ClipVideo(uri: String) {
    // Zero-dependency playback: Android's own VideoView, no streaming library.
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f),
        factory = { context ->
            VideoView(context).apply {
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setVideoURI(Uri.parse(uri))
                setOnPreparedListener { it.isLooping = false }
            }
        }
    )
}
