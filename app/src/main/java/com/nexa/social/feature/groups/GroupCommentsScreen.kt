package com.nexa.social.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.domain.model.GroupComment
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaRadius
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun GroupCommentsScreen(
    groupId: String,
    postId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: GroupCommentsViewModel = viewModel(
        factory = GroupCommentsViewModel.factory(groupId, postId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<String?>(null) }
    var replyToName by remember { mutableStateOf<String?>(null) }
    val threaded = remember(state.comments) { threadComments(state.comments) }

    val title = if (state.comments.isEmpty()) {
        stringResource(R.string.groups_comments_title)
    } else {
        stringResource(R.string.groups_comments_title_count, state.comments.size)
    }

    Scaffold(
        topBar = {
            NexaTopBar(
                title = title,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            when {
                state.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.comments.isEmpty() -> EmptyComments(
                    error = state.error != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = NexaSpacing.lg,
                        vertical = NexaSpacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.lg),
                ) {
                    if (state.error != null) {
                        item {
                            ErrorPill(
                                text = when (state.error) {
                                    "comment_failed" -> stringResource(R.string.groups_comment_failed)
                                    "delete_failed" -> stringResource(R.string.groups_delete_failed)
                                    else -> stringResource(R.string.groups_comments_failed)
                                }
                            )
                        }
                    }
                    items(threaded, key = { it.id }) { comment ->
                        CommentItem(
                            comment = comment,
                            canDelete = comment.isAuthor || state.canManage,
                            onOpenProfile = { onOpenProfile(comment.authorId) },
                            onReply = {
                                replyToId = comment.parentId ?: comment.id
                                replyToName = comment.authorName
                            },
                            onDelete = { viewModel.delete(comment.id) },
                        )
                    }
                }
            }

            if (replyToName != null) {
                ReplyBanner(
                    name = replyToName.orEmpty(),
                    onCancel = {
                        replyToId = null
                        replyToName = null
                    },
                )
            }

            Composer(
                text = text,
                onTextChange = { text = it },
                enabled = text.isNotBlank() && !state.isSaving,
                onSend = {
                    viewModel.add(text, replyToId)
                    text = ""
                    replyToId = null
                    replyToName = null
                },
            )
        }
    }
}

/** Keeps each reply directly under its parent while preserving chronological order. */
private fun threadComments(comments: List<GroupComment>): List<GroupComment> {
    val ids = comments.map { it.id }.toSet()
    val repliesByParent = comments
        .filter { it.parentId != null }
        .groupBy { it.parentId }
    val ordered = mutableListOf<GroupComment>()
    val seen = mutableSetOf<String>()

    comments
        .filter { comment ->
            val parent = comment.parentId
            parent == null || parent !in ids
        }
        .forEach { root ->
            if (seen.add(root.id)) ordered += root
            repliesByParent[root.id]
                ?.sortedBy { it.createdAt }
                ?.forEach { reply ->
                    if (seen.add(reply.id)) ordered += reply
                }
        }
    comments.forEach { comment ->
        if (seen.add(comment.id)) ordered += comment
    }
    return ordered
}

@Composable
private fun EmptyComments(error: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(NexaSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.size(NexaSpacing.md))
            Text(
                text = stringResource(R.string.groups_comments_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error) {
                Spacer(Modifier.size(NexaSpacing.xs))
                Text(
                    text = stringResource(R.string.groups_comments_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ErrorPill(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexaRadius.chip))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun CommentItem(
    comment: GroupComment,
    canDelete: Boolean,
    onOpenProfile: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    val isReply = comment.parentId != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) NexaSpacing.xxl else 0.dp),
        verticalAlignment = Alignment.Top,
    ) {
        NexaAvatar(
            displayName = comment.authorName,
            avatarUri = comment.authorAvatarUrl,
            size = if (isReply) 34.dp else 42.dp,
            modifier = Modifier.clickable(onClick = onOpenProfile),
        )
        Spacer(Modifier.width(NexaSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isReply) 18.dp else 6.dp,
                            topEnd = 20.dp,
                            bottomEnd = 20.dp,
                            bottomStart = 20.dp,
                        )
                    )
                    .background(
                        if (isReply) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
                    )
                    .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.md),
            ) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenProfile),
                )
                Spacer(Modifier.size(NexaSpacing.xxs))
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = NexaSpacing.sm, top = NexaSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = TimeUtils.relativeFromNow(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(NexaSpacing.sm))
                Text(
                    text = stringResource(R.string.groups_reply),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(NexaRadius.pill))
                        .clickable(onClick = onReply)
                        .padding(horizontal = NexaSpacing.sm, vertical = 2.dp),
                )
                Spacer(Modifier.weight(1f))
                if (canDelete) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.groups_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyBanner(name: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(NexaRadius.pill))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .padding(start = NexaSpacing.md, end = NexaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.groups_replying_to, name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.groups_cancel_reply),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
            .padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.md),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.groups_comment_hint)) },
            modifier = Modifier.weight(1f),
            maxLines = 4,
            shape = RoundedCornerShape(22.dp),
        )
        Spacer(Modifier.width(NexaSpacing.sm))
        FilledIconButton(
            onClick = onSend,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.groups_comment_send),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
