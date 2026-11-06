package com.nexa.social.feature.worlds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.CommunityCategory
import com.nexa.social.ui.components.CommunityCard
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaWordmark
import com.nexa.social.ui.components.SectionHeader
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun WorldsScreen(
    onOpenCommunity: (String) -> Unit,
    onCreateWorld: () -> Unit = {},
    onOpenGatherings: () -> Unit,
    viewModel: WorldsViewModel = viewModel(factory = WorldsViewModel.Factory)
) {
    val search by viewModel.search.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val joinLabel = stringResource(R.string.action_join)
    val leaveLabel = stringResource(R.string.action_leave)
    val openLabel = stringResource(R.string.action_open)
    val popularTitle = stringResource(R.string.worlds_popular)
    val newWorldsTitle = stringResource(R.string.worlds_new)
    val suggestedTitle = stringResource(R.string.worlds_suggested)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = NexaSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.md),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    NexaWordmark(markSize = 34.dp)
                    Spacer(Modifier.height(NexaSpacing.sm))
                    Text(
                        text = stringResource(R.string.worlds_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.IconButton(onClick = onCreateWorld) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.world_create_title)
                    )
                }
            }
        }

        item {
            NexaTextField(
                value = search,
                onValueChange = viewModel::setSearch,
                label = stringResource(R.string.worlds_title),
                placeholder = stringResource(R.string.worlds_search_hint),
                leadingIcon = Icons.Filled.Public,
                modifier = Modifier.padding(horizontal = NexaSpacing.lg)
            )
        }

        item {
            LazyCategoryChips(
                selected = category,
                onSelect = viewModel::setCategory
            )
        }

        item {
            NexaCard(
                onClick = onOpenGatherings,
                modifier = Modifier.padding(horizontal = NexaSpacing.lg)
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(NexaSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.worlds_gatherings_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.worlds_gatherings_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.isEmpty) {
            item {
                NexaEmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = stringResource(R.string.state_no_results),
                    body = stringResource(R.string.worlds_search_hint)
                )
            }
        } else {
            worldSection(
                sectionKey = "popular",
                title = popularTitle,
                communities = state.popular,
                joinLabel = joinLabel,
                leaveLabel = leaveLabel,
                openLabel = openLabel,
                membersLabel = { count -> "$count ${stringResource(R.string.community_members)}" },
                onOpen = onOpenCommunity,
                onToggleJoin = viewModel::toggleMembership
            )
            worldSection(
                sectionKey = "new",
                title = newWorldsTitle,
                communities = state.newWorlds,
                joinLabel = joinLabel,
                leaveLabel = leaveLabel,
                openLabel = openLabel,
                membersLabel = { count -> "$count ${stringResource(R.string.community_members)}" },
                onOpen = onOpenCommunity,
                onToggleJoin = viewModel::toggleMembership
            )
            worldSection(
                sectionKey = "suggested",
                title = suggestedTitle,
                communities = state.suggested,
                joinLabel = joinLabel,
                leaveLabel = leaveLabel,
                openLabel = openLabel,
                membersLabel = { count -> "$count ${stringResource(R.string.community_members)}" },
                onOpen = onOpenCommunity,
                onToggleJoin = viewModel::toggleMembership
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.worldSection(
    sectionKey: String,
    title: String,
    communities: List<Community>,
    joinLabel: String,
    leaveLabel: String,
    openLabel: String,
    membersLabel: @Composable (Int) -> String,
    onOpen: (String) -> Unit,
    onToggleJoin: (String) -> Unit
) {
    if (communities.isEmpty()) return
    item {
        SectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = NexaSpacing.lg)
        )
    }
    items(communities, key = { "$sectionKey-${it.id}" }) { community ->
        CommunityCard(
            community = community,
            onOpen = { onOpen(community.id) },
            onToggleJoin = { onToggleJoin(community.id) },
            memberCountLabel = membersLabel(community.sampleMemberCount),
            joinLabel = joinLabel,
            leaveLabel = leaveLabel,
            openLabel = openLabel,
            modifier = Modifier.padding(horizontal = NexaSpacing.lg)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LazyCategoryChips(
    selected: CommunityCategory?,
    onSelect: (CommunityCategory?) -> Unit
) {
    val categories: List<CommunityCategory?> = listOf(null) + CommunityCategory.entries
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = NexaSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(categories) { _, category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = {
                    Text(
                        text = if (category == null) stringResource(R.string.category_all)
                        else categoryLabel(category)
                    )
                }
            )
        }
    }
}
