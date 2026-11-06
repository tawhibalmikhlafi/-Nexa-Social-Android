package com.nexa.social.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.domain.model.Group
import com.nexa.social.domain.model.GroupMember
import com.nexa.social.domain.model.GroupMemberStatus
import com.nexa.social.domain.model.GroupPost
import com.nexa.social.domain.model.GroupRole
import com.nexa.social.domain.model.GroupStats
import com.nexa.social.domain.model.ReactionKind
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaBadge
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaConfirmDialog
import com.nexa.social.ui.components.NexaCover
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.SectionHeader
import com.nexa.social.ui.theme.NexaRadius
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenMembers: (String) -> Unit = {},
    onOpenSettings: (String) -> Unit = {},
    onOpenRequests: (String) -> Unit = {},
    onOpenComments: (String, String) -> Unit = { _, _ -> },
    onOpenProfile: (String) -> Unit = {},
    viewModel: GroupDetailViewModel = viewModel(factory = GroupDetailViewModel.factory(groupId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var postText by remember { mutableStateOf("") }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinMessage by remember { mutableStateOf("") }
    var postToDelete by remember { mutableStateOf<GroupPost?>(null) }
    var postToReport by remember { mutableStateOf<GroupPost?>(null) }
    var reportReason by remember { mutableStateOf("spam") }
    var reportNote by remember { mutableStateOf("") }
    var showGroupReports by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val errorMessage = when (state.error) {
        "load_failed" -> stringResource(R.string.groups_load_failed)
        "delete_failed" -> stringResource(R.string.groups_delete_failed)
        "post_failed" -> stringResource(R.string.groups_post_failed)
        "join_failed" -> stringResource(R.string.groups_join_failed)
        "leave_failed" -> stringResource(R.string.groups_leave_failed)
        "reaction_failed" -> stringResource(R.string.groups_reaction_failed)
        "posts_failed" -> stringResource(R.string.groups_posts_failed)
        "report_failed" -> stringResource(R.string.groups_report_failed)
        "reports_failed", "report_review_failed" -> stringResource(R.string.groups_reports_failed)
        else -> stringResource(R.string.groups_error)
    }

    val reportSentMessage = stringResource(R.string.groups_report_sent)
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { message ->
            snackbar.showSnackbar(if (message == "report_sent") reportSentMessage else message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            NexaTopBar(
                title = state.group?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    val group = state.group
                    if (group?.canManage == true) {
                        IconButton(onClick = { showGroupReports = true; viewModel.loadGroupPostReports() }) {
                            Icon(Icons.Filled.Flag, contentDescription = stringResource(R.string.groups_reports_title))
                        }
                        IconButton(onClick = { onOpenRequests(groupId) }) {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = stringResource(R.string.groups_requests_title),
                            )
                        }
                        IconButton(onClick = { onOpenSettings(groupId) }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.groups_settings_title),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading && state.group == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.group == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            }

            else -> {
                val group = state.group!!
                val activeMembers = state.members.filter { it.status == GroupMemberStatus.ACTIVE }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        top = NexaSpacing.sm,
                        bottom = NexaSpacing.huge,
                    ),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.md),
                ) {
                    item {
                        GroupHero(
                            group = group,
                            activeMembers = activeMembers,
                            onJoin = {
                                if (group.requireJoinApproval) showJoinDialog = true
                                else viewModel.joinOrRequest(null)
                            },
                            onLeave = { viewModel.leave() },
                        )
                    }

                    if (group.isArchived) {
                        item {
                            InlineNotice(
                                text = stringResource(R.string.groups_archived),
                                modifier = Modifier.padding(horizontal = NexaSpacing.md),
                            )
                        }
                    }

                    if (state.error != null) {
                        item {
                            InlineNotice(
                                text = errorMessage,
                                error = true,
                                modifier = Modifier.padding(horizontal = NexaSpacing.md),
                            )
                        }
                    }

                    if (group.description.isNotBlank() ||
                        group.rules.isNotBlank() ||
                        !group.category.isNullOrBlank()
                    ) {
                        item {
                            AboutCard(
                                group = group,
                                modifier = Modifier.padding(horizontal = NexaSpacing.md),
                            )
                        }
                    }

                    if (group.canManage && state.stats != null) {
                        item {
                            StatsCard(
                                stats = state.stats!!,
                                modifier = Modifier.padding(horizontal = NexaSpacing.md),
                            )
                        }
                    }

                    if (activeMembers.isNotEmpty()) {
                        item {
                            MembersStrip(
                                members = activeMembers,
                                onSeeAll = { onOpenMembers(groupId) },
                                onOpenProfile = onOpenProfile,
                            )
                        }
                    }

                    if (group.canPost) {
                        item {
                            GroupComposer(
                                text = postText,
                                onTextChange = { postText = it },
                                isSaving = state.isSaving,
                                onPublish = {
                                    viewModel.createPost(postText, null)
                                    postText = ""
                                },
                                modifier = Modifier.padding(horizontal = NexaSpacing.lg),
                            )
                        }
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.groups_posts_title),
                            modifier = Modifier.padding(horizontal = NexaSpacing.lg),
                        )
                    }

                    if (state.posts.isEmpty()) {
                        item { EmptyPosts() }
                    } else {
                        items(state.posts, key = { it.id }) { post ->
                            GroupPostCard(
                                post = post,
                                canManage = group.canManage,
                                canReport = group.isMember && !post.isAuthor,
                                onReact = { kind -> viewModel.react(post.id, kind) },
                                onComments = { onOpenComments(groupId, post.id) },
                                onDelete = { postToDelete = post },
                                onReport = { postToReport = post },
                                onOpenProfile = onOpenProfile,
                                modifier = Modifier.padding(horizontal = NexaSpacing.lg),
                            )
                        }
                    }

                    if (state.hasMore && state.posts.isNotEmpty()) {
                        item {
                            NexaSecondaryButton(
                                text = stringResource(R.string.groups_load_more),
                                onClick = viewModel::loadMore,
                                enabled = !state.isLoadingMore,
                                modifier = Modifier.padding(horizontal = NexaSpacing.md),
                            )
                        }
                    }
                }
            }
        }
    }

    if (postToReport != null) {
        AlertDialog(
            onDismissRequest = { postToReport = null },
            title = { Text(stringResource(R.string.groups_report_post_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it.take(60) },
                        label = { Text(stringResource(R.string.groups_report_reason_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = reportNote,
                        onValueChange = { reportNote = it.take(500) },
                        placeholder = { Text(stringResource(R.string.groups_report_note_hint)) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reportReason.isNotBlank(),
                    onClick = {
                        postToReport?.let { viewModel.reportPost(it.id, it.content, reportReason.trim(), reportNote.trim()) }
                        postToReport = null
                        reportReason = "spam"
                        reportNote = ""
                    },
                ) { Text(stringResource(R.string.groups_report_send)) }
            },
            dismissButton = {
                TextButton(onClick = { postToReport = null }) {
                    Text(stringResource(R.string.groups_report_cancel))
                }
            },
        )
    }

    if (showGroupReports) {
        AlertDialog(
            onDismissRequest = { showGroupReports = false },
            title = { Text(stringResource(R.string.groups_reports_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm),
                ) {
                    if (state.isLoadingReports) {
                        CircularProgressIndicator()
                    } else if (state.groupPostReports.isEmpty()) {
                        Text(stringResource(R.string.groups_reports_empty))
                    } else {
                        state.groupPostReports.forEach { report ->
                            NexaCard(padding = 16.dp) {
                                if (report.postPreview.isNotBlank()) {
                                    Text(report.postPreview, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    text = report.reason,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (report.note.isNotBlank()) {
                                    Text(report.note, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = stringResource(R.string.groups_reporter_member),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { viewModel.reviewGroupPostReport(report.id) }) {
                                    Text(stringResource(R.string.groups_report_mark_reviewed))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroupReports = false }) {
                    Text(stringResource(R.string.groups_reports_close))
                }
            },
        )
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text(stringResource(R.string.groups_join_request_title)) },
            text = {
                OutlinedTextField(
                    value = joinMessage,
                    onValueChange = { joinMessage = it },
                    placeholder = { Text(stringResource(R.string.groups_request_message_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    shape = RoundedCornerShape(NexaRadius.chip),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.joinOrRequest(joinMessage.trim().ifBlank { null })
                        joinMessage = ""
                        showJoinDialog = false
                    },
                ) {
                    Text(stringResource(R.string.groups_join_request_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text(stringResource(R.string.groups_cancel))
                }
            },
        )
    }

    postToDelete?.let { post ->
        NexaConfirmDialog(
            title = stringResource(R.string.groups_delete_post),
            message = stringResource(R.string.post_delete_confirm),
            destructive = true,
            confirmLabel = stringResource(R.string.post_delete),
            onConfirm = {
                postToDelete = null
                viewModel.deletePost(post.id)
            },
            onDismiss = { postToDelete = null },
        )
    }
}

@Composable
private fun GroupHero(
    group: Group,
    activeMembers: List<GroupMember>,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
) {
    NexaCard(modifier = Modifier.padding(horizontal = NexaSpacing.md), padding = 0) {
        NexaCover(
            seed = group.id.hashCode().toLong(),
            coverUri = group.coverUrl,
            modifier = Modifier.fillMaxWidth(),
            height = 150.dp,
        )
        Column(modifier = Modifier.padding(NexaSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaAvatar(
                    displayName = group.name,
                    avatarUri = group.avatarUrl,
                    size = 56.dp,
                )
                Spacer(Modifier.width(NexaSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(NexaSpacing.xxs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm),
                    ) {
                        NexaBadge(
                            text = if (group.privacy ==
                                com.nexa.social.domain.model.GroupPrivacy.PRIVATE
                            ) {
                                stringResource(R.string.groups_private)
                            } else {
                                stringResource(R.string.groups_public)
                            }
                        )
                        Text(
                            text = stringResource(R.string.groups_members_count, group.memberCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (activeMembers.isNotEmpty()) {
                Spacer(Modifier.height(NexaSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)) {
                    activeMembers.take(5).forEach { member ->
                        NexaAvatar(
                            displayName = member.displayName.ifBlank { member.username },
                            avatarUri = member.avatarUrl,
                            size = 30.dp,
                        )
                    }
                    if (activeMembers.size > 5) {
                        Text(
                            text = "+${activeMembers.size - 5}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }

            Spacer(Modifier.height(NexaSpacing.md))
            when {
                group.isMember -> NexaSecondaryButton(
                    text = stringResource(R.string.groups_leave),
                    onClick = onLeave,
                )

                group.myStatus == GroupMemberStatus.PENDING -> Text(
                    text = stringResource(R.string.groups_pending),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                group.myStatus == GroupMemberStatus.BANNED -> Text(
                    text = stringResource(R.string.groups_banned),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )

                group.requireJoinApproval -> NexaPrimaryButton(
                    text = stringResource(R.string.groups_request_join),
                    onClick = onJoin,
                )

                else -> NexaPrimaryButton(
                    text = stringResource(R.string.groups_join),
                    onClick = onJoin,
                )
            }
        }
    }
}

@Composable
private fun AboutCard(group: Group, modifier: Modifier = Modifier) {
    NexaCard(modifier = modifier) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (group.description.isNotBlank()) {
            Spacer(Modifier.height(NexaSpacing.xs))
            Text(
                text = group.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!group.category.isNullOrBlank()) {
            Spacer(Modifier.height(NexaSpacing.sm))
            Text(
                text = "${stringResource(R.string.groups_category_label)}: ${group.category}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (group.rules.isNotBlank()) {
            Spacer(Modifier.height(NexaSpacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(NexaSpacing.md))
            Text(
                text = stringResource(R.string.groups_rules_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(NexaSpacing.xs))
            Text(
                text = group.rules,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsCard(stats: GroupStats, modifier: Modifier = Modifier) {
    NexaCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.groups_stats_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(NexaSpacing.md))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCell(
                value = stats.totalMembers.toString(),
                label = stringResource(R.string.groups_stats_members),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                value = stats.totalPosts.toString(),
                label = stringResource(R.string.groups_stats_posts),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                value = stats.totalComments.toString(),
                label = stringResource(R.string.groups_stats_comments),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                value = stats.pendingMembers.toString(),
                label = stringResource(R.string.groups_stats_pending),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MembersStrip(
    members: List<GroupMember>,
    onSeeAll: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.groups_members_title),
            modifier = Modifier.padding(horizontal = NexaSpacing.md),
            actionLabel = stringResource(R.string.groups_see_all),
            onAction = onSeeAll,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = NexaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md),
        ) {
            items(members, key = { it.userId }) { member ->
                Column(
                    modifier = Modifier
                        .width(68.dp)
                        .clip(RoundedCornerShape(NexaRadius.chip))
                        .clickable { onOpenProfile(member.userId) }
                        .padding(vertical = NexaSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NexaAvatar(
                        displayName = member.displayName.ifBlank { member.username },
                        avatarUri = member.avatarUrl,
                        size = 48.dp,
                    )
                    Spacer(Modifier.height(NexaSpacing.xs))
                    Text(
                        text = member.displayName.ifBlank { member.username },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val memberRole = roleLabel(member.role)
                    if (memberRole.isNotEmpty()) {
                        Text(
                            text = memberRole,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun roleLabel(role: GroupRole): String = when (role) {
    GroupRole.OWNER -> stringResource(R.string.groups_owner)
    GroupRole.ADMIN -> stringResource(R.string.groups_admin)
    GroupRole.MEMBER -> ""
}

@Composable
private fun GroupComposer(
    text: String,
    onTextChange: (String) -> Unit,
    isSaving: Boolean,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NexaCard(modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.groups_post_hint)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5,
            shape = RoundedCornerShape(NexaRadius.chip),
        )
        Spacer(Modifier.height(NexaSpacing.sm))
        NexaPrimaryButton(
            text = stringResource(R.string.groups_publish),
            onClick = onPublish,
            enabled = text.isNotBlank() && !isSaving,
            loading = isSaving,
        )
    }
}

@Composable
private fun GroupPostCard(
    post: GroupPost,
    canManage: Boolean,
    canReport: Boolean,
    onReact: (ReactionKind?) -> Unit,
    onComments: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meta = buildString {
        append(TimeUtils.relativeFromNow(post.createdAt))
        if (post.editedAt != null) {
            append(" · ")
            append(stringResource(R.string.groups_edited))
        }
    }
    NexaCard(modifier = modifier, padding = 0) {
        Column(modifier = Modifier.padding(NexaSpacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NexaRadius.chip))
                    .clickable { onOpenProfile(post.authorId) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexaAvatar(
                    displayName = post.authorName,
                    avatarUri = post.authorAvatarUrl,
                    size = 46.dp,
                )
                Spacer(Modifier.width(NexaSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (canReport) {
                    IconButton(onClick = onReport) {
                        Icon(
                            imageVector = Icons.Filled.Flag,
                            contentDescription = stringResource(R.string.groups_report_post),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (post.isAuthor || canManage) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.groups_delete_post),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (post.content.isNotBlank()) {
                Spacer(Modifier.height(NexaSpacing.md))
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (!post.mediaUrl.isNullOrBlank()) {
            AsyncImage(
                model = post.mediaUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexaSpacing.lg)
                    .height(230.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
        }

        Spacer(Modifier.height(NexaSpacing.md))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.xs),
        ) {
            ReactionAction(
                post = post,
                onReact = onReact,
                modifier = Modifier.weight(1f),
            )
            CommentAction(
                post = post,
                onComments = onComments,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReactionAction(
    post: GroupPost,
    onReact: (ReactionKind?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = post.myReaction != null
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(NexaRadius.pill))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable { onReact(if (active) null else ReactionKind.LIKE) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (post.myReaction != null) {
            Text(text = reactionEmoji(post.myReaction), style = MaterialTheme.typography.titleMedium)
        } else {
            Icon(
                imageVector = Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(R.string.groups_reaction_like),
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(NexaSpacing.xs))
        Text(
            text = if (post.reactionCount > 0) {
                post.reactionCount.toString()
            } else {
                stringResource(R.string.groups_reaction_like)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun CommentAction(
    post: GroupPost,
    onComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(NexaRadius.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onComments),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = stringResource(R.string.groups_comments_count, post.commentCount),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(NexaSpacing.xs))
        Text(
            text = if (post.commentCount > 0) {
                post.commentCount.toString()
            } else {
                stringResource(R.string.post_comment)
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun reactionEmoji(kind: ReactionKind): String = when (kind) {
    ReactionKind.LIKE -> "👍"
    ReactionKind.LOVE -> "❤️"
    ReactionKind.HAHA -> "😂"
    ReactionKind.SAD -> "😢"
    ReactionKind.ANGRY -> "😡"
}

@Composable
private fun EmptyPosts() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(NexaSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(NexaSpacing.md))
            Text(
                text = stringResource(R.string.groups_posts_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    val container = if (error) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val content = if (error) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NexaRadius.chip))
            .background(container)
            .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = content,
        )
    }
}
