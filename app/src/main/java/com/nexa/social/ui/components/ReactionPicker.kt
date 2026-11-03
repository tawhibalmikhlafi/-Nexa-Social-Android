package com.nexa.social.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Canonical reaction set, shared by every surface so the picker never drifts. */
val ReactionEmojis: List<String> = listOf("❤️", "👍", "😂", "😮", "😢", "😡")

/**
 * Anchored reaction picker. It uses a focusable [DropdownMenu] so the menu reliably opens,
 * dismisses on outside/back and never fires a click on the emoji hidden under the finger.
 */
@Composable
fun ReactionPicker(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    selected: String? = null,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        offset = DpOffset(0.dp, (-8).dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionEmojis.forEach { emoji ->
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (emoji == selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onSelect(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
