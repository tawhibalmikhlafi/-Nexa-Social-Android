package com.nexa.social.feature.post

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nexa.social.R
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.core.ui.failureMessageRes
import com.nexa.social.core.ui.rememberRemoteMode
import com.nexa.social.domain.model.Audience
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.PostComment
import com.nexa.social.domain.model.PostReaction
import com.nexa.social.domain.model.ReportReason
import com.nexa.social.domain.model.User
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.repository.CommentRepository
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaBottomSheet
import com.nexa.social.ui.components.NexaConfirmDialog
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaOptionRow
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTextButton
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.ReactionEmojis
import com.nexa.social.ui.components.ReactionPicker
import com.nexa.social.ui.components.SheetTitle
import com.nexa.social.ui.components.audienceLabel
import com.nexa.social.ui.components.rememberImagePicker
import com.nexa.social.ui.share.sharePost
import com.nexa.social.ui.theme.NexaSpacing
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

private val ComposerEmojis: List<String> = ReactionEmojis + listOf("🔥", "🙏", "👏", "🎉")
private val OpaqueIdPattern = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)

private fun String.looksLikeOpaqueId(): Boolean =
    OpaqueIdPattern.matches(this) ||
        (length >= 24 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == '-' })

private fun UserSummary.preferredName(): String {
    val name = displayName.trim()
    val handle = username.trim()
    return when {
        name.isNotBlank() && !name.looksLikeOpaqueId() -> name
        handle.isNotBlank() && !handle.looksLikeOpaqueId() -> handle
        else -> "Nexa"
    }
}

private fun UserSummary.visibleHandle(): String? {
    val handle = username.trim()
    val name = preferredName()
    return handle.takeIf {
        it.isNotBlank() && !it.looksLikeOpaqueId() && !it.equals(name, ignoreCase = true)
    }?.let { "@$it" }
}

private data class CommentNode(
    val comment: PostComment,
    val depth: Int,
    val replyToName: String?
)

private fun List<PostComment>.toCommentThread(popularFirst: Boolean = false): List<CommentNode> {
    if (isEmpty()) return emptyList()
    val byParent = groupBy { it.parentId }
    val byId = associateBy { it.id }
    val result = mutableListOf<CommentNode>()
    fun ordered(items: List<PostComment>): List<PostComment> =
        if (popularFirst) items.sortedWith(compareByDescending<PostComment> { it.likeCount }.thenByDescending { it.createdAt })
        else items.sortedBy { it.createdAt }
    val visited = mutableSetOf<String>()

    fun append(comment: PostComment, depth: Int) {
        if (!visited.add(comment.id)) return
        result += CommentNode(
            comment = comment,
            depth = depth.coerceAtMost(3),
            replyToName = comment.parentId
                ?.let { byId[it]?.author }
                ?.preferredName()
        )
        ordered(byParent[comment.id].orEmpty()).forEach { append(it, depth + 1) }
    }

    ordered(filter { it.parentId == null || byId[it.parentId] == null })
        .forEach { append(it, 0) }
    sortedBy { it.createdAt }.forEach { if (it.id !in visited) append(it, 0) }
    return result
}

@Composable
private fun relativeTimeLabel(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1L -> stringResource(R.string.time_just_now)
        minutes < 60L -> if (minutes == 1L) {
            stringResource(R.string.time_minute_ago)
        } else {
            stringResource(R.string.time_minutes_ago, minutes)
        }
        hours < 24L -> if (hours == 1L) {
            stringResource(R.string.time_hour_ago)
        } else {
            stringResource(R.string.time_hours_ago, hours)
        }
        days < 7L -> if (days == 1L) {
            stringResource(R.string.time_day_ago)
        } else {
            stringResource(R.string.time_days_ago, days)
        }
        days < 30L -> {
            val weeks = (days / 7L).coerceAtLeast(1L)
            if (weeks == 1L) {
                stringResource(R.string.time_week_ago)
            } else {
                stringResource(R.string.time_weeks_ago, weeks)
            }
        }
        days < 365L -> {
            val months = (days / 30L).coerceAtLeast(1L)
            if (months == 1L) {
                stringResource(R.string.time_month_ago)
            } else {
                stringResource(R.string.time_months_ago, months)
            }
        }
        else -> {
            val years = (days / 365L).coerceAtLeast(1L)
            if (years == 1L) {
                stringResource(R.string.time_year_ago)
            } else {
                stringResource(R.string.time_years_ago, years)
            }
        }
    }
}

@Composable
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: PostDetailViewModel = viewModel(factory = PostDetailViewModel.factory(postId))
) {
    val post by viewModel.post.collectAsStateWithLifecycle()
    val current = post
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val suggestedUsers by viewModel.suggestedUsers.collectAsStateWithLifecycle()
    val canManage by viewModel.canManage.collectAsStateWithLifecycle()
    val canLoadMore by viewModel.canLoadMore.collectAsStateWithLifecycle()
    val reactionsState by viewModel.reactions.collectAsStateWithLifecycle()
    val remoteMode = rememberRemoteMode()
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showMenu by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    var showEditPost by remember { mutableStateOf(false) }
    var showMentionSuggestions by remember { mutableStateOf(false) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var commentText by rememberSaveable { mutableStateOf("") }
    var commentImageUri by remember { mutableStateOf<Uri?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var sendingComment by remember { mutableStateOf(false) }
    var commentError by remember { mutableStateOf<FailureReason?>(null) }
    val commentFocusRequester = remember { FocusRequester() }
    val pickCommentImage = rememberImagePicker { uri ->
        if (uri != null) {
            commentImageUri = uri
            commentError = null
        }
    }

    val mentionQuery = if (showMentionSuggestions) {
        val lastAt = commentText.lastIndexOf('@')
        if (lastAt != -1) commentText.substring(lastAt + 1).trim() else ""
    } else ""

    val filteredUsers = if (mentionQuery.isNotBlank()) {
        suggestedUsers.filter {
            it.username.contains(mentionQuery, ignoreCase = true) ||
            it.displayName.contains(mentionQuery, ignoreCase = true)
        }
    } else {
        suggestedUsers
    }
    var popularComments by rememberSaveable(postId) { mutableStateOf(false) }
    val commentThread = remember(comments, popularComments) { comments.toCommentThread(popularComments) }
    var showAllComments by rememberSaveable(postId) { mutableStateOf(false) }
    val visibleCommentThread = remember(commentThread, showAllComments) {
        if (showAllComments) commentThread else {
            var rootsSeen = 0
            commentThread.takeWhile { node ->
                if (node.depth == 0) rootsSeen++
                rootsSeen <= 2
            }
        }
    }
    var replyingToComment by remember { mutableStateOf<PostComment?>(null) }
    var editingComment by remember { mutableStateOf<PostComment?>(null) }
    var deletingComment by remember { mutableStateOf<PostComment?>(null) }

    val submitComment: () -> Unit = {
        val text = commentText.trim()
        val image = commentImageUri?.toString()
        if (!sendingComment && (text.isNotEmpty() || image != null)) {
            sendingComment = true
            commentError = null
            viewModel.addComment(text, replyingToComment?.id, image) { reason ->
                sendingComment = false
                if (reason == null) {
                    commentText = ""
                    commentImageUri = null
                    replyingToComment = null
                    showMentionSuggestions = false
                    showEmojiPicker = false
                } else {
                    commentError = reason
                }
            }
        }
    }

    val hiddenMessage = stringResource(R.string.post_hide_done)
    val reportMessage = stringResource(R.string.post_report_sent)
    val shareFailedMessage = stringResource(R.string.post_share_failed)
    val actionFailedMessage = stringResource(R.string.comment_action_failed)
    val linkCopiedMessage = stringResource(R.string.post_link_copied)
    val textCopiedMessage = stringResource(R.string.post_text_copied)
    val postUpdatedMessage = stringResource(R.string.post_updated_success)
    val imageSavedMessage = stringResource(R.string.image_saved_success)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) {
        NexaTopBar(
            title = stringResource(R.string.post_detail_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.post_menu))
                }
            }
        )

        if (current == null) {
            NexaEmptyState(
                icon = Icons.Filled.ChatBubbleOutline,
                title = stringResource(R.string.state_empty_generic),
                body = stringResource(R.string.notice_local_data_body)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = NexaSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
            ) {
                item { PostHeader(current, onOpenProfile, onMenu = { showMenu = true }) }
                if (current.text.isNotBlank()) {
                    item {
                        Text(
                            text = current.text,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = NexaSpacing.xs)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Nexa Post Text", current.text))
                                    snackbarHost?.let { host -> scope.launch { host.showSnackbar(textCopiedMessage) } }
                                }
                        )
                    }
                }
                if (current.tags.isNotEmpty()) {
                    item {
                        Text(
                            text = current.tags.joinToString(" ") { "#$it" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                items(current.allImages().filter { it.isNotBlank() }) { uri ->
                    AsyncImage(
                        model = Uri.parse(uri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { fullscreenImageUrl = uri }
                    )
                }
                item {
                    PostActions(
                        post = current,
                        commentCount = maxOf(current.commentCount, comments.size),
                        onLike = { viewModel.toggleLike() },
                        onReaction = { emoji -> viewModel.toggleLike(emoji) },
                        onComment = { runCatching { commentFocusRequester.requestFocus() } },
                        onShare = {
                            if (!sharePost(context, postId, current.text)) {
                                snackbarHost?.let { host -> scope.launch { host.showSnackbar(shareFailedMessage) } }
                            }
                        },
                        onSave = viewModel::toggleSave,
                        onOpenReactions = {
                            showReactions = true
                            viewModel.loadReactions(force = true)
                        }
                    )
                }
                if (!remoteMode) {
                    item { LocalDataBanner(text = stringResource(R.string.post_comments_local_notice)) }
                }
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = NexaSpacing.sm),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                    Text(
                        text = "${stringResource(R.string.post_comments_title)} · " +
                            maxOf(current.commentCount, comments.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)) {
                        FilterChip(
                            selected = !popularComments,
                            onClick = { popularComments = false },
                            label = { Text(stringResource(R.string.comment_sort_newest)) }
                        )
                        FilterChip(
                            selected = popularComments,
                            onClick = { popularComments = true },
                            label = { Text(stringResource(R.string.comment_sort_popular)) }
                        )
                    }
                }
                if (comments.isEmpty()) {
                    item {
                        NexaEmptyState(
                            icon = Icons.Filled.ChatBubbleOutline,
                            title = stringResource(R.string.post_comment_empty),
                            body = stringResource(R.string.post_comment_empty_body)
                        )
                    }
                } else {
                    items(visibleCommentThread, key = { it.comment.id }) { node ->
                        CommentRow(
                            comment = node.comment,
                            depth = node.depth,
                            replyToName = node.replyToName,
                            onOpenProfile = onOpenProfile,
                            onToggleLike = { viewModel.toggleCommentLike(node.comment.id) },
                            onReply = { replyingToComment = node.comment },
                            onEdit = { editingComment = node.comment },
                            onDelete = { deletingComment = node.comment },
                            onOpenImage = { fullscreenImageUrl = it }
                        )
                    }
                }
                if (!showAllComments && visibleCommentThread.size < commentThread.size) {
                    item {
                        NexaTextButton(
                            text = stringResource(R.string.comment_show_all, commentThread.size),
                            onClick = { showAllComments = true }
                        )
                    }
                } else if (showAllComments && commentThread.count { it.depth == 0 } > 2) {
                    item {
                        NexaTextButton(
                            text = stringResource(R.string.comment_show_less),
                            onClick = { showAllComments = false }
                        )
                    }
                }
                if (canLoadMore) {
                    item {
                        NexaTextButton(
                            text = stringResource(R.string.comment_load_more),
                            onClick = viewModel::loadMoreComments
                        )
                    }
                }
                item { Spacer(Modifier.height(NexaSpacing.sm)) }
            }
            CommentComposer(
                text = commentText,
                onTextChange = { value ->
                    if (value.length <= CommentRepository.MAX_COMMENT_LENGTH) {
                        commentText = value
                        showMentionSuggestions = value.endsWith("@") ||
                            (value.contains("@") && !value.endsWith(" "))
                        commentError = null
                    }
                },
                replyingTo = replyingToComment,
                onCancelReply = { replyingToComment = null },
                mentionSuggestions = filteredUsers,
                showMentions = showMentionSuggestions,
                onMentionSelected = { user ->
                    commentText = commentText.substringBeforeLast("@") + "@${user.username} "
                    showMentionSuggestions = false
                },
                selectedImage = commentImageUri,
                onPickImage = pickCommentImage,
                onClearImage = { commentImageUri = null },
                showEmojiPicker = showEmojiPicker,
                onToggleEmoji = { showEmojiPicker = !showEmojiPicker },
                onEmojiSelected = { emoji ->
                    if (commentText.length < CommentRepository.MAX_COMMENT_LENGTH) {
                        commentText += emoji
                    }
                },
                sending = sendingComment,
                error = commentError,
                onDismissError = { commentError = null },
                onRetry = {
                    commentError = null
                    submitComment()
                },
                onSend = submitComment,
                focusRequester = commentFocusRequester
            )
        }
    }

    editingComment?.let { comment ->
        EditCommentDialog(
            initialText = comment.text,
            onDismiss = { editingComment = null },
            onSave = { text ->
                viewModel.editComment(comment.id, text) { reason ->
                    editingComment = null
                    if (reason != null) {
                        snackbarHost?.let { host ->
                            scope.launch {
                                host.showSnackbar(context.getString(failureMessageRes(reason)))
                            }
                        }
                    }
                }
            }
        )
    }

    if (showDelete) {
        NexaConfirmDialog(
            title = stringResource(R.string.post_delete),
            message = stringResource(R.string.post_delete_confirm),
            destructive = true,
            confirmLabel = stringResource(R.string.post_delete),
            onConfirm = {
                showDelete = false
                viewModel.delete { deleted ->
                    if (deleted) {
                        onBack()
                    } else {
                        snackbarHost?.let { host ->
                            scope.launch { host.showSnackbar(actionFailedMessage) }
                        }
                    }
                }
            },
            onDismiss = { showDelete = false }
        )
    }

    if (showBlockConfirm && current != null) {
        NexaConfirmDialog(
            title = stringResource(R.string.post_block_author),
            message = stringResource(R.string.post_block_confirm),
            destructive = true,
            confirmLabel = stringResource(R.string.post_block_author),
            onConfirm = {
                showBlockConfirm = false
                viewModel.blockAuthor(current.author) { success ->
                    scope.launch {
                        snackbarHost?.showSnackbar(
                            context.getString(if (success) R.string.post_block_done else R.string.comment_action_failed)
                        )
                        if (success) onBack()
                    }
                }
            },
            onDismiss = { showBlockConfirm = false }
        )
    }

    deletingComment?.let { comment ->
        NexaConfirmDialog(
            title = stringResource(R.string.comment_delete_confirm_title),
            message = stringResource(R.string.comment_delete_confirm_body),
            destructive = true,
            onConfirm = {
                deletingComment = null
                viewModel.deleteComment(comment.id) { deleted ->
                    if (!deleted) {
                        snackbarHost?.let { host ->
                            scope.launch { host.showSnackbar(actionFailedMessage) }
                        }
                    }
                }
            },
            onDismiss = { deletingComment = null }
        )
    }

    if (showMenu) {
        NexaBottomSheet(onDismiss = { showMenu = false }) {
            SheetTitle(stringResource(R.string.post_menu))
            NexaOptionRow(
                icon = if (current?.savedByMe == true) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                label = stringResource(if (current?.savedByMe == true) R.string.post_unsave else R.string.post_save),
                onClick = {
                    showMenu = false
                    viewModel.toggleSave()
                }
            )
            NexaOptionRow(icon = Icons.Filled.ContentCopy, label = stringResource(R.string.post_copy_link), onClick = {
                showMenu = false
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Nexa Post", "nexa://post/$postId"))
                snackbarHost?.let { host -> scope.launch { host.showSnackbar(linkCopiedMessage) } }
            })
            NexaOptionRow(icon = Icons.Filled.Share, label = stringResource(R.string.post_share), onClick = {
                showMenu = false
                current?.let {
                    if (!sharePost(context, postId, it.text)) {
                        snackbarHost?.let { host -> scope.launch { host.showSnackbar(shareFailedMessage) } }
                    }
                }
            })
            if (!canManage && current != null) {
                NexaOptionRow(icon = Icons.Filled.Flag, label = stringResource(R.string.post_report), onClick = {
                    showMenu = false
                    showReport = true
                })
                NexaOptionRow(
                    icon = Icons.Filled.VisibilityOff,
                NexaOptionRow(
                    icon = Icons.Filled.VisibilityOff,
                    label = stringResource(R.string.post_unfollow_author),
                    onClick = {
                        showMenu = false
                        viewModel.unfollowAuthor(current.author.id) { success ->
                            scope.launch {
                                snackbarHost?.showSnackbar(
                                    context.getString(if (success) R.string.post_unfollow_done else R.string.comment_action_failed)
                                )
                            }
                        }
                    }
                )
                NexaOptionRow(
                    icon = Icons.Filled.Flag,
                    label = stringResource(R.string.post_block_author),
                    destructive = true,
                    onClick = {
                        showMenu = false
                        showBlockConfirm = true
                    }
                )
            }
            if (!canManage) {
                NexaOptionRow(icon = Icons.Filled.VisibilityOff, label = stringResource(R.string.post_hide), onClick = {
                    showMenu = false
                    viewModel.hide {
                        snackbarHost?.let { host -> scope.launch { host.showSnackbar(hiddenMessage) } }
                    }
                })
            }
            if (canManage) {
                NexaOptionRow(
                    icon = Icons.Filled.Edit,
                    label = stringResource(R.string.action_edit),
                    onClick = {
                        showMenu = false
                        showEditPost = true
                    }
                )
                NexaOptionRow(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.post_delete),
                    destructive = true,
                    onClick = {
                        showMenu = false
                        showDelete = true
                    }
                )
            }
        }
    }

    if (showEditPost) {
        current?.let { postToEdit ->
            EditPostDialog(
                initialPost = postToEdit,
                onDismiss = { showEditPost = false },
                onSave = { newText, newTags, newAudience ->
                    viewModel.updatePost(newText, postToEdit.imageUris, newTags, newAudience) { success ->
                        showEditPost = false
                        if (success) {
                            scope.launch { snackbarHost?.showSnackbar(postUpdatedMessage) }
                        } else {
                            scope.launch { snackbarHost?.showSnackbar(actionFailedMessage) }
                        }
                    }
                }
            )
        }
    }

    fullscreenImageUrl?.let { url ->
        FullscreenImageViewer(
            imageUrl = url,
            onDismiss = { fullscreenImageUrl = null },
            onSaveImage = {
                snackbarHost?.let { host -> scope.launch { host.showSnackbar(imageSavedMessage) } }
            }
        )
    }

    if (showReport) {
        NexaBottomSheet(onDismiss = { showReport = false }) {
            SheetTitle(stringResource(R.string.post_report_title))
            Text(
                text = stringResource(R.string.post_report_local_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(NexaSpacing.sm))
            ReportReason.entries.forEach { reason ->
                NexaOptionRow(
                    icon = Icons.Filled.Flag,
                    label = reportReasonLabel(reason),
                    onClick = {
                        showReport = false
                        viewModel.report(reason, "") {
                            snackbarHost?.let { host -> scope.launch { host.showSnackbar(reportMessage) } }
                        }
                    }
                )
            }
        }
    }

    if (showReactions) {
        ReactionsSheet(
            state = reactionsState,
            total = maxOf(current?.likeCount ?: 0, reactionsState.reactions.size),
            remoteMode = remoteMode,
            onDismiss = { showReactions = false },
            onRetry = { viewModel.loadReactions(force = true) },
            onOpenProfile = { userId ->
                showReactions = false
                onOpenProfile(userId)
            }
        )
    }
}

@Composable
private fun ReactionsSheet(
    state: PostReactionsUiState,
    total: Int,
    remoteMode: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    var selectedEmoji by rememberSaveable { mutableStateOf<String?>(null) }
    val counts = remember(state.reactions) { state.reactions.groupingBy { it.emoji }.eachCount() }
    val emojis = remember(counts) {
        ReactionEmojis.filter { (counts[it] ?: 0) > 0 } + counts.keys.filterNot { it in ReactionEmojis }
    }
    val filtered = remember(state.reactions, selectedEmoji) {
        selectedEmoji?.let { emoji -> state.reactions.filter { it.emoji == emoji } } ?: state.reactions
    }
    val headerTotal = maxOf(total, state.reactions.size)

    NexaBottomSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reactions_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.reactions_total, headerTotal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)) {
                emojis.take(4).forEach { emoji ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(NexaSpacing.md))
        if (state.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
            )
            Spacer(Modifier.height(NexaSpacing.sm))
        }
        if (state.error != null) {
            Text(
                text = stringResource(R.string.reactions_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            NexaTextButton(
                text = stringResource(R.string.reactions_retry),
                onClick = onRetry
            )
        } else if (!remoteMode && state.reactions.isEmpty()) {
            Text(
                text = stringResource(R.string.reactions_local_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (emojis.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                ) {
                    item {
                        FilterChip(
                            selected = selectedEmoji == null,
                            onClick = { selectedEmoji = null },
                            label = { Text(stringResource(R.string.reactions_all)) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                    items(emojis, key = { it }) { emoji ->
                        FilterChip(
                            selected = selectedEmoji == emoji,
                            onClick = { selectedEmoji = if (selectedEmoji == emoji) null else emoji },
                            label = { Text((counts[emoji] ?: 0).toString()) },
                            leadingIcon = {
                                Text(text = emoji, style = MaterialTheme.typography.titleSmall)
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
                Spacer(Modifier.height(NexaSpacing.sm))
            }
            when {
                state.isLoading && filtered.isEmpty() -> Unit
                filtered.isEmpty() -> NexaEmptyState(
                    icon = Icons.Filled.FavoriteBorder,
                    title = stringResource(R.string.reactions_empty_title),
                    body = stringResource(R.string.reactions_empty_body),
                    action = {
                        NexaTextButton(
                            text = stringResource(R.string.reactions_retry),
                            onClick = onRetry
                        )
                    }
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                ) {
                    items(filtered, key = { it.user.id }) { reaction ->
                        ReactionRow(reaction = reaction, onOpenProfile = onOpenProfile)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionRow(
    reaction: PostReaction,
    onOpenProfile: (String) -> Unit
) {
    val name = reaction.user.preferredName()
    val handle = reaction.user.visibleHandle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenProfile(reaction.user.id) }
            .padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md)
    ) {
        Box {
            NexaAvatar(
                displayName = name,
                avatarUri = reaction.user.avatarUri,
                size = 44.dp
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = reaction.emoji, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (handle != null) {
                Text(
                    text = handle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = relativeTimeLabel(reaction.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun PostHeader(
    post: Post,
    onOpenProfile: (String) -> Unit,
    onMenu: () -> Unit
) {
    val authorName = post.author.preferredName()
    val handle = post.author.visibleHandle()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    ) {
        Row(
            modifier = Modifier.padding(NexaSpacing.md),
            verticalAlignment = Alignment.Top
        ) {
            NexaAvatar(
                displayName = authorName,
                avatarUri = post.author.avatarUri,
                size = 52.dp,
                modifier = Modifier.clickable { onOpenProfile(post.author.id) }
            )
            Spacer(Modifier.width(NexaSpacing.md))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenProfile(post.author.id) }
            ) {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (handle != null) {
                    Text(
                        text = handle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(NexaSpacing.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                ) {
                    Text(
                        text = relativeTimeLabel(post.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = audienceLabel(post.audience),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (post.isEdited) {
                        Text(
                            text = "· ${stringResource(R.string.post_edited)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = stringResource(R.string.post_menu),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostActions(
    post: Post,
    commentCount: Int,
    onLike: () -> Unit,
    onReaction: (String) -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onOpenReactions: () -> Unit
) {
    var showReactionPicker by remember { mutableStateOf(false) }
    val reaction = post.myReaction ?: if (post.likedByMe) "❤️" else null
    val likeScale by animateFloatAsState(
        targetValue = if (reaction != null) 1.15f else 1f,
        label = "post-like-scale"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NexaSpacing.xs, vertical = NexaSpacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box {
                    LikeActionItem(
                        icon = Icons.Filled.FavoriteBorder,
                        count = post.likeCount,
                        reaction = reaction,
                        contentDescription = stringResource(R.string.post_like),
                        active = reaction != null,
                        scale = likeScale,
                        onToggle = onLike,
                        onOpenReactions = onOpenReactions,
                        onLongPress = { showReactionPicker = true }
                    )
                    ReactionPicker(
                        expanded = showReactionPicker,
                        selected = post.myReaction,
                        onDismiss = { showReactionPicker = false },
                        onSelect = { emoji ->
                            showReactionPicker = false
                            onReaction(emoji)
                        }
                    )
                }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ActionItem(
                    icon = Icons.Filled.ChatBubbleOutline,
                    label = commentCount.toString(),
                    contentDescription = stringResource(R.string.post_comment),
                    active = false,
                    onClick = onComment
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ActionItem(
                    icon = Icons.AutoMirrored.Filled.Send,
                    label = post.shareCount.toString(),
                    contentDescription = stringResource(R.string.post_share),
                    active = false,
                    onClick = onShare
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ActionItem(
                    icon = if (post.savedByMe) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    label = null,
                    contentDescription = stringResource(R.string.post_save),
                    active = post.savedByMe,
                    onClick = onSave
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LikeActionItem(
    icon: ImageVector,
    count: Int,
    reaction: String?,
    contentDescription: String,
    active: Boolean,
    scale: Float,
    onToggle: () -> Unit,
    onOpenReactions: () -> Unit,
    onLongPress: () -> Unit
) {
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .padding(horizontal = NexaSpacing.xs, vertical = NexaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
                .padding(NexaSpacing.xs),
            contentAlignment = Alignment.Center
        ) {
            if (reaction != null) {
                Text(
                    text = reaction,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onOpenReactions)
                .padding(horizontal = NexaSpacing.xs, vertical = NexaSpacing.xs)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String?,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    scale: Float = 1f,
    onLongClick: (() -> Unit)? = null
) {
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (emoji != null) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        if (!label.isNullOrEmpty()) {
            Spacer(Modifier.width(NexaSpacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    comment: PostComment,
    depth: Int,
    replyToName: String?,
    onOpenProfile: (String) -> Unit,
    onToggleLike: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenImage: (String) -> Unit
) {
    val editedLabel = stringResource(R.string.comment_edited)
    val authorName = comment.author.preferredName()
    val handle = comment.author.visibleHandle()
    val imageUrl = comment.imageUri
    var showActions by remember { mutableStateOf(false) }
    val indent = (depth.coerceAtMost(3) * 16).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = NexaSpacing.xs, bottom = NexaSpacing.xs),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
    ) {
        NexaAvatar(
            displayName = authorName,
            avatarUri = comment.author.avatarUri,
            size = if (depth == 0) 38.dp else 32.dp,
            modifier = Modifier.clickable { onOpenProfile(comment.author.id) }
        )
        Column(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm + 2.dp)
            ) {
                if (replyToName != null) {
                    Text(
                        text = stringResource(R.string.comment_reply_to_name, replyToName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(NexaSpacing.xs))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { onOpenProfile(comment.author.id) }
                    )
                    if (handle != null) {
                        Spacer(Modifier.width(NexaSpacing.xs))
                        Text(
                            text = handle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (comment.text.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = comment.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!imageUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = stringResource(R.string.comment_image_desc),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenImage(imageUrl) }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = NexaSpacing.xs, top = NexaSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
            ) {
                Text(
                    text = relativeTimeLabel(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (comment.isEdited) {
                    Text(
                        text = "· $editedLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                if (comment.likeCount > 0 || comment.likedByMe) {
                    Row(
                        modifier = Modifier.padding(horizontal = NexaSpacing.xs, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.post_like),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = comment.likeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { showActions = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreHoriz,
                            contentDescription = stringResource(R.string.comment_actions),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (comment.likedByMe) R.string.comment_unlike else R.string.post_like
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (comment.likedByMe) Icons.Filled.Favorite
                                    else Icons.Filled.FavoriteBorder,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showActions = false
                                onToggleLike()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.comment_reply)) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                            },
                            onClick = {
                                showActions = false
                                onReply()
                            }
                        )
                        if (comment.isMine) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.comment_edit)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    showActions = false
                                    onEdit()
                                }
                            )
                        }
                        if (comment.canDelete) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.post_comment_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showActions = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentComposer(
    text: String,
    onTextChange: (String) -> Unit,
    replyingTo: PostComment?,
    onCancelReply: () -> Unit,
    mentionSuggestions: List<User>,
    showMentions: Boolean,
    onMentionSelected: (User) -> Unit,
    selectedImage: Uri?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    showEmojiPicker: Boolean,
    onToggleEmoji: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    sending: Boolean,
    error: FailureReason?,
    onDismissError: () -> Unit,
    onRetry: () -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm)
        ) {
            if (sending) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(NexaSpacing.sm))
                    Text(
                        text = stringResource(R.string.comment_sending),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(NexaSpacing.xs))
            }
            if (replyingTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(NexaSpacing.xs))
                    Text(
                        text = "${stringResource(R.string.comment_replying_to)} ${replyingTo.author.preferredName()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onCancelReply, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(NexaSpacing.xs))
            }
            if (showMentions && mentionSuggestions.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = NexaSpacing.xs, vertical = NexaSpacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                ) {
                    items(mentionSuggestions.take(8), key = { it.id }) { user ->
                        TextButton(onClick = { onMentionSelected(user) }) {
                            Text(
                                text = "@${user.username}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(NexaSpacing.xs))
            }
            if (showEmojiPicker) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                ) {
                    items(ComposerEmojis) { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { onEmojiSelected(emoji) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(NexaSpacing.xs))
            }
            if (selectedImage != null) {
                Box(modifier = Modifier.size(84.dp)) {
                    AsyncImage(
                        model = selectedImage,
                        contentDescription = stringResource(R.string.comment_image_desc),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                    )
                    IconButton(
                        onClick = onClearImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .background(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.comment_remove_image),
                            modifier = Modifier.size(14.dp),
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
                Spacer(Modifier.height(NexaSpacing.xs))
            }
            if (error != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(failureMessageRes(error)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetry, enabled = !sending) {
                        Text(stringResource(R.string.comment_retry))
                    }
                    IconButton(
                        onClick = onDismissError,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = onPickImage,
                    enabled = !sending,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = stringResource(R.string.comment_add_image),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onToggleEmoji,
                    enabled = !sending,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.EmojiEmotions,
                        contentDescription = stringResource(R.string.comment_emoji),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = { onTextChange(text + "@") },
                    enabled = !sending,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.AlternateEmail,
                        contentDescription = stringResource(R.string.mention_friend),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            if (replyingTo != null) stringResource(R.string.post_reply_hint)
                            else stringResource(R.string.post_comment_hint)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Default
                    )
                )
                IconButton(
                    onClick = onSend,
                    enabled = !sending && (text.isNotBlank() || selectedImage != null),
                    modifier = Modifier.size(44.dp)
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.action_send),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionsPickerSheet(
    onDismiss: () -> Unit,
    onSelectEmoji: (String) -> Unit
) {
    NexaBottomSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.reactions_title))
        Spacer(Modifier.height(NexaSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("👍", "❤️", "😂", "😮", "😢", "🔥").forEach { emoji ->
                IconButton(
                    onClick = { onSelectEmoji(emoji) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Spacer(Modifier.height(NexaSpacing.xl))
    }
}

@Composable
private fun FullscreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
    onSaveImage: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .clickable(onClick = {}) // intercept clicks
    ) {
        AsyncImage(
            model = Uri.parse(imageUrl),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = maxOf(1f, scale),
                    scaleY = maxOf(1f, scale),
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
        )

        Row(
            modifier = Modifier
                .padding(NexaSpacing.lg)
                .align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
        ) {
            IconButton(
                onClick = onSaveImage,
                modifier = Modifier
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = stringResource(R.string.save_image),
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}

@Composable
private fun EditPostDialog(
    initialPost: Post,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, Audience) -> Unit
) {
    var text by remember(initialPost) { mutableStateOf(initialPost.text) }
    var tagsText by remember(initialPost) { mutableStateOf(initialPost.tags.joinToString(", ")) }
    var audience by remember(initialPost) { mutableStateOf(initialPost.audience) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.post_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                NexaTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.post_content_hint),
                    singleLine = false
                )
                NexaTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = stringResource(R.string.post_tags_hint)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedTags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onSave(text.trim(), parsedTags, audience)
                },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}


@Composable
private fun EditCommentDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    val tooLong = text.length > CommentRepository.MAX_COMMENT_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.comment_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                NexaTextField(
                    value = text,
                    onValueChange = { value ->
                        if (value.length <= CommentRepository.MAX_COMMENT_LENGTH) text = value
                    },
                    label = stringResource(R.string.post_comment_hint),
                    singleLine = false,
                    errorText = if (tooLong) stringResource(R.string.comment_too_long) else null
                )
                Text(
                    text = "${text.length}/${CommentRepository.MAX_COMMENT_LENGTH}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim()) },
                enabled = text.isNotBlank() && !tooLong
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun reportReasonLabel(reason: ReportReason): String = when (reason) {
    ReportReason.SPAM -> stringResource(R.string.report_reason_spam)
    ReportReason.HARASSMENT -> stringResource(R.string.report_reason_harassment)
    ReportReason.HATE_SPEECH -> stringResource(R.string.report_reason_hate)
    ReportReason.MISINFORMATION -> stringResource(R.string.report_reason_misinformation)
    ReportReason.SEXUAL_CONTENT -> stringResource(R.string.report_reason_sexual)
    ReportReason.VIOLENCE -> stringResource(R.string.report_reason_violence)
    ReportReason.SELF_HARM -> stringResource(R.string.report_reason_self_harm)
    ReportReason.IMPERSONATION -> stringResource(R.string.report_reason_impersonation)
    ReportReason.OTHER -> stringResource(R.string.report_reason_other)
}
