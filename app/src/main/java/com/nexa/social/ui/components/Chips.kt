package com.nexa.social.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexa.social.ui.theme.NexaSpacing

/** A labelled filter chip used for feed types, search filters and categories. */
@Composable
fun NexaChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier
    )
}

/** Horizontally scrollable row of choice chips backed by a small state holder. */
@Composable
fun <T> NexaChoiceChipRow(
    options: List<T>,
    selected: T?,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = NexaSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
    ) {
        items(options) { option ->
            NexaChoiceChip(
                label = labelOf(option),
                selected = selected == option,
                onClick = { onSelect(option) }
            )
        }
    }
}
