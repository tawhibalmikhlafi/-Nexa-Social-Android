package com.nexa.social.feature.notifications

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.domain.model.NexaNotification
import com.nexa.social.domain.model.NotificationType
import com.nexa.social.feature.profile.ProfileViewModel
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaBottomSheet
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaTextButton
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.SheetTitle
import com.nexa.social.ui.theme.NexaSpacing

private fun notificationIcon(type: NotificationType?): ImageVector = when (type) {
    NotificationType.LIKE, NotificationType.COMMENT_LIKE -> Icons.Filled.Favorite
    NotificationType.COMMENT -> Icons.Filled.ChatBubble
    NotificationType.REPLY, NotificationType.STORY_REPLY -> Icons.AutoMirrored.Filled.Reply
    NotificationType.FOLLOW, NotificationType.FOLLOW_REQUEST_ACCEPTED -> Icons.Filled.PersonAdd
    NotificationType.COMMUNITY,
    NotificationType.GROUP_JOIN_REQUEST,
    NotificationType.GROUP_JOIN_APPROVED,
    NotificationType.GROUP_JOIN_REJECTED -> Icons.Filled.Groups
    NotificationType.SPACE -> Icons.Filled.Explore
    NotificationType.WELCOME -> Icons.Filled.Star
    NotificationType.MENTION -> Icons.Filled.AlternateEmail
    NotificationType.MESSAGE -> Icons.AutoMirrored.Filled.Chat
    NotificationType.SYSTEM -> Icons.Filled.Info
    null -> Icons.Filled.Notifications
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onOpen: (NexaNotification) -> Unit = {},
    viewModel: NotificationsViewModel = viewModel(factory = NotificationsViewModel.Factory)
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(null), key = "notification-follow-requests")
    val followRequests by profileViewModel.incomingFollowRequests.collectAsStateWithLifecycle()
    val followRequestsLoading by profileViewModel.followRequestsLoading.collectAsStateWithLifecycle()
    val followRequestActionId by profileViewModel.followRequestActionId.collectAsStateWithLifecycle()
    val typeEnabled by viewModel.typeEnabled.collectAsStateWithLifecycle()
    val isRemote by viewModel.isRemote.collectAsStateWithLifecycle()
    var showTypes by remember { mutableStateOf(false) }
    var unreadOnly by remember { mutableStateOf(false) }
    var showFollowRequests by remember { mutableStateOf(false) }
    val visibleNotifications = if (unreadOnly) notifications.filter { !it.isRead } else notifications

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(
            title = stringResource(R.string.notifications_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = { showTypes = true }) {
                    Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.notifications_filter))
                }
                if (notifications.any { !it.isRead }) {
                    NexaTextButton(
                        text = stringResource(R.string.notifications_mark_all_read),
                        onClick = viewModel::markAllRead
                    )
                }
                if (notifications.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearAll) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.notifications_clear_all))
                    }
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
        ) {
            FilterChip(
                selected = !unreadOnly,
                onClick = { unreadOnly = false; showFollowRequests = false },
                label = { Text(stringResource(R.string.notifications_filter_all)) }
            )
            FilterChip(
                selected = unreadOnly,
                onClick = { unreadOnly = true; showFollowRequests = false },
                label = { Text(stringResource(R.string.notifications_filter_unread) + " (" + notifications.count { !it.isRead } + ")") }
            )
            FilterChip(
                selected = showFollowRequests,
                onClick = { showFollowRequests = true },
                label = { Text("طلبات المتابعة (" + followRequests.size + ")") }
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NexaSpacing.lg),
            contentPadding = PaddingValues(top = NexaSpacing.md, bottom = NexaSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
        ) {
            if (showFollowRequests) {
                item { Text("طلبات المتابعة", style = MaterialTheme.typography.titleMedium) }
                if (followRequestsLoading && followRequests.isEmpty()) {
                    item { Text("جارٍ تحميل الطلبات…", style = MaterialTheme.typography.bodySmall) }
                }
                if (followRequests.isEmpty() && !followRequestsLoading) {
                    item {
                        NexaEmptyState(
                            icon = Icons.Filled.PersonAdd,
                            title = "لا توجد طلبات متابعة",
                            body = "ستظهر هنا طلبات الأشخاص الذين يرغبون في متابعة حسابك الخاص."
                        )
                    }
                }
                items(followRequests, key = { "follow_request_" + it.id }) { request ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = NexaSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                    ) {
                        NexaAvatar(
                            displayName = request.requester.displayName,
                            avatarUri = request.requester.avatarUri,
                            size = 44.dp,
                            modifier = Modifier.clickable { onOpenProfile(request.requester.id) }
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenProfile(request.requester.id) }
                        ) {
                            Text(request.requester.displayName + " طلب متابعتك", style = MaterialTheme.typography.bodyMedium)
                            Text("@" + request.requester.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        NexaTextButton(
                            text = "إزالة",
                            onClick = { profileViewModel.respondToFollowRequest(request.id, false) }
                        )
                        NexaTextButton(
                            text = if (followRequestActionId == request.id) "…" else "تأكيد",
                            onClick = { profileViewModel.respondToFollowRequest(request.id, true) }
                        )
                    }
                }
            } else {
            item {
                if (!isRemote) {
                    LocalDataBanner(
                        text = stringResource(R.string.notifications_local_notice),
                        modifier = Modifier.padding(bottom = NexaSpacing.sm)
                    )
                }
            }
            if (visibleNotifications.isEmpty()) {
                item {
                    NexaEmptyState(
                        icon = Icons.Filled.Notifications,
                        title = stringResource(R.string.notifications_empty_title),
                        body = stringResource(R.string.notifications_empty_body)
                    )
                }
            } else {
                items(visibleNotifications, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        onClick = {
                            viewModel.markRead(notification.id)
                            onOpen(notification)
                        },
                        onDelete = { viewModel.delete(notification.id) }
                    )
                }
            }
            }
        }
    }

    if (showTypes) {
        NexaBottomSheet(onDismiss = { showTypes = false }) {
            SheetTitle(stringResource(R.string.notifications_filter))
            Text(
                text = stringResource(R.string.notifications_filter_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(NexaSpacing.sm))
            notificationTypeToggles().forEach { type ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = notificationTypeLabel(type), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = typeEnabled[type] ?: true,
                        onCheckedChange = { viewModel.setTypeEnabled(type, it) }
                    )
                }
            }
        }
    }
}

private fun notificationTypeToggles(): List<NotificationType> = listOf(
    NotificationType.LIKE,
    NotificationType.COMMENT,
    NotificationType.REPLY,
    NotificationType.FOLLOW,
    NotificationType.FOLLOW_REQUEST_ACCEPTED,
    NotificationType.MENTION,
    NotificationType.STORY_REPLY,
    NotificationType.COMMUNITY,
    NotificationType.SPACE
)

@Composable
private fun notificationTypeLabel(type: NotificationType): String = when (type) {
    NotificationType.LIKE -> stringResource(R.string.notif_type_like)
    NotificationType.COMMENT_LIKE -> stringResource(R.string.notif_type_comment_like)
    NotificationType.COMMENT -> stringResource(R.string.notif_type_comment)
    NotificationType.REPLY -> stringResource(R.string.notif_type_reply)
    NotificationType.FOLLOW, NotificationType.FOLLOW_REQUEST_ACCEPTED -> stringResource(R.string.notif_type_follow)
    NotificationType.COMMUNITY -> stringResource(R.string.notif_type_community)
    NotificationType.GROUP_JOIN_REQUEST -> stringResource(R.string.notif_type_group_join_request)
    NotificationType.GROUP_JOIN_APPROVED -> stringResource(R.string.notif_type_group_join_approved)
    NotificationType.GROUP_JOIN_REJECTED -> stringResource(R.string.notif_type_group_join_rejected)
    NotificationType.SPACE -> stringResource(R.string.notif_type_space)
    NotificationType.WELCOME -> stringResource(R.string.notif_type_welcome)
    NotificationType.MENTION -> stringResource(R.string.notif_type_mention)
    NotificationType.STORY_REPLY -> stringResource(R.string.notif_type_story_reply)
    NotificationType.MESSAGE -> stringResource(R.string.notif_type_message)
    NotificationType.SYSTEM -> stringResource(R.string.notif_type_system)
}

/** Remote rows only store the type and actor; the sentence is localized on the client. */
@Composable
private fun notificationMessage(notification: NexaNotification): String {
    val actor = notification.actorName.orEmpty()
    return when (notification.type) {
        NotificationType.LIKE -> stringResource(R.string.notif_post_like, actor)
        NotificationType.COMMENT_LIKE -> stringResource(R.string.notif_comment_like, actor)
        NotificationType.COMMENT -> stringResource(R.string.notif_post_comment, actor)
        NotificationType.REPLY -> stringResource(R.string.notif_comment_reply, actor)
        NotificationType.FOLLOW -> stringResource(R.string.notif_follow, actor)
        NotificationType.FOLLOW_REQUEST_ACCEPTED -> stringResource(R.string.notif_follow_request_accepted)
        NotificationType.MENTION -> stringResource(R.string.notif_mention, actor)
        NotificationType.STORY_REPLY -> stringResource(R.string.notif_story_reply, actor)
        NotificationType.MESSAGE -> stringResource(R.string.notif_message, actor)
        NotificationType.GROUP_JOIN_REQUEST -> stringResource(R.string.notif_group_join_request, actor)
        NotificationType.GROUP_JOIN_APPROVED -> stringResource(R.string.notif_group_join_approved)
        NotificationType.GROUP_JOIN_REJECTED -> stringResource(R.string.notif_group_join_rejected)
        else -> notification.body
    }
}

@Composable
private fun NotificationRow(
    notification: NexaNotification,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (notification.isRead) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            )
            .clickable { onClick() }
            .padding(NexaSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (notification.actorName != null) {
            NexaAvatar(
                displayName = notification.actorName,
                avatarUri = notification.actorAvatarUri,
                size = 42.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = notificationIcon(notification.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(NexaSpacing.md))
        val title = if (notification.title.isNotBlank()) {
            notification.title
        } else {
            notificationMessage(notification)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = buildString {
                    if (notification.body.isNotBlank()) {
                        append(notification.body)
                        append(" · ")
                    }
                    append(TimeUtils.relativeFromNow(notification.timestamp))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!notification.isRead) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
