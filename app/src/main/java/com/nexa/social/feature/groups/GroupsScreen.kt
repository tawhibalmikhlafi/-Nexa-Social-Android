package com.nexa.social.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.domain.model.Group
import com.nexa.social.domain.model.GroupInvite
import com.nexa.social.domain.model.GroupPrivacy
import com.nexa.social.domain.model.GroupSearchResult
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaRadius
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    viewModel: GroupsViewModel = viewModel(factory = GroupsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            NexaTopBar(
                title = stringResource(R.string.groups_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateGroup) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.groups_new))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQuery,
                placeholder = { Text(stringResource(R.string.groups_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(NexaRadius.pill),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.md),
            )
            if (state.isLoading && state.searchQuery.isBlank()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexaSpacing.lg)
                        .clip(RoundedCornerShape(NexaRadius.pill))
                )
            }
            if (state.isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexaSpacing.lg)
                        .clip(RoundedCornerShape(NexaRadius.pill))
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = NexaSpacing.sm),
            ) {
                if (state.searchQuery.isBlank()) {
                    if (state.error != null) {
                        item {
                            Text(
                                text = stringResource(R.string.groups_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(
                                    horizontal = NexaSpacing.md,
                                    vertical = NexaSpacing.sm,
                                ),
                            )
                        }
                    }
                    if (state.invites.isNotEmpty()) {
                        item {
                            SectionTitle(
                                stringResource(
                                    R.string.groups_invites_title,
                                    state.invites.size,
                                )
                            )
                        }
                        items(state.invites, key = { "invite_${it.id}" }) { invite ->
                            InviteRow(
                                invite = invite,
                                onAccept = { viewModel.respondToInvite(invite.id, true) },
                                onDecline = { viewModel.respondToInvite(invite.id, false) },
                            )
                        }
                        item {
                            HorizontalDivider(Modifier.padding(vertical = NexaSpacing.sm))
                        }
                    }
                    item {
                        SectionTitle(
                            stringResource(R.string.groups_mine_title, state.myGroups.size)
                        )
                    }
                    if (state.myGroups.isEmpty()) {
                        item {
                            EmptyState(stringResource(R.string.groups_empty))
                        }
                    } else {
                        items(state.myGroups, key = { it.id }) { group ->
                            GroupRow(group = group, onClick = { onOpenGroup(group.id) })
                        }
                    }
                } else {
                    item {
                        SectionTitle(
                            stringResource(
                                R.string.groups_search_results,
                                state.searchResults.size,
                            )
                        )
                    }
                    if (state.searchResults.isEmpty() && !state.isSearching) {
                        item {
                            EmptyState(stringResource(R.string.groups_search_empty))
                        }
                    } else {
                        items(state.searchResults, key = { it.id }) { result ->
                            SearchResultRow(
                                result = result,
                                onClick = { onOpenGroup(result.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.sm),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.xl)
            .clip(RoundedCornerShape(NexaRadius.card))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupRow(group: Group, onClick: () -> Unit) {
    NexaCard(
        modifier = Modifier.padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.xs),
        onClick = onClick,
        padding = 16,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexaAvatar(
                displayName = group.name,
                avatarUri = group.avatarUrl,
                size = 60.dp,
            )
            Spacer(Modifier.width(NexaSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.description.isNotBlank()) {
                    Spacer(Modifier.size(NexaSpacing.xxs))
                    Text(
                        text = group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(NexaSpacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrivacyPill(group.privacy)
                    Spacer(Modifier.width(NexaSpacing.sm))
                    Text(
                        text = stringResource(R.string.groups_members_count, group.memberCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(NexaRadius.pill))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun PrivacyPill(privacy: GroupPrivacy) {
    val isPublic = privacy == GroupPrivacy.PUBLIC
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(NexaRadius.pill))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = NexaSpacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isPublic) Icons.Filled.Public else Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = stringResource(
                if (isPublic) R.string.groups_public else R.string.groups_private
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SearchResultRow(result: GroupSearchResult, onClick: () -> Unit) {
    NexaCard(
        modifier = Modifier.padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.xs),
        onClick = onClick,
        padding = 16,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexaAvatar(
                displayName = result.name,
                avatarUri = result.avatarUrl,
                size = 56.dp,
            )
            Spacer(Modifier.width(NexaSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.description.isNotBlank()) {
                    Text(
                        text = result.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.groups_search_meta,
                        result.memberCount,
                        stringResource(
                            if (result.privacy == GroupPrivacy.PUBLIC) {
                                R.string.groups_public
                            } else {
                                R.string.groups_private
                            }
                        ),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InviteRow(
    invite: GroupInvite,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    NexaCard(
        modifier = Modifier.padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.xs),
        padding = 16,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexaAvatar(
                displayName = invite.groupName,
                avatarUri = invite.groupAvatarUrl,
                size = 44.dp,
            )
            Spacer(Modifier.width(NexaSpacing.md))
            Text(
                text = stringResource(
                    R.string.groups_invite_message,
                    invite.inviterName,
                    invite.groupName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.padding(top = NexaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm),
        ) {
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(NexaRadius.pill),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.groups_accept))
            }
            OutlinedButton(
                onClick = onDecline,
                shape = RoundedCornerShape(NexaRadius.pill),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.groups_decline))
            }
        }
    }
}
