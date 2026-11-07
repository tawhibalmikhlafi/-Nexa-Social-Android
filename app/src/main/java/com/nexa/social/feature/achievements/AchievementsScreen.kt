package com.nexa.social.feature.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.domain.model.AchievementDefinition
import com.nexa.social.domain.model.AchievementId
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = viewModel(factory = AchievementsViewModel.Factory)
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.achievements_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            NexaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.achievements_level, snapshot.level),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.achievements_points, snapshot.points),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(NexaSpacing.sm))
                Text(
                    text = stringResource(R.string.achievements_count, snapshot.unlocked.size, viewModel.definitions.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            viewModel.definitions.forEach { definition ->
                AchievementRow(
                    definition = definition,
                    progress = snapshot.progressOf(definition),
                    unlocked = snapshot.isUnlocked(definition)
                )
            }
            Spacer(Modifier.height(NexaSpacing.xxxl))
        }
    }
}

@Composable
private fun AchievementRow(
    definition: AchievementDefinition,
    progress: Int,
    unlocked: Boolean
) {
    NexaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = achievementLabel(definition.id),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (unlocked) {
                Text(
                    text = stringResource(R.string.achievements_unlocked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(NexaSpacing.xs))
        LinearProgressIndicator(
            progress = { (progress.toFloat() / definition.target).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(NexaSpacing.xs))
        Text(
            text = stringResource(R.string.achievements_progress, progress, definition.target),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun achievementLabel(id: AchievementId): String = when (id) {
    AchievementId.FIRST_POST -> stringResource(R.string.achievement_first_post)
    AchievementId.ACTIVE_CREATOR -> stringResource(R.string.achievement_active_creator)
    AchievementId.HELPFUL_VOICE -> stringResource(R.string.achievement_helpful_voice)
    AchievementId.WORLD_EXPLORER -> stringResource(R.string.achievement_world_explorer)
    AchievementId.WORLD_BUILDER -> stringResource(R.string.achievement_world_builder)
    AchievementId.SOCIAL_CONNECTOR -> stringResource(R.string.achievement_social_connector)
    AchievementId.CLIP_MAKER -> stringResource(R.string.achievement_clip_maker)
    AchievementId.STORYTELLER -> stringResource(R.string.achievement_storyteller)
    AchievementId.RISING_STAR -> stringResource(R.string.achievement_rising_star)
}
