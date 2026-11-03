package com.nexa.social.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.feature.follow.FollowListContent
import com.nexa.social.feature.follow.FollowListMode
import com.nexa.social.feature.follow.FollowListViewModel
import com.nexa.social.feature.follow.emptyBodyRes
import com.nexa.social.feature.follow.emptyTitleRes
import com.nexa.social.feature.follow.titleRes
import com.nexa.social.feature.presence.PresenceViewModel
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

/**
 * Friends hub opened from the profile tab. Reuses the existing follow list (content + view model)
 * for active accounts, following, followers and suggestions, so nothing is duplicated.
 */
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    presenceViewModel: PresenceViewModel = viewModel(factory = PresenceViewModel.Factory)
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val suggestionsViewModel: FollowListViewModel = viewModel(
        factory = FollowListViewModel.factory("", FollowListMode.SUGGESTIONS),
        key = "friends_suggestions"
    )
    val currentUserId by suggestionsViewModel.currentUserId.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.friends_title), onBack = onBack)

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = NexaSpacing.lg
        ) {
            FriendsTab(
                selected = selectedTab == 0,
                label = stringResource(FollowListMode.ACTIVE.titleRes),
                onClick = { selectedTab = 0 }
            )
            FriendsTab(
                selected = selectedTab == 1,
                label = stringResource(FollowListMode.FOLLOWING.titleRes),
                onClick = { selectedTab = 1 }
            )
            FriendsTab(
                selected = selectedTab == 2,
                label = stringResource(FollowListMode.FOLLOWERS.titleRes),
                onClick = { selectedTab = 2 }
            )
            FriendsTab(
                selected = selectedTab == 3,
                label = stringResource(FollowListMode.SUGGESTIONS.titleRes),
                onClick = { selectedTab = 3 }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> FollowTab(
                    userId = currentUserId,
                    mode = FollowListMode.ACTIVE,
                    onOpenProfile = onOpenProfile,
                    presenceViewModel = presenceViewModel
                )
                1 -> FollowTab(
                    userId = currentUserId,
                    mode = FollowListMode.FOLLOWING,
                    onOpenProfile = onOpenProfile,
                    presenceViewModel = presenceViewModel
                )
                2 -> FollowTab(
                    userId = currentUserId,
                    mode = FollowListMode.FOLLOWERS,
                    onOpenProfile = onOpenProfile,
                    presenceViewModel = presenceViewModel
                )
                else -> SuggestionsTab(
                    viewModel = suggestionsViewModel,
                    onOpenProfile = onOpenProfile,
                    presenceViewModel = presenceViewModel
                )
            }
        }
    }
}

@Composable
private fun FriendsTab(selected: Boolean, label: String, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun FollowTab(
    userId: String?,
    mode: FollowListMode,
    onOpenProfile: (String) -> Unit,
    presenceViewModel: PresenceViewModel
) {
    val emptyTitle = stringResource(mode.emptyTitleRes)
    val emptyBody = stringResource(mode.emptyBodyRes)
    if (userId == null) {
        NexaEmptyState(
            icon = Icons.Filled.People,
            title = emptyTitle,
            body = emptyBody
        )
        return
    }

    val followViewModel: FollowListViewModel = viewModel(
        factory = FollowListViewModel.factory(userId, mode),
        key = "friends_${mode.name}_$userId"
    )
    val users by followViewModel.users.collectAsStateWithLifecycle()
    val followingIds by followViewModel.followingIds.collectAsStateWithLifecycle()
    val currentUserId by followViewModel.currentUserId.collectAsStateWithLifecycle()
    val overrides by followViewModel.overrides.collectAsStateWithLifecycle()
    val pending by followViewModel.pending.collectAsStateWithLifecycle()
    val error by followViewModel.error.collectAsStateWithLifecycle()

    FollowListContent(
        users = users,
        followingIds = followingIds,
        currentUserId = currentUserId,
        overrides = overrides,
        pending = pending,
        emptyTitle = emptyTitle,
        emptyBody = emptyBody,
        onOpenProfile = onOpenProfile,
        onToggleFollowing = followViewModel::toggleFollowing,
        presenceViewModel = presenceViewModel,
        error = error,
        onConsumeError = followViewModel::consumeError
    )
}

@Composable
private fun SuggestionsTab(
    viewModel: FollowListViewModel,
    onOpenProfile: (String) -> Unit,
    presenceViewModel: PresenceViewModel
) {
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    if (currentUserId == null) {
        NexaEmptyState(
            icon = Icons.Filled.AutoAwesome,
            title = stringResource(FollowListMode.SUGGESTIONS.emptyTitleRes),
            body = stringResource(FollowListMode.SUGGESTIONS.emptyBodyRes)
        )
        return
    }

    val users by viewModel.users.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val overrides by viewModel.overrides.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    FollowListContent(
        users = users,
        followingIds = followingIds,
        currentUserId = currentUserId,
        overrides = overrides,
        pending = pending,
        emptyTitle = stringResource(FollowListMode.SUGGESTIONS.emptyTitleRes),
        emptyBody = stringResource(FollowListMode.SUGGESTIONS.emptyBodyRes),
        emptyIcon = Icons.Filled.AutoAwesome,
        onOpenProfile = onOpenProfile,
        onToggleFollowing = viewModel::toggleFollowing,
        presenceViewModel = presenceViewModel,
        error = error,
        onConsumeError = viewModel::consumeError
    )
}

