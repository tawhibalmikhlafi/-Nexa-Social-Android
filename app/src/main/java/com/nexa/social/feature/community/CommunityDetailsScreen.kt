package com.nexa.social.feature.community

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.ui.components.CommunityPostCard
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaCover
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.rememberImagePicker
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun CommunityDetailsScreen(
    communityId: String,
    onBack: () -> Unit,
    onOpenSpaces: (String) -> Unit = {},
    onEditWorld: (String) -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
    viewModel: CommunityDetailsViewModel = viewModel(
        factory = CommunityDetailsViewModel.factory(communityId),
        key = communityId
    )
) {
    val community by viewModel.community.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val current = community
    if (current == null) {
        Box(Modifier.fillMaxSize()) { NexaTopBar(title = stringResource(R.string.worlds_title), onBack = onBack) }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(
            title = current.name,
            onBack = onBack,
            actions = {
                androidx.compose.material3.IconButton(onClick = { onOpenSpaces(current.id) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = stringResource(R.string.spaces_title)
                    )
                }
                if (current.createdByMe) {
                    androidx.compose.material3.IconButton(onClick = { onEditWorld(current.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.world_edit_title)
                        )
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = NexaSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = NexaSpacing.lg)) {
                    NexaCover(
                        seed = current.coverColorSeed,
                        coverUri = current.coverUri,
                        height = 150.dp
                    )
                    Spacer(Modifier.height(NexaSpacing.md))
                    Text(text = current.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(NexaSpacing.xs))
                    Text(
                        text = current.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(NexaSpacing.sm))
                    Text(
                        text = "${current.sampleMemberCount} ${stringResource(R.string.community_members)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(NexaSpacing.md))
                    if (current.isJoinedLocally) {
                        NexaSecondaryButton(
                            text = stringResource(R.string.action_leave),
                            onClick = viewModel::toggleMembership
                        )
                    } else {
                        NexaPrimaryButton(
                            text = stringResource(R.string.action_join),
                            onClick = viewModel::toggleMembership
                        )
                    }
                    Spacer(Modifier.height(NexaSpacing.sm))
                    LocalDataBanner(text = stringResource(R.string.community_join_local_notice))
                }
            }

            item {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.community_tab_posts)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.community_tab_channels)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(R.string.community_tab_events)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text(stringResource(R.string.community_tab_about)) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    if (current.isJoinedLocally) {
                        item {
                            CommunityComposer(
                                onPublish = { text, imageUri ->
                                    viewModel.createPost(text, listOfNotNull(imageUri)) { }
                                }
                            )
                        }
                    }
                    if (posts.isEmpty()) {
                        item {
                            NexaEmptyState(
                                icon = Icons.Filled.Forum,
                                title = stringResource(R.string.community_no_posts),
                                body = stringResource(R.string.create_hint)
                            )
                        }
                    } else {
                        items(posts, key = { it.id }) { post ->
                            CommunityPostCard(
                                post = post,
                                onLike = { viewModel.togglePostLike(post.id) },
                                onReaction = { emoji -> viewModel.togglePostLike(post.id, emoji) },
                                onOpenProfile = onOpenProfile,
                                modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                            )
                        }
                    }
                }
                1 -> {
                    if (current.channels.isEmpty()) {
                        item {
                            NexaEmptyState(
                                icon = Icons.Filled.Tag,
                                title = stringResource(R.string.community_channels_empty),
                                body = stringResource(R.string.community_tab_channels)
                            )
                        }
                    } else {
                        items(current.channels, key = { it.id }) { channel ->
                            NexaCard(modifier = Modifier.padding(horizontal = NexaSpacing.lg)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.padding(start = NexaSpacing.sm)) {
                                        Text(text = channel.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = channel.topic,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (current.events.isEmpty()) {
                        item {
                            NexaEmptyState(
                                icon = Icons.Filled.CalendarMonth,
                                title = stringResource(R.string.community_events_empty),
                                body = stringResource(R.string.community_tab_events)
                            )
                        }
                    } else {
                        items(current.events, key = { it.id }) { event ->
                            NexaCard(modifier = Modifier.padding(horizontal = NexaSpacing.lg)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.padding(start = NexaSpacing.sm)) {
                                        Text(text = event.title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "${event.location} · ${TimeUtils.relativeFromNow(event.startsAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    item {
                        Column(modifier = Modifier.padding(horizontal = NexaSpacing.lg)) {
                            Text(text = stringResource(R.string.community_rules), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(NexaSpacing.sm))
                            current.rules.forEach { rule ->
                                Row(
                                    modifier = Modifier.padding(vertical = NexaSpacing.xxs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tag,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = NexaSpacing.sm),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(text = rule, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityComposer(onPublish: (String, String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var imageUri by rememberSaveable { mutableStateOf<String?>(null) }
    val pickImage = rememberImagePicker { uri -> imageUri = uri?.toString() }

    NexaCard(modifier = Modifier.padding(horizontal = NexaSpacing.lg)) {
        NexaAvatar(displayName = "N", avatarUri = null, size = 34.dp)
        Spacer(Modifier.height(NexaSpacing.sm))
        NexaTextField(
            value = text,
            onValueChange = { text = it },
            label = stringResource(R.string.create_hint),
            singleLine = false,
            imeAction = androidx.compose.ui.text.input.ImeAction.Default
        )
        if (imageUri != null) {
            Spacer(Modifier.height(NexaSpacing.sm))
            NexaCover(seed = imageUri!!.hashCode().toLong(), coverUri = imageUri, height = 140.dp)
        }
        Spacer(Modifier.height(NexaSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
            NexaSecondaryButton(
                text = stringResource(R.string.create_add_image),
                onClick = pickImage,
                modifier = Modifier.weight(1f)
            )
            NexaPrimaryButton(
                text = stringResource(R.string.create_publish),
                enabled = text.isNotBlank() || imageUri != null,
                onClick = {
                    onPublish(text, imageUri)
                    text = ""
                    imageUri = null
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
