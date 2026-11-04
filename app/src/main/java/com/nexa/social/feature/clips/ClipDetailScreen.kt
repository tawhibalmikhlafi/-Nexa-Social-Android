package com.nexa.social.feature.clips

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.ui.components.NexaAvatar

@Composable
fun ClipDetailScreen(
    clipId: String,
    onBack: () -> Unit,
    viewModel: ClipsViewModel = viewModel(factory = ClipsViewModel.Factory)
) {
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val autoplay by viewModel.videoAutoplay.collectAsStateWithLifecycle()
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val reduceMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()
    val clip = clips.firstOrNull { it.id == clipId }
    var playing by remember(clipId) { mutableStateOf(autoplay && !dataSaver && !reduceMotion) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (clip == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.state_empty_generic), color = Color.White)
                Text(stringResource(R.string.clips_local_notice), color = Color.LightGray)
            }
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
            }
            return@Box
        }

        val videoUri = clip.videoUri
        if (!videoUri.isNullOrBlank()) {
            ClipVideo(
                uri = videoUri,
                autoplay = autoplay && !dataSaver && !reduceMotion,
                playing = playing,
                onTogglePlayback = { playing = !playing }
            )
        } else {
            Text(stringResource(R.string.clip_no_video), color = Color.White)
        }

        // Soft dark scrims keep the controls readable over bright video frames.
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.28f)).padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.clips_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.38f)).padding(start = 16.dp, end = 10.dp, top = 18.dp, bottom = 24.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NexaAvatar(clip.author.displayName, clip.author.avatarUri, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(clip.author.displayName, color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text(TimeUtils.relativeFromNow(clip.createdAt), color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (clip.caption.isNotBlank()) {
                        Text(
                            text = clip.caption,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { viewModel.toggleLike(clip.id) }) {
                        Icon(
                            if (clip.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.post_like),
                            tint = if (clip.likedByMe) Color(0xFFFF5277) else Color.White,
                            modifier = Modifier.size(29.dp)
                        )
                    }
                    Text(clip.likeCount.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.size(12.dp))
                    IconButton(onClick = { viewModel.toggleSave(clip.id) }) {
                        Icon(
                            if (clip.savedByMe) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = stringResource(R.string.post_save),
                            tint = Color.White,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
            }
        }

        if (videoUri != null) {
            IconButton(
                onClick = { playing = !playing },
                modifier = Modifier.align(Alignment.Center).size(64.dp).background(Color.Black.copy(alpha = 0.35f), MaterialTheme.shapes.extraLarge)
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}

@Composable
private fun ClipVideo(
    uri: String,
    autoplay: Boolean,
    playing: Boolean,
    onTogglePlayback: () -> Unit
) {
    val context = LocalContext.current
    val videoView = remember(uri) {
        VideoView(context)
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().clickable(onClick = onTogglePlayback),
        factory = {
            videoView.apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                setVideoURI(Uri.parse(uri))
                setOnPreparedListener { player ->
                    player.isLooping = true
                    if (autoplay) start() else pause()
                }
            }
        },
        update = { view ->
            if (playing && !view.isPlaying) view.start()
            if (!playing && view.isPlaying) view.pause()
        }
    )
    DisposableEffect(videoView) {
        onDispose { runCatching { videoView.stopPlayback() } }
    }
}
