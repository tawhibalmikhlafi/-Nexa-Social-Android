package com.nexa.social.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.domain.model.Audience
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.PostComment
import com.nexa.social.ui.theme.NexaRadius
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun audienceLabel(audience: Audience): String = when (audience) {
    Audience.PUBLIC_LOCAL -> stringResource(R.string.audience_public)
    Audience.FOLLOWERS -> stringResource(R.string.audience_followers)
    Audience.ONLY_ME -> stringResource(R.string.audience_private)
}

@Composable
fun PostCard(
    post: Post,
    onLike: () -> Unit,
    onReaction: (String) -> Unit = { onLike() },
    onComment: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onOpenProfile: (String) -> Unit = {},
    onOpenPost: () -> Unit = {},
    modifier: Modifier = Modifier,
    commentPreview: List<PostComment> = emptyList()
) {
    var expanded by remember(post.id) { mutableStateOf(false) }
    var textOverflowing by remember(post.id) { mutableStateOf(false) }
    val editedLabel = stringResource(R.string.post_edited)
    val authorName = post.author.displayName.trim().takeIf { it.isNotBlank() } ?: post.author.username
    val meta = buildString {
        append(TimeUtils.relativeFromNow(post.createdAt))
        if (post.isEdited) {
            append(" · ")
            append(editedLabel)
        }
    }

    NexaCard(modifier = modifier, padding = 0) {
        Column(modifier = Modifier.padding(NexaSpacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NexaRadius.chip))
                    .clickable { onOpenProfile(post.author.id) }
                    .padding(vertical = NexaSpacing.xs, horizontal = NexaSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexaAvatar(
                    displayName = authorName,
                    avatarUri = post.author.avatarUri,
                    size = 46.dp
                )
                Spacer(Modifier.width(NexaSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (post.pinned) {
                            Spacer(Modifier.width(NexaSpacing.xs))
                            PinnedBadge()
                        }
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    AudienceBadge(post.audience)
                }
                IconButton(
                    onClick = onOpenPost,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.post_menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (!post.title.isNullOrBlank()) {
                Spacer(Modifier.height(NexaSpacing.md))
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPost() }
                )
            }

            if (post.text.isNotBlank()) {
                Spacer(Modifier.height(NexaSpacing.md))
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layout ->
                        if (!expanded && layout.hasVisualOverflow) textOverflowing = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPost() }
                )
                if (!expanded && textOverflowing) {
                    Text(
                        text = stringResource(R.string.post_see_more),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(NexaRadius.pill))
                            .clickable { expanded = true }
                            .padding(vertical = NexaSpacing.xs)
                    )
                }
            }

            if (post.tags.isNotEmpty()) {
                Spacer(Modifier.height(NexaSpacing.sm))
                Text(
                    text = post.tags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        val images = post.allImages().filter { it.isNotBlank() }
        if (images.isNotEmpty()) {
            PostImageGrid(
                images = images,
                onOpenPost = onOpenPost,
                modifier = Modifier.padding(horizontal = NexaSpacing.lg)
            )
        }

        if (post.likeCount > 0 || post.commentCount > 0 || post.shareCount > 0) {
            Spacer(Modifier.height(NexaSpacing.md))
            PostStats(post)
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = NexaSpacing.md),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.xs)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionLikeButton(
                likedByMe = post.likedByMe,
                myReaction = post.myReaction,
                onLike = onLike,
                onReaction = onReaction,
                modifier = Modifier.weight(1f)
            )
            PostAction(
                icon = Icons.Filled.ChatBubbleOutline,
                label = stringResource(R.string.post_comment),
                contentDescription = stringResource(R.string.post_comment),
                onClick = onComment,
                modifier = Modifier.weight(1f)
            )
            PostAction(
                icon = Icons.AutoMirrored.Filled.Send,
                label = stringResource(R.string.post_share),
                contentDescription = stringResource(R.string.post_share),
                onClick = onShare,
                modifier = Modifier.weight(1f)
            )
            PostAction(
                icon = if (post.savedByMe) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                label = stringResource(R.string.post_save),
                tint = if (post.savedByMe) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = stringResource(R.string.post_save),
                onClick = onSave,
                modifier = Modifier.weight(1f)
            )
        }
        if (commentPreview.isNotEmpty() || post.commentCount > 0) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.xs),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
            ) {
                commentPreview.take(2).forEach { comment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
                            .clickable { onOpenPost() }
                            .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        NexaAvatar(
                            displayName = comment.author.displayName.ifBlank { comment.author.username },
                            avatarUri = comment.author.avatarUri,
                            size = 34.dp
                        )
                        Spacer(Modifier.width(NexaSpacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = comment.author.displayName.ifBlank { comment.author.username },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = comment.text.ifBlank { stringResource(R.string.comment_image_only) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (comment.likeCount > 0) {
                            Text(
                                text = "♥ ${comment.likeCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.post_view_all_comments, maxOf(post.commentCount, commentPreview.size)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { onOpenPost() }
                        .padding(vertical = NexaSpacing.xs)
                )
            }
        }
    }
}

@Composable
private fun PostStats(post: Post) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (post.likeCount > 0) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(NexaRadius.pill))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(horizontal = NexaSpacing.sm, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = post.likeCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (post.commentCount > 0) {
            Text(
                text = stringResource(R.string.post_comments_count, post.commentCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (post.shareCount > 0) {
            Spacer(Modifier.width(NexaSpacing.md))
            Text(
                text = stringResource(R.string.post_shares_count, post.shareCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PostImageGrid(
    images: List<String>,
    onOpenPost: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = images.take(4)
    val extra = images.size - shown.size
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (shown.size) {
            1 -> PostImage(
                uri = shown[0],
                onOpenPost = onOpenPost,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
            )
            2 -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                shown.forEach { uri ->
                    PostImage(
                        uri = uri,
                        onOpenPost = onOpenPost,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                }
            }
            3 -> {
                PostImage(
                    uri = shown[0],
                    onOpenPost = onOpenPost,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PostImage(
                        uri = shown[1],
                        onOpenPost = onOpenPost,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                    PostImage(
                        uri = shown[2],
                        onOpenPost = onOpenPost,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PostImage(
                        uri = shown[0],
                        onOpenPost = onOpenPost,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                    PostImage(
                        uri = shown[1],
                        onOpenPost = onOpenPost,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PostImage(
                        uri = shown[2],
                        onOpenPost = onOpenPost,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    ) {
                        PostImage(
                            uri = shown[3],
                            onOpenPost = onOpenPost,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (extra > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { onOpenPost() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+$extra",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostImage(
    uri: String,
    onOpenPost: () -> Unit,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = Uri.parse(uri),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenPost() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReactionLikeButton(
    likedByMe: Boolean,
    myReaction: String? = null,
    onLike: () -> Unit,
    onReaction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val displayEmoji = myReaction ?: if (likedByMe) "❤️" else null
    Box(modifier = modifier) {
        if (displayEmoji != null) {
            PostAction(
                emoji = displayEmoji,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = stringResource(R.string.post_like),
                onClick = onLike,
                onLongClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            PostAction(
                icon = Icons.Filled.FavoriteBorder,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = stringResource(R.string.post_like),
                onClick = onLike,
                onLongClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (showPicker) {
            ReactionPicker(
                expanded = showPicker,
                selected = myReaction,
                onDismiss = { showPicker = false },
                onSelect = { emoji ->
                    showPicker = false
                    onReaction(emoji)
                }
            )
        }
    }
}

@Composable
private fun PinnedBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(NexaRadius.pill))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = NexaSpacing.sm, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.PushPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = stringResource(R.string.post_pinned),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AudienceBadge(audience: Audience) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.xs)
    ) {
        Text(
            text = audienceLabel(audience),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostAction(
    icon: ImageVector? = null,
    emoji: String? = null,
    contentDescription: String,
    onClick: () -> Unit,
    label: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interaction = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable { onClick() }
    }
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(NexaRadius.chip))
            .then(interaction),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (emoji != null) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.titleMedium
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
        if (label != null) {
            Spacer(Modifier.width(NexaSpacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
