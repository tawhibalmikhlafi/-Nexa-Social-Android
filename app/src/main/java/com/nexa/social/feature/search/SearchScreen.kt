package com.nexa.social.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.core.ui.rememberRemoteMode
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaChoiceChipRow
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaTextButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenWorld: (String) -> Unit,
    onOpenSpace: (String) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val remoteMode = rememberRemoteMode()

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.search_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) content@ {
            NexaTextField(
                value = query,
                onValueChange = viewModel::updateQuery,
                label = stringResource(R.string.search_label),
                placeholder = stringResource(R.string.search_hint_all),
                leadingIcon = Icons.Filled.Search,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = viewModel::clear) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                }
            )
            LocalDataBanner(
                text = stringResource(
                    if (remoteMode) R.string.search_remote_notice else R.string.search_local_notice
                )
            )
            NexaChoiceChipRow(
                options = SearchTab.entries,
                selected = tab,
                labelOf = { searchTabLabel(it) },
                onSelect = { viewModel.selectTab(it) }
            )

            if (query.isBlank()) {
                if (history.isEmpty()) {
                    NexaEmptyState(
                        icon = Icons.Filled.PersonSearch,
                        title = stringResource(R.string.search_empty_title),
                        body = stringResource(R.string.search_empty_body)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(R.string.search_history_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        NexaTextButton(
                            text = stringResource(R.string.search_history_clear),
                            onClick = viewModel::clearHistory
                        )
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(NexaSpacing.xs)) {
                        items(history) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateQuery(entry) }
                                    .padding(vertical = NexaSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null)
                                Spacer(Modifier.width(NexaSpacing.md))
                                Text(text = entry, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                return@content
            }

            when (tab) {
                SearchTab.PEOPLE -> ResultsList(
                    items = results.people,
                    empty = results.people.isEmpty(),
                    emptyBody = R.string.search_no_accounts_body,
                    key = { it.id }
                ) { user ->
                    ResultRow(
                        title = user.displayName,
                        subtitle = "@${user.username}",
                        avatarName = user.displayName,
                        avatarUri = user.avatarUri,
                        onClick = {
                            viewModel.commitHistory()
                            onOpenProfile(user.id)
                        }
                    )
                }
                SearchTab.WORLDS -> ResultsList(
                    items = results.worlds,
                    empty = results.worlds.isEmpty(),
                    key = { it.id }
                ) { world ->
                    ResultRow(
                        title = world.name,
                        subtitle = world.description,
                        avatarName = world.name,
                        avatarUri = null,
                        onClick = {
                            viewModel.commitHistory()
                            onOpenWorld(world.id)
                        }
                    )
                }
                SearchTab.SPACES -> ResultsList(
                    items = results.spaces,
                    empty = results.spaces.isEmpty(),
                    key = { it.id }
                ) { space ->
                    ResultRow(
                        title = space.title,
                        subtitle = space.description,
                        avatarName = space.title,
                        avatarUri = null,
                        onClick = {
                            viewModel.commitHistory()
                            onOpenSpace(space.id)
                        }
                    )
                }
                SearchTab.POSTS -> ResultsList(
                    items = results.posts,
                    empty = results.posts.isEmpty(),
                    key = { it.id }
                ) { post ->
                    ResultRow(
                        title = post.author.displayName,
                        subtitle = post.text,
                        avatarName = post.author.displayName,
                        avatarUri = post.author.avatarUri,
                        onClick = {
                            viewModel.commitHistory()
                            onOpenPost(post.id)
                        }
                    )
                }
                SearchTab.TAGS -> {
                    if (results.tags.isEmpty()) {
                        NoResults()
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                            items(results.tags) { tag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.updateQuery(tag) }
                                        .padding(vertical = NexaSpacing.sm)
                                ) {
                                    Text(text = "#$tag", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
private fun <T> ResultsList(
    items: List<T>,
    empty: Boolean,
    emptyBody: Int = R.string.search_no_results_body,
    key: (T) -> Any,
    row: @Composable (T) -> Unit
) {
    if (empty) {
        NoResults(emptyBody)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
            items(items, key = key) { item -> row(item) }
        }
    }
}

@Composable
private fun NoResults(bodyRes: Int = R.string.search_no_results_body) {
    NexaEmptyState(
        icon = Icons.Filled.Search,
        title = stringResource(R.string.search_no_results_title),
        body = stringResource(bodyRes)
    )
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    avatarName: String,
    avatarUri: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = NexaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexaAvatar(displayName = avatarName, avatarUri = avatarUri, size = 46.dp)
        Spacer(Modifier.width(NexaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun searchTabLabel(tab: SearchTab): String = when (tab) {
    SearchTab.PEOPLE -> stringResource(R.string.search_tab_people)
    SearchTab.POSTS -> stringResource(R.string.search_tab_posts)
    SearchTab.WORLDS -> stringResource(R.string.search_tab_worlds)
    SearchTab.SPACES -> stringResource(R.string.search_tab_spaces)
    SearchTab.TAGS -> stringResource(R.string.search_tab_tags)
}
