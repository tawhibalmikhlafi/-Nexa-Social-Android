package com.nexa.social.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexa.social.domain.model.Community
import com.nexa.social.ui.theme.NexaRadius
import com.nexa.social.ui.theme.NexaSpacing

/** Visual world card: gradient cover, name, short description, sample member count and join action. */
@Composable
fun CommunityCard(
    community: Community,
    onOpen: () -> Unit,
    onToggleJoin: () -> Unit,
    memberCountLabel: String,
    joinLabel: String,
    leaveLabel: String,
    openLabel: String,
    modifier: Modifier = Modifier
) {
    NexaCard(modifier = modifier, padding = 0) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = NexaRadius.card, topEnd = NexaRadius.card))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(community.coverColorSeed), MaterialTheme.colorScheme.secondary)
                        )
                    )
            ) {
                if (community.isNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(NexaSpacing.sm)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.xs)
                    ) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(NexaSpacing.lg)) {
                Text(
                    text = community.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(NexaSpacing.xs))
                Text(
                    text = community.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(NexaSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = memberCountLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                        NexaSecondaryButton(
                            text = if (community.isJoinedLocally) leaveLabel else joinLabel,
                            onClick = onToggleJoin,
                            modifier = Modifier.width(110.dp)
                        )
                        NexaPrimaryButton(
                            text = openLabel,
                            onClick = onOpen,
                            modifier = Modifier.width(110.dp)
                        )
                    }
                }
            }
        }
    }
}
