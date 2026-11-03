package com.nexa.social.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.domain.model.Space
import com.nexa.social.domain.model.SpaceCategory
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun SpacesScreen(
    communityId: String,
    onBack: () -> Unit,
    onOpenSpace: (String) -> Unit,
    onCreateSpace: () -> Unit,
    viewModel: SpacesViewModel = viewModel(factory = SpacesViewModel.factory(communityId))
) {
    val spaces by viewModel.spaces.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(
            title = stringResource(R.string.spaces_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = onCreateSpace) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.space_create_title))
                }
            }
        )
        LocalDataBanner(
            text = stringResource(R.string.spaces_local_notice),
            modifier = Modifier.padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.sm)
        )
        if (spaces.isEmpty()) {
            NexaEmptyState(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = stringResource(R.string.spaces_empty_title),
                body = stringResource(R.string.spaces_empty_body)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = NexaSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
            ) {
                items(spaces, key = { it.id }) { space ->
                    SpaceCard(space = space, onClick = { onOpenSpace(space.id) })
                }
            }
        }
    }
}

@Composable
private fun SpaceCard(space: Space, onClick: () -> Unit) {
    NexaCard(onClick = onClick) {
        Text(text = space.title, style = MaterialTheme.typography.titleMedium)
        if (space.description.isNotBlank()) {
            Text(
                text = space.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = spaceCategoryLabel(space.category),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = NexaSpacing.xs)
        )
    }
}

@Composable
fun spaceCategoryLabel(category: SpaceCategory): String = when (category) {
    SpaceCategory.PROGRAMMING -> stringResource(R.string.space_cat_programming)
    SpaceCategory.DESIGN -> stringResource(R.string.space_cat_design)
    SpaceCategory.EDUCATION -> stringResource(R.string.space_cat_education)
    SpaceCategory.TECH -> stringResource(R.string.space_cat_tech)
    SpaceCategory.CREATIVE -> stringResource(R.string.space_cat_creative)
    SpaceCategory.DISCUSSION -> stringResource(R.string.space_cat_discussion)
}
