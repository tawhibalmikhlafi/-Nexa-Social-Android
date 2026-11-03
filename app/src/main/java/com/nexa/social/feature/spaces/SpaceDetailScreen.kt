package com.nexa.social.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.SheetTitle
import com.nexa.social.ui.components.NexaBottomSheet
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun SpaceDetailScreen(
    spaceId: String,
    onBack: () -> Unit,
    viewModel: SpaceDetailViewModel = viewModel(factory = SpaceDetailViewModel.factory(spaceId))
) {
    val space by viewModel.space.collectAsStateWithLifecycle()
    var showAddResource by remember { mutableStateOf(false) }
    var showAddChallenge by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = space?.title ?: stringResource(R.string.spaces_title), onBack = onBack)

        val current = space
        if (current == null) {
            NexaEmptyState(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = stringResource(R.string.state_empty_generic),
                body = stringResource(R.string.spaces_local_notice)
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            LocalDataBanner(text = stringResource(R.string.spaces_local_notice))
            if (current.description.isNotBlank()) {
                Text(text = current.description, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                text = spaceCategoryLabel(current.category),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.space_resources_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showAddResource = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.space_add_resource))
                }
            }
            if (current.resources.isEmpty()) {
                Text(
                    text = stringResource(R.string.space_resources_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                current.resources.forEach { resource ->
                    NexaCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = resource.title, style = MaterialTheme.typography.titleSmall)
                                if (resource.description.isNotBlank()) {
                                    Text(text = resource.description, style = MaterialTheme.typography.bodySmall)
                                }
                                resource.reference?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (current.isMine) {
                                IconButton(onClick = { viewModel.removeResource(resource.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove))
                                }
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.space_challenges_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showAddChallenge = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.space_add_challenge))
                }
            }
            if (current.challenges.isEmpty()) {
                Text(
                    text = stringResource(R.string.space_challenges_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                current.challenges.forEach { challenge ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = challenge.doneLocally,
                            onCheckedChange = { viewModel.toggleChallenge(challenge.id) }
                        )
                        Text(text = challenge.prompt, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(NexaSpacing.xxxl))
        }
    }

    if (showAddResource) {
        AddResourceSheet(
            onDismiss = { showAddResource = false },
            onAdd = { title, description, reference ->
                viewModel.addResource(title, description, reference)
                showAddResource = false
            }
        )
    }

    if (showAddChallenge) {
        AddChallengeSheet(
            onDismiss = { showAddChallenge = false },
            onAdd = { prompt ->
                viewModel.addChallenge(prompt)
                showAddChallenge = false
            }
        )
    }
}

@Composable
private fun AddResourceSheet(
    onDismiss: () -> Unit,
    onAdd: (String, String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    NexaBottomSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.space_add_resource))
        NexaTextField(value = title, onValueChange = { title = it }, label = stringResource(R.string.space_resource_title_label))
        Spacer(Modifier.height(NexaSpacing.sm))
        NexaTextField(
            value = description,
            onValueChange = { description = it },
            label = stringResource(R.string.space_resource_desc_label),
            singleLine = false
        )
        Spacer(Modifier.height(NexaSpacing.sm))
        NexaTextField(
            value = reference,
            onValueChange = { reference = it },
            label = stringResource(R.string.space_resource_ref_label)
        )
        Spacer(Modifier.height(NexaSpacing.md))
        TextButton(onClick = { if (title.isNotBlank()) onAdd(title, description, reference.ifBlank { null }) }) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun AddChallengeSheet(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    NexaBottomSheet(onDismiss = onDismiss) {
        SheetTitle(stringResource(R.string.space_add_challenge))
        NexaTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = stringResource(R.string.space_challenge_prompt_label),
            singleLine = false
        )
        Spacer(Modifier.height(NexaSpacing.md))
        TextButton(onClick = { if (prompt.isNotBlank()) onAdd(prompt) }) {
            Text(stringResource(R.string.action_save))
        }
    }
}
