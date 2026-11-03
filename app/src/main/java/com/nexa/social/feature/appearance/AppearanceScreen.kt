package com.nexa.social.feature.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.domain.model.BackgroundStyle
import com.nexa.social.domain.model.NexaAccent
import com.nexa.social.domain.model.ThemeMode
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.SectionHeader
import com.nexa.social.ui.theme.NexaTheme
import com.nexa.social.ui.theme.accentChoices
import com.nexa.social.ui.theme.backgroundTokens
import com.nexa.social.ui.theme.NexaSpacing

private val customPresets: List<Long> = listOf(
    0xFFFF4D8DL, 0xFFFF8A3DL, 0xFFFFC93DL, 0xFF57D9A3L,
    0xFF3DB6FFL, 0xFF8C6BFFL, 0xFFB388FFL, 0xFFFF6B6BL
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: AppearanceViewModel = viewModel(factory = AppearanceViewModel.Factory)
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val snackbarHost = LocalSnackbarHostState.current
    val savedMessage = stringResource(R.string.appearance_saved)

    LaunchedEffect(saved) {
        if (saved) {
            snackbarHost?.showSnackbar(savedMessage)
            viewModel.consumeSaved()
        }
    }

    val config = draft ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.appearance_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
        ) {
            SectionHeader(title = stringResource(R.string.appearance_color_theme))
            Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md)) {
                accentChoices.forEach { (accent, color) ->
                    ColorDot(
                        color = color,
                        selected = config.accent == accent,
                        onClick = { viewModel.setAccent(accent) }
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.appearance_custom_color))
            Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md)) {
                customPresets.forEach { argb ->
                    ColorDot(
                        color = Color(argb),
                        selected = config.accent == NexaAccent.CUSTOM && config.customAccentArgb == argb,
                        onClick = { viewModel.setCustomAccent(argb) }
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.appearance_background))
            Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md)) {
                BackgroundStyle.entries.forEach { style ->
                    val dark = config.mode != ThemeMode.LIGHT
                    val swatch = backgroundTokens(style, dark).base
                    ColorDot(
                        color = swatch,
                        selected = config.background == style,
                        onClick = { viewModel.setBackground(style) }
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.appearance_mode))
            Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                ModeChip(stringResource(R.string.appearance_mode_dark), config.mode == ThemeMode.DARK) {
                    viewModel.setMode(ThemeMode.DARK)
                }
                ModeChip(stringResource(R.string.appearance_mode_light), config.mode == ThemeMode.LIGHT) {
                    viewModel.setMode(ThemeMode.LIGHT)
                }
                ModeChip(stringResource(R.string.appearance_mode_system), config.mode == ThemeMode.SYSTEM) {
                    viewModel.setMode(ThemeMode.SYSTEM)
                }
            }

            SectionHeader(title = stringResource(R.string.appearance_preview))
            NexaTheme(config = config) {
                NexaCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(NexaSpacing.sm))
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(NexaSpacing.md))
                    NexaPrimaryButton(text = stringResource(R.string.action_save), onClick = {})
                }
            }

            Spacer(Modifier.height(NexaSpacing.md))
            NexaPrimaryButton(
                text = stringResource(R.string.appearance_save),
                onClick = { viewModel.save() }
            )
            Spacer(Modifier.height(NexaSpacing.xxxl))
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
