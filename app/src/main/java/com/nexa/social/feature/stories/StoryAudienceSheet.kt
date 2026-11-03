package com.nexa.social.feature.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.domain.model.StoryAudienceMember
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaBottomSheet
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaErrorState
import com.nexa.social.ui.components.NexaLoadingState
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.theme.NexaSpacing

/**
 * Owner-only sheet listing everyone who viewed the story and everyone who replied to it.
 * All rows come from `get_story_audience`; there are no local or sample entries.
 */
@Composable
fun StoryAudienceSheet(
    state: StoryAudienceUiState,
    isLocal: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    NexaBottomSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.isLoading && state.members.isEmpty()) {
                        stringResource(R.string.story_viewers_title)
                    } else {
                        stringResource(
                            R.string.story_viewers_title_count,
                            state.members.size
                        )
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                if (!state.isLoading && state.error == null &&
                    state.members.isNotEmpty()
                ) {
                    Text(
                        text = stringResource(
                            R.string.story_audience_summary,
                            state.viewerCount,
                            state.interactionCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.story_audience_refresh)
                )
            }
        }

        Spacer(Modifier.height(NexaSpacing.sm))

        when {
            state.isLoading && state.members.isEmpty() -> {
                NexaLoadingState(label = stringResource(R.string.state_loading))
            }

            state.error != null && state.members.isEmpty() -> {
                NexaErrorState(
                    message = failureMessage(state.error)
                        ?: stringResource(R.string.error_generic),
                    onRetry = onRefresh
                )
            }

            state.members.isEmpty() -> {
                NexaEmptyState(
                    icon = Icons.Filled.Visibility,
                    title = stringResource(
                        if (isLocal) {
                            R.string.story_audience_local_title
                        } else {
                            R.string.story_audience_empty_title
                        }
                    ),
                    body = stringResource(
                        if (isLocal) {
                            R.string.story_audience_local_body
                        } else {
                            R.string.story_audience_empty_body
                        }
                    )
                )
            }

            else -> {
                if (state.error != null) {
                    Text(
                        text = failureMessage(state.error)
                            ?: stringResource(R.string.error_generic),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(NexaSpacing.sm))
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                ) {
                    items(state.members, key = { it.user.id }) { member ->
                        StoryAudienceRow(member)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(NexaSpacing.md))
        NexaSecondaryButton(
            text = stringResource(R.string.action_close),
            onClick = onDismiss
        )
    }
}

@Composable
private fun StoryAudienceRow(member: StoryAudienceMember) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexaAvatar(
            displayName = member.user.displayName,
            avatarUri = member.user.avatarUri,
            size = 44.dp
        )

        Spacer(Modifier.width(NexaSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = member.user.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (member.hasInteracted) {
                    InteractionBadge(member.interactionCount)
                }
            }
            Text(
                text = "@${member.user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (member.hasInteracted) {
                if (!member.interactionText.isNullOrBlank()) {
                    Text(
                        text = "“${member.interactionText.take(180)}”",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = stringResource(R.string.story_audience_interacted),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.story_audience_viewed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (member.lastActivityAt > 0L) {
            Spacer(Modifier.width(NexaSpacing.sm))
            Text(
                text = TimeUtils.relativeFromNow(member.lastActivityAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InteractionBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = if (count > 1) {
                    stringResource(R.string.story_audience_replies_count, count)
                } else {
                    stringResource(R.string.story_audience_interacted)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
