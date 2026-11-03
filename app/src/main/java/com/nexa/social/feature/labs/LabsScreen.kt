package com.nexa.social.feature.labs

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexa.social.R
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

/** Hub for the experimental, locally-powered "Nexa 2030" suite. */
@Composable
fun LabsScreen(
    onBack: () -> Unit,
    onPulse: () -> Unit,
    onGatherings: () -> Unit,
    onCapsules: () -> Unit,
    onStudio: () -> Unit,
    onWellbeing: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.labs_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            NexaCard(padding = 20) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.size(NexaSpacing.md))
                    Column {
                        Text(
                            text = stringResource(R.string.labs_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.labs_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LabsFeatureRow(
                icon = Icons.Filled.Bolt,
                title = stringResource(R.string.labs_pulse_title),
                summary = stringResource(R.string.labs_pulse_summary),
                onClick = onPulse
            )
            LabsFeatureRow(
                icon = Icons.Filled.Groups,
                title = stringResource(R.string.labs_gatherings_title),
                summary = stringResource(R.string.labs_gatherings_summary),
                onClick = onGatherings
            )
            LabsFeatureRow(
                icon = Icons.Filled.Schedule,
                title = stringResource(R.string.labs_capsules_title),
                summary = stringResource(R.string.labs_capsules_summary),
                onClick = onCapsules
            )
            LabsFeatureRow(
                icon = Icons.Filled.Insights,
                title = stringResource(R.string.labs_studio_title),
                summary = stringResource(R.string.labs_studio_summary),
                onClick = onStudio
            )
            LabsFeatureRow(
                icon = Icons.Filled.SelfImprovement,
                title = stringResource(R.string.labs_wellbeing_title),
                summary = stringResource(R.string.labs_wellbeing_summary),
                onClick = onWellbeing
            )

            Spacer(Modifier.height(NexaSpacing.sm))
            LocalDataBanner(text = stringResource(R.string.labs_local_note))
            Spacer(Modifier.height(NexaSpacing.xxxl))
        }
    }
}

@Composable
private fun LabsFeatureRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    NexaCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.size(NexaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
