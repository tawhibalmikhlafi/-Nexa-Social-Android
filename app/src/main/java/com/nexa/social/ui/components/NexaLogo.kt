package com.nexa.social.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexa.social.ui.theme.NexaRadius
import com.nexa.social.ui.theme.NexaSpacing
import com.nexa.social.ui.theme.NexaTheme

/** The Nexa "N" mark with the brand gradient. */
@Composable
fun NexaLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val density = LocalDensity.current
    val letterSize = with(density) { (size * 0.52f).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(NexaRadius.logo))
            .background(Brush.linearGradient(listOf(accent, secondary))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "N",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = letterSize
        )
    }
}

/** Logo mark plus the "Nexa" wordmark and the optional "2030" year. */
@Composable
fun NexaWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 44.dp,
    showYear: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md)
    ) {
        NexaLogoMark(size = markSize)
        Column {
            Text(
                text = "Nexa",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (showYear) {
                Text(
                    text = "2030",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LogoPreview() {
    NexaTheme {
        Box(Modifier.padding(24.dp)) { NexaWordmark() }
    }
}
