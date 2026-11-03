package com.nexa.social.feature.capsules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
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
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.domain.model.TimeCapsule
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaConfirmDialog
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun CapsulesScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    viewModel: CapsulesViewModel = viewModel(factory = CapsulesViewModel.Factory)
) {
    val capsules by viewModel.capsules.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle()
    val opened by viewModel.opened.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<TimeCapsule?>(null) }
    val error = failureMessage(failure)

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(
            title = stringResource(R.string.capsules_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = onCreate) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.capsules_create))
                }
            }
        )

        if (capsules.isEmpty()) {
            NexaEmptyState(
                icon = Icons.Filled.Schedule,
                title = stringResource(R.string.capsules_empty_title),
                body = stringResource(R.string.capsules_empty_body)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = NexaSpacing.lg,
                    end = NexaSpacing.lg,
                    bottom = NexaSpacing.xxxl
                ),
                verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
            ) {
                if (error != null) {
                    item { AccountRequiredBanner(text = error) }
                }
                item { LocalDataBanner(text = stringResource(R.string.capsules_encrypted_note)) }
                items(capsules, key = { it.id }) { capsule ->
                    CapsuleCard(
                        capsule = capsule,
                        canDelete = capsule.authorId == userId,
                        onOpen = { viewModel.open(capsule.id) },
                        onDelete = { pendingDelete = capsule }
                    )
                }
            }
        }
    }

    opened?.let { capsule ->
        AlertDialog(
            onDismissRequest = viewModel::dismissOpened,
            title = { Text(capsule.title) },
            text = {
                Column {
                    Text(
                        text = TimeUtils.date(capsule.unlockAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(NexaSpacing.sm))
                    Text(
                        text = capsule.message.ifBlank {
                            stringResource(R.string.capsules_corrupt)
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissOpened) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    pendingDelete?.let { capsule ->
        NexaConfirmDialog(
            title = stringResource(R.string.capsules_delete),
            message = stringResource(R.string.capsules_delete_confirm),
            destructive = true,
            onConfirm = {
                viewModel.delete(capsule.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun CapsuleCard(
    capsule: TimeCapsule,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val locked = capsule.isLocked()
    NexaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(NexaSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = capsule.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (locked) {
                        stringResource(R.string.capsules_unlock_in, capsule.daysRemaining())
                    } else {
                        stringResource(R.string.capsules_unlocked, TimeUtils.date(capsule.unlockAt))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.capsules_delete)
                    )
                }
            }
        }
        Spacer(Modifier.height(NexaSpacing.sm))
        NexaSecondaryButton(
            text = if (locked) {
                stringResource(R.string.capsules_locked)
            } else {
                stringResource(R.string.capsules_open)
            },
            enabled = !locked,
            onClick = onOpen
        )
    }
}
