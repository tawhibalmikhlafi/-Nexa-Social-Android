package com.nexa.social.feature.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.core.ui.rememberRemoteMode
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.PostCard
import com.nexa.social.ui.share.sharePost
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.launch

@Composable
fun SavedPostsScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: SavedPostsViewModel = viewModel(factory = SavedPostsViewModel.Factory)
) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val remoteMode = rememberRemoteMode()
    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHostState.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val shareFailedMessage = stringResource(R.string.post_share_failed)

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.saved_title), onBack = onBack)
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.saved_tab_saved)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.saved_tab_hidden)) })
        }
        if (!remoteMode) {
            LocalDataBanner(
                text = stringResource(R.string.saved_local_notice),
                modifier = Modifier.padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.sm)
            )
        }

        if (tab == 0) {
            if (saved.isEmpty()) {
                NexaEmptyState(
                    icon = Icons.Filled.Bookmark,
                    title = stringResource(R.string.saved_empty_title),
                    body = stringResource(R.string.saved_empty_body)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = NexaSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
                ) {
                    items(saved, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onLike = { viewModel.toggleLike(post.id) },
                            onReaction = { emoji -> viewModel.toggleLike(post.id, emoji) },
                            onComment = { onOpenPost(post.id) },
                            onShare = {
                                if (!sharePost(context, post.id, post.text)) {
                                    scope.launch { snackbarHost?.showSnackbar(shareFailedMessage) }
                                }
                            },
                            onSave = { viewModel.toggleSave(post.id) },
                            onOpenProfile = onOpenProfile,
                            onOpenPost = { onOpenPost(post.id) }
                        )
                    }
                }
            }
        } else {
            if (hidden.isEmpty()) {
                NexaEmptyState(
                    icon = Icons.Filled.VisibilityOff,
                    title = stringResource(R.string.saved_hidden_empty_title),
                    body = stringResource(R.string.saved_hidden_empty_body)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = NexaSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
                ) {
                    items(hidden, key = { it.id }) { post ->
                        Column {
                            PostCard(
                                post = post,
                                onLike = { viewModel.toggleLike(post.id) },
                                onReaction = { emoji -> viewModel.toggleLike(post.id, emoji) },
                                onComment = { onOpenPost(post.id) },
                                onShare = {
                                    if (!sharePost(context, post.id, post.text)) {
                                        scope.launch { snackbarHost?.showSnackbar(shareFailedMessage) }
                                    }
                                },
                                onSave = { },
                                onOpenProfile = onOpenProfile,
                                onOpenPost = { onOpenPost(post.id) }
                            )
                            NexaSecondaryButton(
                                text = stringResource(R.string.saved_unhide),
                                onClick = { viewModel.unhide(post.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
