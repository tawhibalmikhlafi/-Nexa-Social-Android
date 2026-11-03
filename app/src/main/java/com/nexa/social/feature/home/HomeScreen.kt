package com.nexa.social.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.core.ui.rememberRemoteMode
import com.nexa.social.domain.model.FeedType
import com.nexa.social.domain.model.Gathering
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.StoryGroup
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaChoiceChip
import com.nexa.social.ui.components.NexaConfirmDialog
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaWordmark
import com.nexa.social.ui.components.PostCard
import com.nexa.social.ui.share.sharePost
import com.nexa.social.ui.theme.NexaRadius
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onCreatePost: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (String) -> Unit = {},
    onOpenStory: (String) -> Unit = {},
    onCreateStory: () -> Unit = {},
    onOpenLabs: () -> Unit = {},
    onOpenGatherings: () -> Unit = {},
    onOpenGroups: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val stories by viewModel.storyGroups.collectAsStateWithLifecycle()
    val nextGathering by viewModel.nextGathering.collectAsStateWithLifecycle()
    val unread by viewModel.unreadCount.collectAsStateWithLifecycle()
    val feedType by viewModel.feedType.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle()

    var postToDelete by remember { mutableStateOf<Post?>(null) }

    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val remoteMode = rememberRemoteMode()
    val shareFailedMessage = stringResource(R.string.post_share_failed)
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            feed.isNotEmpty() && last >= feed.size - 2
        }
    }

    LaunchedEffect(shouldLoadMore, feed.size) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = NexaSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
    ) {
        item {
            HomeHeader(
                unread = unread,
                onCreatePost = onCreatePost,
                onOpenGroups = onOpenGroups,
                onOpenLabs = onOpenLabs,
                onSearch = onSearch,
                onNotifications = onNotifications
            )
        }

        item {
            FeedTypeRow(
                selected = feedType,
                onSelect = viewModel::setFeedType
            )
        }

        if (!remoteMode) {
            item {
                LocalDataBanner(
                    text = stringResource(R.string.home_local_feed_notice),
                    modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                )
            }
        }

        item {
            StoryTray(
                stories = stories,
                onCreateStory = onCreateStory,
                onOpenStory = onOpenStory
            )
        }

        item {
            CreatePostCard(
                onCreatePost = onCreatePost,
                modifier = Modifier.padding(horizontal = NexaSpacing.lg)
            )
        }

        // Gatherings are currently stored locally; do not present them as live server data
        // to a remote Supabase account until a remote gatherings table/repository is configured.
        if (!remoteMode) {
            nextGathering?.let { gathering ->
                item {
                    UpcomingGatheringCard(
                        gathering = gathering,
                        onOpen = onOpenGatherings,
                        modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                    )
                }
            }
        }

        if (feed.isEmpty()) {
            item {
                NexaEmptyState(
                    icon = Icons.Filled.Groups,
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                )
            }
        } else {
            items(feed, key = { it.id }) { post ->
                val commentPreview by viewModel.observeCommentPreview(post.id).collectAsStateWithLifecycle(initialValue = emptyList())
                PostCard(
                    post = post,
                    commentPreview = commentPreview.take(2),
                    onLike = { viewModel.toggleLike(post.id) },
                    onReaction = { emoji -> viewModel.toggleLike(post.id, emoji) },
                    onComment = { onOpenPost(post.id) },
                    onShare = {
                        if (sharePost(context, post.id, post.text)) {
                            viewModel.recordShare(post.id)
                        } else {
                            scope.launch { snackbarHost?.showSnackbar(shareFailedMessage) }
                        }
                    },
                    onSave = { viewModel.toggleSave(post.id) },
                    onDelete = if (post.author.id == userId) {
                        { postToDelete = post }
                    } else {
                        null
                    },
                    onOpenProfile = onOpenProfile,
                    onOpenPost = { onOpenPost(post.id) },
                    modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                )
            }
        }
    }

    postToDelete?.let { post ->
        NexaConfirmDialog(
            title = stringResource(R.string.post_delete),
            message = stringResource(R.string.post_delete_confirm),
            destructive = true,
            confirmLabel = stringResource(R.string.post_delete),
            onConfirm = {
                postToDelete = null
                viewModel.deletePost(post.id)
            },
            onDismiss = { postToDelete = null }
        )
    }
}

@Composable
private fun HomeHeader(
    unread: Int,
    onCreatePost: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenLabs: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexaWordmark(modifier = Modifier.weight(1f), markSize = 32.dp, showYear = false)
        CompactIconButton(
            onClick = onOpenGroups,
            icon = Icons.Filled.Groups,
            contentDescription = stringResource(R.string.groups_title)
        )
        CompactIconButton(
            onClick = onCreatePost,
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.home_create_hint)
        )
        CompactIconButton(
            onClick = onOpenLabs,
            icon = Icons.Filled.AutoAwesome,
            contentDescription = stringResource(R.string.labs_title)
        )
        CompactIconButton(
            onClick = onSearch,
            icon = Icons.Filled.Search,
            contentDescription = stringResource(R.string.action_search)
        )
        NotificationButton(unread = unread, onClick = onNotifications)
    }
}

@Composable
private fun NotificationButton(unread: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        if (unread > 0) {
            BadgedBox(
                badge = {
                    Badge {
                        Text(text = if (unread > 99) "99+" else unread.toString())
                    }
                }
            ) {
                NotificationIcon()
            }
        } else {
            NotificationIcon()
        }
    }
}

@Composable
private fun NotificationIcon() {
    Icon(
        imageVector = Icons.Filled.Notifications,
        contentDescription = stringResource(R.string.notifications_title),
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun FeedTypeRow(
    selected: FeedType,
    onSelect: (FeedType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NexaSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
    ) {
        NexaChoiceChip(
            label = stringResource(R.string.home_tab_for_you),
            selected = selected == FeedType.LATEST,
            onClick = { onSelect(FeedType.LATEST) }
        )
        NexaChoiceChip(
            label = stringResource(R.string.home_tab_following),
            selected = selected == FeedType.FOLLOWING,
            onClick = { onSelect(FeedType.FOLLOWING) }
        )
    }
}

@Composable
private fun StoryTray(
    stories: List<StoryGroup>,
    onCreateStory: () -> Unit,
    onOpenStory: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.stories_label),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = NexaSpacing.lg)
        )
        Spacer(Modifier.height(NexaSpacing.sm))
        LazyRow(
            contentPadding = PaddingValues(horizontal = NexaSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            item(key = "create_story") {
                CreateStoryBubble(onClick = onCreateStory)
            }
            items(stories, key = { it.author.id }) { group ->
                StoryBubble(
                    group = group,
                    onClick = { onOpenStory(group.author.id) }
                )
            }
        }
    }
}

@Composable
private fun CreateStoryBubble(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(NexaRadius.chip))
            .clickable(onClick = onClick)
            .padding(vertical = NexaSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.sweepGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            Color(0xFFD946EF),
                            Color(0xFF8B5CF6),
                            MaterialTheme.colorScheme.primary
                        )
                    )
                )
                .padding(3.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .padding(2.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.story_create_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(NexaSpacing.xs))
        Text(
            text = stringResource(R.string.story_add_short),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StoryBubble(group: StoryGroup, onClick: () -> Unit) {
    val ringColor = if (group.hasUnseen) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }
    Column(
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(NexaRadius.chip))
            .clickable(onClick = onClick)
            .padding(vertical = NexaSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(
                    if (group.hasUnseen) {
                        androidx.compose.ui.graphics.Brush.sweepGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                Color(0xFFD946EF),
                                Color(0xFF8B5CF6),
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    } else {
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(ringColor, ringColor.copy(alpha = 0.55f))
                        )
                    }
                )
                .padding(3.dp)
                .background(MaterialTheme.colorScheme.background, CircleShape)
                .padding(2.dp)
        ) {
            NexaAvatar(
                displayName = group.author.displayName,
                avatarUri = group.author.avatarUri,
                size = 56.dp
            )
        }
        Spacer(Modifier.height(NexaSpacing.xs))
        Text(
            text = group.author.displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CreatePostCard(
    onCreatePost: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexaCard(modifier = modifier, onClick = onCreatePost) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    NexaWordmark()
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_create_hint),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                                RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = NexaSpacing.lg, vertical = 13.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                ) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        tint = Color(0xFF38B66B),
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = stringResource(R.string.post_add_image),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                ) {
                    Icon(
                        Icons.Filled.EmojiEmotions,
                        contentDescription = null,
                        tint = Color(0xFFE8A33A),
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = stringResource(R.string.post_feeling),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f))
                        .padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = stringResource(R.string.post_publish),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingGatheringCard(
    gathering: Gathering,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexaCard(modifier = modifier, onClick = onOpen) {
        Text(
            text = stringResource(R.string.home_gathering_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(NexaSpacing.xs))
        Text(
            text = gathering.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(NexaSpacing.xs))
        Text(
            text = TimeUtils.dateTime(gathering.startsAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (gathering.location.isNotBlank()) {
            Text(
                text = gathering.location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
