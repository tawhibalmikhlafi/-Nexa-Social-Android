package com.nexa.social.feature.clips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.ui.components.NexaAvatar
import kotlinx.coroutines.launch

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
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val commentsLoading by viewModel.commentsLoading.collectAsStateWithLifecycle()
    val commentError by viewModel.commentError.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val clip = clips.firstOrNull { it.id == clipId }
    var playing by remember(clipId) { mutableStateOf(autoplay && !dataSaver && !reduceMotion) }
    var showComments by remember(clipId) { mutableStateOf(false) }
    var resumeAfterComments by remember(clipId) { mutableStateOf(false) }
    var commentText by remember(clipId) { mutableStateOf("") }
    var sendingComment by remember(clipId) { mutableStateOf(false) }
    var showEditCaption by remember(clipId) { mutableStateOf(false) }
    var showDeleteConfirm by remember(clipId) { mutableStateOf(false) }
    var editedCaption by remember(clipId) { mutableStateOf("") }
    LaunchedEffect(clipId) { viewModel.recordView(clipId) }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (clip == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.state_empty_generic), color = Color.White)
                Text(stringResource(R.string.clips_local_notice), color = Color.LightGray)
            }
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
            }
            return@Box
        }

        val videoUri = clip.videoUri
        if (!videoUri.isNullOrBlank()) {
            ClipVideo(videoUri, autoplay && !dataSaver && !reduceMotion, playing, { playing = !playing })
        } else Text(stringResource(R.string.clip_no_video), color = Color.White)

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .30f)).padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White) }
                Text(stringResource(R.string.clips_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .40f)).padding(start = 16.dp, end = 10.dp, top = 18.dp, bottom = 24.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NexaAvatar(clip.author.displayName, clip.author.avatarUri, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(clip.author.displayName, color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text(TimeUtils.relativeFromNow(clip.createdAt), color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (clip.caption.isNotBlank()) Text(clip.caption, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { viewModel.toggleLike(clip.id) }) {
                        Icon(if (clip.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "إعجاب", tint = if (clip.likedByMe) Color(0xFFFF5277) else Color.White, modifier = Modifier.size(29.dp))
                    }
                    Text(clip.likeCount.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.size(8.dp))
                    IconButton(onClick = { viewModel.toggleSave(clip.id) }) {
                        Icon(if (clip.savedByMe) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "حفظ", tint = Color.White, modifier = Modifier.size(27.dp))
                    }
                    Text(clip.viewCount.toString(), color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = {
                        resumeAfterComments = playing
                        playing = false
                        showComments = true
                        viewModel.loadComments(clip.id)
                    }) { Icon(Icons.Filled.Comment, "التعليقات", tint = Color.White) }
                    Text(clip.commentCount.toString(), color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    if (clip.author.id == currentUserId) {
                        IconButton(onClick = { editedCaption = clip.caption; showEditCaption = true }) { Icon(Icons.Filled.Edit, "تعديل الوصف", tint = Color.White) }
                        IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Filled.Delete, "حذف الريلز", tint = Color.White) }
                    }
                }
            }
        }

        if (showEditCaption) AlertDialog(
            onDismissRequest = { showEditCaption = false },
            title = { Text("تعديل وصف الريلز") },
            text = { OutlinedTextField(value = editedCaption, onValueChange = { editedCaption = it }, modifier = Modifier.fillMaxWidth(), maxLines = 5, label = { Text("الوصف") }) },
            confirmButton = { TextButton(onClick = { viewModel.updateCaption(clip.id, editedCaption) { if (it) showEditCaption = false } }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { showEditCaption = false }) { Text("إلغاء") } }
        )
        if (showDeleteConfirm) AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف الريلز؟") },
            text = { Text("لن يتمكن الآخرون من مشاهدة هذا الفيديو بعد حذفه.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteClip(clip.id) { if (it) onBack() }; showDeleteConfirm = false }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("إلغاء") } }
        )

        if (showComments) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
            ModalBottomSheet(
                onDismissRequest = {
                    showComments = false
                    if (resumeAfterComments) playing = true
                },
                sheetState = sheetState,
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                dragHandle = {
                    Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f), CircleShape))
                }
            ) {
                Column(Modifier.fillMaxWidth().heightIn(min = 340.dp, max = 680.dp).imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(28.dp))
                        Text("التعليقات · ${clip.commentCount}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        IconButton(onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showComments = false
                                if (resumeAfterComments) playing = true
                            }
                        }) { Icon(Icons.Filled.Close, "إغلاق التعليقات") }
                    }
                    HorizontalDivider()
                    when {
                        commentsLoading && comments.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        commentError && comments.isEmpty() -> Column(
                            Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("تعذر تحميل التعليقات", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.loadComments(clip.id) }) { Text("إعادة المحاولة") }
                        }
                        comments.isEmpty() -> Column(
                            Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Comment, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Text("ابدأ المحادثة", style = MaterialTheme.typography.titleMedium)
                            Text("كن أول من يعلّق على هذا الريلز", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                        else -> LazyColumn(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            items(comments, key = { it.id }) { comment ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    NexaAvatar(comment.authorName, comment.authorAvatarUrl, size = 38.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(comment.authorName, style = MaterialTheme.typography.labelLarge)
                                            Spacer(Modifier.width(8.dp))
                                            Text(TimeUtils.relativeFromNow(comment.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(comment.content, style = MaterialTheme.typography.bodyMedium)
                                        if (comment.isAuthor) TextButton(onClick = { viewModel.deleteComment(comment.id) }) {
                                            Text("حذف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (commentError && comments.isNotEmpty()) {
                        Text("تعذر إرسال التعليق. تحقق من الاتصال وحاول مجددًا.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it.take(2000) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("أضف تعليقًا...") },
                            maxLines = 4,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            enabled = commentText.isNotBlank() && !sendingComment,
                            onClick = {
                                val text = commentText.trim()
                                if (text.isNotEmpty()) {
                                    sendingComment = true
                                    viewModel.addComment(clip.id, text) { success ->
                                        sendingComment = false
                                        if (success) commentText = ""
                                    }
                                }
                            },
                            modifier = Modifier.size(46.dp).background(
                                if (commentText.isNotBlank() && !sendingComment) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                        ) {
                            if (sendingComment) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.Send, "إرسال التعليق", tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (!videoUri.isNullOrBlank() && !showComments) {
            IconButton(
                onClick = { playing = !playing },
                modifier = Modifier.align(Alignment.Center).size(64.dp).background(Color.Black.copy(alpha = .35f), MaterialTheme.shapes.extraLarge)
            ) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "إيقاف الفيديو" else "تشغيل الفيديو", tint = Color.White, modifier = Modifier.size(38.dp))
            }
        }
    }
}

@Composable
private fun ClipVideo(uri: String, autoplay: Boolean, playing: Boolean, onTogglePlayback: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val shouldPlay by rememberUpdatedState(playing)
    var playbackError by remember(uri) { mutableStateOf(false) }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(uri))
            playWhenReady = autoplay
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { playbackError = true }
            })
            prepare()
        }
    }
    LaunchedEffect(playing, player) {
        if (playing) player.play() else player.pause()
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> if (shouldPlay) player.play() else player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clickable(onClick = onTogglePlayback),
            factory = { PlayerView(it) },
            update = { view ->
                view.player = player
                view.useController = false
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                view.keepScreenOn = playing
            }
        )
        if (playbackError) Column(
            Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .75f)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("تعذر تشغيل هذا الفيديو", color = Color.White)
            TextButton(onClick = {
                playbackError = false
                player.seekTo(0)
                player.prepare()
                if (playing) player.play()
            }) { Text("إعادة المحاولة", color = Color.White) }
        }
    }
}
