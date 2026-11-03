package com.nexa.social.feature.gatherings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.domain.model.Gathering
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaChoiceChip
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun GatheringsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    viewModel: GatheringsViewModel = viewModel(factory = GatheringsViewModel.Factory)
) {
    val gatherings by viewModel.gatherings.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val filtered = gatherings.filter { if (tab == 0) !it.isPast() else it.isPast() }

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(
            title = stringResource(R.string.gatherings_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = onCreate) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.gatherings_create))
                }
            }
        )
        Column(modifier = Modifier.padding(horizontal = NexaSpacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                NexaChoiceChip(
                    label = stringResource(R.string.gatherings_upcoming),
                    selected = tab == 0,
                    onClick = { tab = 0 }
                )
                NexaChoiceChip(
                    label = stringResource(R.string.gatherings_past),
                    selected = tab == 1,
                    onClick = { tab = 1 }
                )
            }
            Spacer(Modifier.height(NexaSpacing.sm))
            if (userId == null) {
                AccountRequiredBanner(text = stringResource(R.string.gatherings_guest_error))
                Spacer(Modifier.height(NexaSpacing.sm))
            }
            failureMessage(failure)?.let {
                AccountRequiredBanner(text = it)
                Spacer(Modifier.height(NexaSpacing.sm))
            }
        }

        if (filtered.isEmpty()) {
            NexaEmptyState(
                icon = Icons.Filled.Groups,
                title = stringResource(R.string.gatherings_empty_title),
                body = stringResource(R.string.gatherings_empty_body)
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
                items(filtered, key = { it.id }) { gathering ->
                    GatheringCard(
                        gathering = gathering,
                        userId = userId,
                        onOpen = { onOpen(gathering.id) },
                        onRsvp = { viewModel.toggleAttendance(gathering.id) }
                    )
                }
                item {
                    LocalDataBanner(text = stringResource(R.string.gatherings_local_note))
                }
            }
        }
    }
}

@Composable
private fun GatheringCard(
    gathering: Gathering,
    userId: String?,
    onOpen: () -> Unit,
    onRsvp: () -> Unit
) {
    val attending = gathering.isAttending(userId)
    val full = gathering.isFull()
    val past = gathering.isPast()

    NexaCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (gathering.isVirtual) Icons.Filled.Videocam else Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(NexaSpacing.xs))
            Text(
                text = if (gathering.isVirtual) {
                    stringResource(R.string.gatherings_virtual)
                } else {
                    stringResource(R.string.gatherings_in_person)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = TimeUtils.dateTime(gathering.startsAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(NexaSpacing.sm))
        Text(text = gathering.title, style = MaterialTheme.typography.titleMedium)
        if (gathering.description.isNotBlank()) {
            Text(
                text = gathering.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(NexaSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = gathering.location,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (gathering.capacity <= 0) {
                    stringResource(R.string.gatherings_unlimited)
                } else {
                    stringResource(
                        R.string.gatherings_capacity,
                        gathering.attendeeIds.size,
                        gathering.capacity
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!past) {
            Spacer(Modifier.height(NexaSpacing.sm))
            NexaSecondaryButton(
                text = when {
                    attending -> stringResource(R.string.gatherings_going)
                    full -> stringResource(R.string.gatherings_full)
                    else -> stringResource(R.string.gatherings_rsvp)
                },
                enabled = attending || !full,
                onClick = onRsvp
            )
        }
    }
}
