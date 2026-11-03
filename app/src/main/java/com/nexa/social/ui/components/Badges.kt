package com.nexa.social.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexa.social.ui.theme.NexaSpacing

/** Small pill label, optionally tinted. Used for statuses such as SAMPLE, LOCAL or points. */
@Composable
fun NexaBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.xxs)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}
