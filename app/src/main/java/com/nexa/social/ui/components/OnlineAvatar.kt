package com.nexa.social.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nexa.social.feature.presence.PresenceViewModel

/**
 * Circular avatar (image or initials) with a green dot in the bottom-end corner when online.
 * @param presenceViewModel required when showPresence = true.
 */
@Composable
fun OnlineAvatar(
    userId: String,
    imageUrl: String?,
    size: Dp = 44.dp,
    showPresence: Boolean = true,
    fallbackInitials: String = "؟",
    presenceViewModel: PresenceViewModel? = null,
) {
    val isOnline: Boolean = if (showPresence && presenceViewModel != null) {
        val state by presenceViewModel.onlineFor(userId)
            .collectAsStateWithLifecycle(initialValue = false)
        state
    } else {
        false
    }

    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(nexaGradient(userId.hashCode().toLong())),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = fallbackInitials,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
                    .border(
                        width = (size.value * 0.035f).dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                    ),
            )
        }
    }
}
