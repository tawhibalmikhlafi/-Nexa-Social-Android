package com.nexa.social.feature.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.domain.model.GroupMember
import com.nexa.social.domain.model.GroupMemberStatus
import com.nexa.social.domain.model.GroupRole
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun GroupMembersScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: GroupMembersViewModel = viewModel(factory = GroupMembersViewModel.factory(groupId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query = state.searchQuery.trim().lowercase()
    val filtered = state.members.filter { member ->
        query.isEmpty() ||
            member.displayName.lowercase().contains(query) ||
            member.username.lowercase().contains(query)
    }
    val active = filtered.filter { it.status == GroupMemberStatus.ACTIVE }
    val banned = filtered.filter { it.status == GroupMemberStatus.BANNED }

    Scaffold(
        topBar = {
            NexaTopBar(
                title = stringResource(R.string.groups_members_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.groups_member_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm),
            )
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = NexaSpacing.xl),
                ) {
                    if (state.error != null) {
                        item {
                            Text(
                                text = when (state.error) {
                                    "role_failed", "remove_failed" ->
                                        stringResource(R.string.groups_action_failed)
                                    else -> stringResource(R.string.groups_members_failed)
                                },
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = NexaSpacing.md),
                            )
                        }
                    }
                    item {
                        MemberSectionTitle(
                            stringResource(R.string.groups_active_members, active.size)
                        )
                    }
                    if (active.isEmpty()) {
                        item {
                            EmptyMembers(stringResource(R.string.groups_no_members))
                        }
                    } else {
                        items(active, key = { it.userId }) { member ->
                            MemberRow(
                                member = member,
                                myRole = state.myRole,
                                onOpenProfile = { onOpenProfile(member.userId) },
                                onRole = { role -> viewModel.setRole(member.userId, role) },
                                onRemove = { viewModel.remove(member.userId, false) },
                                onBan = { viewModel.remove(member.userId, true) },
                                onUnban = { viewModel.unban(member.userId) },
                            )
                        }
                    }
                    if (banned.isNotEmpty()) {
                        item {
                            MemberSectionTitle(
                                stringResource(R.string.groups_banned_members, banned.size)
                            )
                        }
                        items(banned, key = { "banned_${it.userId}" }) { member ->
                            MemberRow(
                                member = member,
                                myRole = state.myRole,
                                onOpenProfile = { onOpenProfile(member.userId) },
                                onRole = { role -> viewModel.setRole(member.userId, role) },
                                onRemove = { viewModel.remove(member.userId, false) },
                                onBan = { viewModel.remove(member.userId, true) },
                                onUnban = { viewModel.unban(member.userId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm),
    )
}

@Composable
private fun EmptyMembers(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
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
private fun MemberRow(
    member: GroupMember,
    myRole: GroupRole?,
    onOpenProfile: () -> Unit,
    onRole: (String) -> Unit,
    onRemove: () -> Unit,
    onBan: () -> Unit,
    onUnban: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val canManage = myRole == GroupRole.OWNER || myRole == GroupRole.ADMIN
    val canActOnTarget = member.role != GroupRole.OWNER &&
        (myRole == GroupRole.OWNER || member.role == GroupRole.MEMBER)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenProfile),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NexaAvatar(
                displayName = member.displayName.ifBlank { member.username },
                avatarUri = member.avatarUrl,
                size = 44.dp,
            )
            Spacer(Modifier.width(NexaSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = member.displayName.ifBlank { member.username },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val roleText = roleLabel(member.role)
                Text(
                    text = "@${member.username} • $roleText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.status == GroupMemberStatus.BANNED) {
                    Text(
                        text = stringResource(R.string.groups_member_banned),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (canManage && canActOnTarget) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.groups_member_actions),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (member.status == GroupMemberStatus.BANNED) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.groups_unban_member)) },
                            onClick = {
                                menuOpen = false
                                onUnban()
                            },
                        )
                    } else {
                        if (member.role == GroupRole.MEMBER) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.groups_make_admin)) },
                                onClick = {
                                    menuOpen = false
                                    onRole(GroupRole.ADMIN.wire)
                                },
                            )
                        }
                        if (member.role == GroupRole.ADMIN && myRole == GroupRole.OWNER) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.groups_make_member)) },
                                onClick = {
                                    menuOpen = false
                                    onRole(GroupRole.MEMBER.wire)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.groups_remove_member)) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.groups_ban_member)) },
                            onClick = {
                                menuOpen = false
                                onBan()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun roleLabel(role: GroupRole): String = stringResource(
    when (role) {
        GroupRole.OWNER -> R.string.groups_owner
        GroupRole.ADMIN -> R.string.groups_admin
        GroupRole.MEMBER -> R.string.groups_member
    }
)
