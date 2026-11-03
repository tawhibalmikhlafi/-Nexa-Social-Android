package com.nexa.social.feature.gatherings

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.nexa.social.domain.model.Gathering
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaConfirmDialog
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun GatheringDetailScreen(
    gatheringId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: GatheringDetailViewModel = viewModel(
        factory = GatheringDetailViewModel.factory(gatheringId),
        key = gatheringId
    )
) {
    val gathering by viewModel.gathering.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    val error = failureMessage(failure)
    val item = gathering

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(
            title = stringResource(R.string.gatherings_detail_title),
            onBack = onBack,
            actions = {
                if (item != null && item.hostId == userId) {
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.gatherings_delete))
                    }
                }
            }
        )
        if (item == null) {
            Text(
                text = stringResource(R.string.gatherings_missing),
                modifier = Modifier.padding(NexaSpacing.lg),
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            Text(text = item.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.gatherings_hosted_by, item.hostName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            NexaCard {
                DetailRow(
                    icon = Icons.Filled.Schedule,
                    text = TimeUtils.dateTime(item.startsAt)
                )
                DetailRow(
                    icon = if (item.isVirtual) Icons.Filled.Videocam else Icons.Filled.LocationOn,
                    text = item.location
                )
                DetailRow(
                    icon = Icons.Filled.Groups,
                    text = if (item.capacity <= 0) {
                        stringResource(R.string.gatherings_attendees, item.attendeeIds.size)
                    } else {
                        stringResource(
                            R.string.gatherings_attendees_capacity,
                            item.attendeeIds.size,
                            item.capacity
                        )
                    }
                )
            }

            if (item.description.isNotBlank()) {
                Text(text = item.description, style = MaterialTheme.typography.bodyLarge)
            }

            if (item.tags.isNotEmpty()) {
                Text(
                    text = item.tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (error != null) {
                AccountRequiredBanner(text = error)
            }

            if (!item.isPast()) {
                val attending = item.isAttending(userId)
                NexaPrimaryButton(
                    text = when {
                        attending -> stringResource(R.string.gatherings_going)
                        item.isFull() -> stringResource(R.string.gatherings_full)
                        else -> stringResource(R.string.gatherings_rsvp)
                    },
                    enabled = attending || !item.isFull(),
                    onClick = viewModel::toggleAttendance
                )
            } else {
                Text(
                    text = stringResource(R.string.gatherings_ended),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LocalDataBanner(text = stringResource(R.string.gatherings_local_note))
            Spacer(Modifier.height(NexaSpacing.xxxl))
        }
    }

    if (showDelete) {
        NexaConfirmDialog(
            title = stringResource(R.string.gatherings_delete),
            message = stringResource(R.string.gatherings_delete_confirm),
            destructive = true,
            onConfirm = {
                showDelete = false
                viewModel.delete(onDeleted)
            },
            onDismiss = { showDelete = false }
        )
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier.padding(vertical = NexaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
