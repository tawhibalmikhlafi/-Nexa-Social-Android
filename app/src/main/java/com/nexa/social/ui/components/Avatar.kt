package com.nexa.social.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexa.social.ui.theme.NexaRadius

/** Deterministic gradient derived from a stable seed (id or username). */
fun nexaGradient(seed: Long): Brush {
    val hue = ((seed % 360) + 360) % 360
    val start = Color.hsl(hue.toFloat(), 0.62f, 0.55f)
    val end = Color.hsl(((hue + 45) % 360).toFloat(), 0.7f, 0.42f)
    return Brush.linearGradient(listOf(start, end))
}

fun initialsOf(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/**
 * Avatar that shows the picked image when present, otherwise a stable gradient with initials.
 * Sample accounts do not ship real photos, so the gradient keeps the UI honest.
 */
@Composable
fun NexaAvatar(
    displayName: String,
    avatarUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val seed = displayName.hashCode().toLong()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(nexaGradient(seed)),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUri.isNullOrBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initialsOf(displayName),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** Cover image or a gradient fallback for communities and profiles. */
@Composable
fun NexaCover(
    seed: Long,
    coverUri: String?,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    corner: Dp = NexaRadius.card
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(nexaGradient(seed))
    ) {
        if (!coverUri.isNullOrBlank()) {
            AsyncImage(
                model = coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
