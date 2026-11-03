package com.nexa.social.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.domain.model.CommunityPost
import com.nexa.social.ui.theme.NexaSpacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommunityPostCard(
    post: CommunityPost,
    onLike: () -> Unit,
    onReaction: (String) -> Unit = { onLike() },
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPicker by remember(post.id) { mutableStateOf(false) }
    NexaCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onOpenProfile(post.author.id) }
                .padding(vertical = NexaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NexaAvatar(
                displayName = post.author.displayName,
                avatarUri = post.author.avatarUri,
                size = 38.dp
            )
            Spacer(Modifier.width(NexaSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.author.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = TimeUtils.relativeFromNow(post.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (post.text.isNotBlank()) {
            Spacer(Modifier.height(NexaSpacing.sm))
            Text(text = post.text, style = MaterialTheme.typography.bodyLarge)
        }

        if (!post.imageUri.isNullOrBlank()) {
            Spacer(Modifier.height(NexaSpacing.sm))
            AsyncImage(
                model = Uri.parse(post.imageUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }

        Spacer(Modifier.height(NexaSpacing.sm))
        Box {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NexaSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val active = post.likedByMe || post.myReaction != null
                val displayEmoji = post.myReaction ?: if (post.likedByMe) "❤️" else null
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .combinedClickable(
                            onClick = onLike,
                            onLongClick = { showPicker = true }
                        )
                        .padding(horizontal = 6.dp, vertical = NexaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                ) {
                    if (displayEmoji != null) {
                        Text(
                            text = displayEmoji,
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = post.likeCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(NexaSpacing.xs))
                    Text(
                        text = post.commentCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (showPicker) {
                ReactionPicker(
                    expanded = showPicker,
                    selected = post.myReaction,
                    onDismiss = { showPicker = false },
                    onSelect = { emoji ->
                        showPicker = false
                        onReaction(emoji)
                    }
                )
            }
        }
    }
}
