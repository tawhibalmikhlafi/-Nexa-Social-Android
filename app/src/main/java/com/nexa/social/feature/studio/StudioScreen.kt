package com.nexa.social.feature.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.SectionHeader
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.launch

@Composable
fun StudioScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: StudioViewModel = viewModel(factory = StudioViewModel.Factory)
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val shareMessage = stringResource(R.string.studio_id_copied)
    val user = stats.user

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.studio_title), onBack = onBack)
        if (user == null) {
            NexaEmptyState(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.studio_empty_title),
                body = stringResource(R.string.studio_empty_body)
            )
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.studio_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md)) {
                MetricCard(
                    value = stats.posts.toString(),
                    label = stringResource(R.string.studio_posts),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    value = stats.likes.toString(),
                    label = stringResource(R.string.studio_likes),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md)) {
                MetricCard(
                    value = stats.comments.toString(),
                    label = stringResource(R.string.studio_comments),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    value = stats.shares.toString(),
                    label = stringResource(R.string.studio_shares),
                    modifier = Modifier.weight(1f)
                )
            }

            NexaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.studio_nexa_score),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stats.nexaScore.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.studio_level, stats.level),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.studio_avg, stats.averageEngagement),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(NexaSpacing.sm))
                Text(
                    text = if (stats.streakDays > 0) {
                        stringResource(R.string.studio_streak, stats.streakDays)
                    } else {
                        stringResource(R.string.studio_streak_none)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (stats.topTags.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.studio_top_tags))
                Text(
                    text = stats.topTags.joinToString("   ") { "#$it" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            SectionHeader(title = stringResource(R.string.studio_best_post))
            val best = stats.bestPost
            if (best == null) {
                Text(
                    text = stringResource(R.string.studio_no_posts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                NexaCard(onClick = { onOpenPost(best.id) }) {
                    Text(
                        text = best.text.ifBlank { stringResource(R.string.studio_media_post) },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(NexaSpacing.sm))
                    Text(
                        text = stringResource(R.string.studio_best_meta, best.likeCount, best.commentCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = TimeUtils.date(best.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LocalDataBanner(text = stringResource(R.string.studio_local_note))

            NexaPrimaryButton(
                text = stringResource(R.string.studio_share_id),
                icon = Icons.Filled.Share,
                onClick = {
                    clipboard.setText(
                        AnnotatedString("@${user.username} · ${user.displayName} · Nexa 2030")
                    )
                    scope.launch { snackbarHost?.showSnackbar(shareMessage) }
                }
            )
            Spacer(Modifier.height(NexaSpacing.xxxl))
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    NexaCard(modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
