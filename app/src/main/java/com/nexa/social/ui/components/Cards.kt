package com.nexa.social.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nexa.social.ui.theme.NexaSpacing

/** Themed surface used for cards across the app. */
@Composable
fun NexaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit
) {
    val darkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    val cardBackground = if (darkSurface) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                MaterialTheme.colorScheme.surface,
                Color(0xFF24103D).copy(alpha = 0.42f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBackground, shape)
            .border(
                width = 1.dp,
                color = if (darkSurface) MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = shape
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(padding.dp),
        content = content
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = NexaSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onAction() }
                    .padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.xs)
            )
        }
    }
}
