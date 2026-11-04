package com.nexa.social.feature.messages

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.domain.model.Message
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.feature.presence.PresenceViewModel
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaBottomSheet
import com.nexa.social.ui.components.NexaOptionRow
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.OnlineAvatar
import com.nexa.social.ui.components.SheetTitle
import com.nexa.social.ui.components.initialsOf
import com.nexa.social.ui.components.rememberImagePicker
import com.nexa.social.ui.theme.NexaRadius
import com.nexa.social.ui.theme.NexaSpacing
import java.util.Calendar

private val reactionChoices = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/** Consecutive messages from the same sender within this window share a bubble group. */
private const val MESSAGE_GROUP_WINDOW_MS = 5 * 60_000L

@Composable
private fun ConversationTopBar(
    peer: UserSummary,
    presenceViewModel: PresenceViewModel,
    onBack: () -> Unit
) {
    NexaTopBar(
        title = peer.displayName,
        onBack = onBack,
        titleContent = { ConversationHeader(peer, presenceViewModel) }
    )
}

@Composable
private fun ConversationHeader(peer: UserSummary, presenceViewModel: PresenceViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OnlineAvatar(
            userId = peer.id,
            imageUrl = peer.avatarUri,
            size = 36.dp,
            fallbackInitials = initialsOf(peer.displayName),
            presenceViewModel = presenceViewModel
        )
        Spacer(Modifier.width(NexaSpacing.sm))
        Column {
            Text(text = peer.displayName, style = MaterialTheme.typography.titleSmall)
            val online by presenceViewModel.onlineFor(peer.id)
                .collectAsStateWithLifecycle(initialValue = false)
            if (online) {
                Text(
                    text = stringResource(R.string.presence_online_now),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ConversationScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ConversationViewModel = viewModel(
        factory = ConversationViewModel.factory(conversationId),
        key = conversationId
    ),
    presenceViewModel: PresenceViewModel = viewModel(factory = PresenceViewModel.Factory)
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val canSend by viewModel.canSend.collectAsStateWithLifecycle()
    val isRemote by viewModel.isRemote.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val attachmentUrls by viewModel.attachmentUrls.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var attachmentUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Message?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var isRecordingPaused by rememberSaveable { mutableStateOf(false) }
    var recordingSeconds by rememberSaveable { mutableStateOf(0) }
    val recorderState = remember { mutableStateOf<MediaRecorder?>(null) }
    val recordingFile = remember { mutableStateOf<File?>(null) }

    fun startVoiceRecording() {
        val file = File(context.cacheDir, "nexa-voice-\u0024{System.currentTimeMillis()}.m4a")
        runCatching {
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(96_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.onSuccess { recorder ->
            recorderState.value = recorder
            recordingFile.value = file
            recordingSeconds = 0
            isRecordingPaused = false
            isRecording = true
        }.onFailure { file.delete() }
    }

    fun finishVoiceRecording(send: Boolean) {
        val file = recordingFile.value
        runCatching { recorderState.value?.stop() }
        runCatching { recorderState.value?.release() }
        recorderState.value = null
        recordingFile.value = null
        isRecording = false
        isRecordingPaused = false
        recordingSeconds = 0
        if (send && file != null && file.exists() && file.length() > 0L) {
            viewModel.send("", Uri.fromFile(file).toString())
        } else file?.delete()
    }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startVoiceRecording() }
    val pickAttachment = rememberImagePicker { uri ->
        if (uri != null) attachmentUri = uri.toString()
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) attachmentUri = uri.toString()
    }

    val messages = conversation?.messages.orEmpty()

    LaunchedEffect(isRecording, isRecordingPaused) {
        while (isRecording && !isRecordingPaused) {
            delay(1_000)
            recordingSeconds += 1
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        val peer = conversation?.participant
        if (peer != null) {
            ConversationTopBar(peer, presenceViewModel, onBack)
        } else {
            NexaTopBar(title = "", onBack = onBack)
        }

        if (!isRemote) {
            LocalDataBanner(
                text = stringResource(R.string.conversation_local_notice),
                modifier = Modifier.padding(horizontal = NexaSpacing.lg)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = NexaSpacing.md,
                end = NexaSpacing.md,
                top = NexaSpacing.sm,
                bottom = NexaSpacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                val previous = messages.getOrNull(index - 1)
                val next = messages.getOrNull(index + 1)
                val newDay = previous == null || !sameDay(previous.timestamp, message.timestamp)
                if (newDay) {
                    DateSeparator(timestamp = message.timestamp)
                }
                val joinsPrevious = previous != null &&
                    previous.senderId == message.senderId &&
                    !newDay &&
                    message.timestamp - previous.timestamp <= MESSAGE_GROUP_WINDOW_MS
                val joinsNext = next != null &&
                    next.senderId == message.senderId &&
                    sameDay(next.timestamp, message.timestamp) &&
                    next.timestamp - message.timestamp <= MESSAGE_GROUP_WINDOW_MS
                MessageBubble(
                    message = message,
                    attachmentUrl = attachmentUrls[message.id],
                    currentUserId = currentUserId,
                    isFirstInGroup = !joinsPrevious,
                    isLastInGroup = !joinsNext,
                    onOpenActions = { selected = message },
                    onToggleReaction = { emoji -> viewModel.toggleReaction(message.id, emoji) },
                    onToggleStar = { viewModel.toggleStar(message.id, !message.isStarred) }
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm)
            ) {
                val failureText = failureMessage(failure)
                if (failureText != null) {
                    AccountRequiredBanner(text = failureText)
                    Spacer(Modifier.height(NexaSpacing.xs))
                }
                if (!canSend) {
                    AccountRequiredBanner(text = stringResource(R.string.messages_send_requires_account))
                    Spacer(Modifier.height(NexaSpacing.sm))
                }
                if (isRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = NexaSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(if (isRecordingPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error))
                        Spacer(Modifier.width(NexaSpacing.sm))
                        Text(String.format("%02d:%02d", recordingSeconds / 60, recordingSeconds % 60), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (isRecordingPaused) { runCatching { recorderState.value?.resume() }; isRecordingPaused = false } else { runCatching { recorderState.value?.pause() }; isRecordingPaused = true } }) {
                            Icon(if (isRecordingPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
                        }
                        IconButton(onClick = { finishVoiceRecording(false) }) { Icon(Icons.Filled.Close, contentDescription = null) }
                        FilledIconButton(onClick = { finishVoiceRecording(true) }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) }
                    }
                }
                if (editing != null || attachmentUri != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val chipText = if (editing != null) {
                            stringResource(R.string.message_editing)
                        } else {
                            stringResource(R.string.message_attachment_selected)
                        }
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                editing = null
                                attachmentUri = null
                                input = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.message_edit_cancel),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    IconButton(
                        onClick = {
                            if (input.isBlank() && editing == null && !isRecording) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoiceRecording()
                                else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                            } else pickAttachment()
                        },
                        enabled = canSend && editing == null && !isRecording,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (input.isBlank() && editing == null) Icons.Filled.Mic else Icons.Filled.AttachFile,
                            contentDescription = stringResource(R.string.message_attachment_add),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { pickVideo.launch("video/*") },
                        enabled = canSend && editing == null && !isRecording,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = "Send video", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(stringResource(R.string.conversation_hint_send)) },
                        enabled = canSend && !isRecording,
                        maxLines = 5,
                        shape = RoundedCornerShape(NexaRadius.pill),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(NexaSpacing.xs))
                    FilledIconButton(
                        onClick = {
                            val target = editing
                            if (target != null) {
                                viewModel.edit(target.id, input)
                                editing = null
                            } else {
                                viewModel.send(input, attachmentUri)
                                attachmentUri = null
                            }
                            input = ""
                        },
                        enabled = canSend && input.isNotBlank() && !isRecording,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (editing != null) {
                                Icons.Filled.Check
                            } else {
                                Icons.AutoMirrored.Filled.Send
                            },
                            contentDescription = stringResource(
                                if (editing != null) R.string.message_edit_save else R.string.action_send
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    selected?.let { message ->
        MessageActionSheet(
            message = message,
            onDismiss = { selected = null },
            onCopy = {
                if (message.text.isNotBlank()) {
                    clipboard.setText(AnnotatedString(message.text))
                }
                selected = null
            },
            onReact = { emoji ->
                viewModel.toggleReaction(message.id, emoji)
                selected = null
            },
            onEdit = {
                editing = message
                input = message.text
                selected = null
            },
            onToggleStar = {
                viewModel.toggleStar(message.id, !message.isStarred)
                selected = null
            },
            onDeleteForMe = {
                viewModel.deleteForMe(message.id)
                if (editing?.id == message.id) {
                    editing = null
                    input = ""
                }
                selected = null
            },
            onDeleteForEveryone = {
                viewModel.deleteForEveryone(message.id)
                if (editing?.id == message.id) {
                    editing = null
                    input = ""
                }
                selected = null
            },
            onBlock = {
                conversation?.participant?.let { viewModel.blockUser(it) }
                selected = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    attachmentUrl: String?,
    currentUserId: String?,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    onOpenActions: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onToggleStar: () -> Unit
) {
    val mine = message.isMine
    val bubbleColor = if (mine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (mine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val mutedColor = if (mine) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else if (isLastInGroup) 6.dp else 18.dp,
        bottomEnd = if (mine) if (isLastInGroup) 6.dp else 18.dp else 18.dp
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFirstInGroup) NexaSpacing.xs else 0.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier
                    .clip(shape)
                    .background(bubbleColor)
                    .then(
                        if (mine) {
                            Modifier
                        } else {
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                                shape
                            )
                        }
                    )
                    .combinedClickable(onClick = {}, onLongClick = onOpenActions)
                    .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm)
            ) {
                if (message.replyToStoryId != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(NexaRadius.chip))
                            .background(
                                if (mine) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                }
                            )
                            .padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = if (mine) contentColor else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(NexaSpacing.xxs))
                        Text(
                            text = stringResource(R.string.message_replied_to_story),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mine) contentColor else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(NexaSpacing.xs))
                }
                if (!message.attachmentPath.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (mine) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (attachmentUrl.isNullOrBlank()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = if (mine) contentColor else MaterialTheme.colorScheme.primary
                            )
                        } else if (message.attachmentMime?.startsWith("video/") == true) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    VideoView(context).apply {
                                        setMediaController(MediaController(context).also { it.setAnchorView(this) })
                                        setVideoURI(Uri.parse(attachmentUrl))
                                        setOnPreparedListener { player -> player.isLooping = false; start() }
                                    }
                                }
                            )
                        } else {
                            AsyncImage(
                                model = attachmentUrl,
                                contentDescription = stringResource(R.string.message_attachment_image),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
        NexaOptionRow(
            icon = Icons.Filled.Star,
            label = if (message.isStarred) "إلغاء التمييز بنجمة" else "تمييز بنجمة",
            onClick = onToggleStar
        )
        if (message.text.isNotBlank()) {
                        Spacer(Modifier.height(NexaSpacing.xs))
                    }
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = NexaSpacing.xxs),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isStarred) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Starred",
                            tint = if (mine) contentColor else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (message.editedAt != null) {
                        Text(
                            text = stringResource(R.string.message_edited),
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedColor
                        )
                        Spacer(Modifier.width(NexaSpacing.xs))
                    }
                    Text(
                        text = TimeUtils.relativeFromNow(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor
                    )
                    if (mine) {
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            imageVector = if (message.isRead) {
                                Icons.Filled.DoneAll
                            } else {
                                Icons.Filled.Done
                            },
                            contentDescription = stringResource(
                                if (message.isRead) R.string.message_status_read
                                else R.string.message_status_sent
                            ),
                            tint = if (message.isRead) mutedColor else mutedColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            if (message.reactionList.isNotEmpty()) {
                Spacer(Modifier.height(NexaSpacing.xxs))
                ReactionRow(
                    message = message,
                    currentUserId = currentUserId,
                    onToggleReaction = onToggleReaction,
                    onAddReaction = onOpenActions
                )
            }
        }
    }
}

@Composable
private fun ReactionRow(
    message: Message,
    currentUserId: String?,
    onToggleReaction: (String) -> Unit,
    onAddReaction: () -> Unit
) {
    Row(
        modifier = Modifier.offset(y = (-2).dp),
        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        message.reactionList.groupBy { it.emoji }.forEach { (emoji, reactions) ->
            val mine = reactions.any { it.userId == currentUserId }
            Surface(
                shape = RoundedCornerShape(NexaRadius.pill),
                color = if (mine) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (mine) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    }
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(NexaRadius.pill))
                    .clickable { onToggleReaction(emoji) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = reactions.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (mine) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(NexaRadius.pill),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier
                .clip(RoundedCornerShape(NexaRadius.pill))
                .clickable(onClick = onAddReaction)
        ) {
            Icon(
                imageVector = Icons.Outlined.AddReaction,
                contentDescription = stringResource(R.string.message_action_react),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .size(14.dp)
            )
        }
    }
}

@Composable
private fun DateSeparator(timestamp: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexaSpacing.sm),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(NexaRadius.pill))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.xxs)
        ) {
            Text(
                text = TimeUtils.date(timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun sameDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}


@Composable
private fun MessageActionSheet(
    message: Message,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onReact: (String) -> Unit,
    onEdit: () -> Unit,
    onToggleStar: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onBlock: () -> Unit
) {
    NexaBottomSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.message_actions_title))
        Surface(
            shape = RoundedCornerShape(NexaRadius.pill),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = NexaSpacing.sm)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexaSpacing.xs, vertical = NexaSpacing.xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                reactionChoices.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { onReact(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 26.sp)
                    }
                }
            }
        }
        HorizontalDivider()

        if (message.text.isNotBlank()) {
            NexaOptionRow(
                icon = Icons.Filled.ContentCopy,
                label = stringResource(R.string.message_action_copy),
                onClick = onCopy
            )
        }
        NexaOptionRow(
            icon = Icons.Filled.Star,
            label = if (message.isStarred) "Unstar message" else "Star message",
            onClick = onToggleStar
        )
        if (message.canEdit) {
            NexaOptionRow(
                icon = Icons.Filled.Edit,
                label = stringResource(R.string.message_action_edit),
                onClick = onEdit
            )
        }
        NexaOptionRow(
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.message_action_delete_for_me),
            onClick = onDeleteForMe
        )
        if (message.isMine) {
            NexaOptionRow(
                icon = Icons.Filled.DeleteForever,
                label = stringResource(R.string.message_action_delete_for_everyone),
                destructive = true,
                onClick = onDeleteForEveryone
            )
        }
        NexaOptionRow(
            icon = Icons.Filled.Block,
            label = stringResource(R.string.message_action_block),
            destructive = true,
            onClick = onBlock
        )
    }
}


// Removed MessageActionButton as it was replaced by NexaOptionRow
