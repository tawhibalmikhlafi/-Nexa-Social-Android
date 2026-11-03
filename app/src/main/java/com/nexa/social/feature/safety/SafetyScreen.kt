package com.nexa.social.feature.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun SafetyScreen(
    onBack: () -> Unit,
    viewModel: SafetyViewModel = viewModel(factory = SafetyViewModel.Factory)
) {
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val privacy by viewModel.privacy.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.safety_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
        ) {
            AccountRequiredBanner(text = stringResource(R.string.safety_local_notice))

            Text(text = stringResource(R.string.safety_privacy_title), style = MaterialTheme.typography.titleMedium)
            PrivacySwitch(
                label = stringResource(R.string.safety_private_account),
                checked = privacy.privateAccount,
                onChange = { value -> viewModel.updatePrivacy { it.copy(privateAccount = value) } }
            )
            PrivacySwitch(
                label = stringResource(R.string.safety_show_activity),
                checked = privacy.showActivityStatus,
                onChange = { value -> viewModel.updatePrivacy { it.copy(showActivityStatus = value) } }
            )
            PrivacySwitch(
                label = stringResource(R.string.safety_allow_messages),
                checked = privacy.allowMessagesFromEveryone,
                onChange = { value ->
                    viewModel.updatePrivacy {
                        it.copy(
                            messagesFrom = if (value) {
                                com.nexa.social.domain.model.MessagePolicy.EVERYONE
                            } else {
                                com.nexa.social.domain.model.MessagePolicy.NOBODY
                            }
                        )
                    }
                }
            )
            PrivacySwitch(
                label = stringResource(R.string.safety_show_search),
                checked = privacy.showInLocalSearch,
                onChange = { value -> viewModel.updatePrivacy { it.copy(showInSearch = value) } }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = NexaSpacing.sm))

            Text(text = stringResource(R.string.safety_blocked_title), style = MaterialTheme.typography.titleMedium)
            if (blocked.isEmpty()) {
                EmptyLine(stringResource(R.string.safety_blocked_empty))
            } else {
                blocked.forEach { user ->
                    ListLine(
                        icon = Icons.Filled.Block,
                        title = user.displayName,
                        subtitle = "@${user.username}",
                        onRemove = { viewModel.unblock(user.userId) }
                    )
                }
            }

            Text(text = stringResource(R.string.safety_muted_title), style = MaterialTheme.typography.titleMedium)
            if (muted.isEmpty()) {
                EmptyLine(stringResource(R.string.safety_muted_empty))
            } else {
                muted.forEach { user ->
                    ListLine(
                        icon = Icons.Filled.NotificationsOff,
                        title = user.displayName,
                        subtitle = "@${user.username}",
                        onRemove = { viewModel.unmute(user.userId) }
                    )
                }
            }

            Text(text = stringResource(R.string.safety_hidden_title), style = MaterialTheme.typography.titleMedium)
            if (hidden.isEmpty()) {
                EmptyLine(stringResource(R.string.safety_hidden_empty))
            } else {
                hidden.forEach { content ->
                    ListLine(
                        icon = Icons.Filled.VisibilityOff,
                        title = content.kind.name,
                        subtitle = content.contentId,
                        onRemove = { viewModel.unhide(content.contentId) }
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = NexaSpacing.sm))
        }
    }
}

@Composable
private fun PrivacySwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ListLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f).padding(start = NexaSpacing.md)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove))
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = NexaSpacing.xs)
    )
}
