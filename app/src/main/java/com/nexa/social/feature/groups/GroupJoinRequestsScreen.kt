package com.nexa.social.feature.groups

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.nexa.social.domain.model.JoinRequest
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun GroupJoinRequestsScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: GroupJoinRequestsViewModel = viewModel(
        factory = GroupJoinRequestsViewModel.factory(groupId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            NexaTopBar(
                title = stringResource(R.string.groups_requests_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.requests.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(NexaSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.error != null) {
                        Text(
                            text = when (state.error) {
                                "action_failed" -> stringResource(R.string.groups_action_failed)
                                else -> stringResource(R.string.groups_requests_failed)
                            },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(NexaSpacing.sm))
                    }
                    Text(
                        text = stringResource(R.string.groups_requests_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(NexaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm),
            ) {
                if (state.error != null) {
                    item {
                        Text(
                            text = stringResource(R.string.groups_action_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(state.requests, key = { it.id }) { request ->
                    JoinRequestCard(
                        request = request,
                        onOpenProfile = { onOpenProfile(request.userId) },
                        onApprove = { viewModel.approve(request.id) },
                        onReject = { viewModel.reject(request.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun JoinRequestCard(
    request: JoinRequest,
    onOpenProfile: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    NexaCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenProfile),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NexaAvatar(
                displayName = request.displayName.ifBlank { request.username },
                avatarUri = request.avatarUrl,
                size = 44.dp,
            )
            Spacer(Modifier.width(NexaSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = request.displayName.ifBlank { request.username },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${request.username} • ${TimeUtils.relativeFromNow(request.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val message = request.message
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = NexaSpacing.sm),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NexaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm),
        ) {
            Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.groups_accept))
            }
            OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.groups_decline))
            }
        }
    }
}
