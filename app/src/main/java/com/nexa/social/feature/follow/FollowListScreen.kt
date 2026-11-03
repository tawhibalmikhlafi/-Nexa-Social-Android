package com.nexa.social.feature.follow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.domain.model.User
import com.nexa.social.feature.presence.PresenceViewModel
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaTextButton
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.OnlineAvatar
import com.nexa.social.ui.components.initialsOf
import com.nexa.social.ui.theme.NexaSpacing

val FollowListMode.titleRes: Int
    get() = when (this) {
        FollowListMode.FOLLOWERS -> R.string.follow_list_followers_title
        FollowListMode.FOLLOWING -> R.string.follow_list_following_title
        FollowListMode.ACTIVE -> R.string.friends_tab_active
        FollowListMode.SUGGESTIONS -> R.string.friends_tab_suggestions
    }

val FollowListMode.emptyTitleRes: Int
    get() = when (this) {
        FollowListMode.FOLLOWERS -> R.string.follow_list_empty_followers
        FollowListMode.FOLLOWING -> R.string.follow_list_empty_following
        FollowListMode.ACTIVE -> R.string.friends_active_empty_title
        FollowListMode.SUGGESTIONS -> R.string.friends_suggestions_empty_title
    }

val FollowListMode.emptyBodyRes: Int
    get() = when (this) {
        FollowListMode.ACTIVE -> R.string.friends_active_empty_body
        FollowListMode.SUGGESTIONS -> R.string.friends_suggestions_empty_body
        else -> R.string.follow_list_empty_body
    }

@Composable
fun FollowListScreen(
    targetUserId: String,
    mode: FollowListMode,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: FollowListViewModel = viewModel(
        factory = FollowListViewModel.factory(targetUserId, mode),
        key = "follow_${mode.name}_$targetUserId"
    ),
    presenceViewModel: PresenceViewModel = viewModel(factory = PresenceViewModel.Factory)
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val overrides by viewModel.overrides.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val requestPending by viewModel.requestPending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(
            title = stringResource(mode.titleRes),
            onBack = onBack
        )

        FollowListContent(
            users = users,
            followingIds = followingIds,
            currentUserId = currentUserId,
            overrides = overrides,
            pending = pending,
            requestPending = requestPending,
            emptyTitle = stringResource(mode.emptyTitleRes),
            emptyBody = stringResource(mode.emptyBodyRes),
            onOpenProfile = onOpenProfile,
            onToggleFollowing = viewModel::toggleFollowing,
            presenceViewModel = presenceViewModel,
            error = error,
            onConsumeError = viewModel::consumeError
        )
    }
}

/**
 * Shared follow list body. Reused by the standalone follow screens and the friends screen so the
 * list, empty states and follow action are defined once.
 */
@Composable
fun FollowListContent(
    users: List<User>,
    followingIds: Set<String>,
    currentUserId: String?,
    overrides: Map<String, Boolean>,
    pending: Set<String>,
    requestPending: Set<String> = emptySet(),
    emptyTitle: String,
    emptyBody: String,
    onOpenProfile: (String) -> Unit,
    onToggleFollowing: (String, Boolean) -> Unit,
    presenceViewModel: PresenceViewModel,
    modifier: Modifier = Modifier,
    emptyIcon: ImageVector = Icons.Filled.People,
    error: Boolean = false,
    onConsumeError: () -> Unit = {}
) {
    val snackbarHost = LocalSnackbarHostState.current
    val errorMessage = stringResource(R.string.follow_action_failed)

    LaunchedEffect(error) {
        if (error) {
            snackbarHost?.showSnackbar(errorMessage)
            onConsumeError()
        }
    }

    if (users.isEmpty()) {
        NexaEmptyState(
            icon = emptyIcon,
            title = emptyTitle,
            body = emptyBody
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = NexaSpacing.lg,
                vertical = NexaSpacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
        ) {
            items(users, key = { it.id }) { user ->
                FollowRow(
                    user = user,
                    isSelf = currentUserId == null || user.id == currentUserId,
                    isFollowing = overrides[user.id] ?: (user.id in followingIds),
                    busy = user.id in pending,
                    isFollowRequestPending = user.id in requestPending,
                    onOpen = { onOpenProfile(user.id) },
                    onToggle = { follow -> onToggleFollowing(user.id, follow) },
                    presenceViewModel = presenceViewModel
                )
            }
        }
    }
}

@Composable
private fun FollowRow(
    user: User,
    isSelf: Boolean,
    isFollowing: Boolean,
    busy: Boolean,
    isFollowRequestPending: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    presenceViewModel: PresenceViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = NexaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OnlineAvatar(
            userId = user.id,
            imageUrl = user.avatarUri,
            size = 48.dp,
            showPresence = !isSelf,
            fallbackInitials = initialsOf(user.displayName),
            presenceViewModel = presenceViewModel
        )
        Spacer(Modifier.width(NexaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!isSelf) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                NexaTextButton(
                    text = stringResource(
                        when {
                            isFollowRequestPending -> R.string.action_follow_requested
                            isFollowing -> R.string.action_unfollow
                            else -> R.string.action_follow
                        }
                    ),
                    onClick = { onToggle(!isFollowing && !isFollowRequestPending) }
                )
            }
        }
    }
}
