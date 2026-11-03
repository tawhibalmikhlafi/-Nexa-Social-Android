package com.nexa.social.feature.worlds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.core.ui.failureMessageRes
import com.nexa.social.domain.model.CommunityCategory
import com.nexa.social.domain.model.WorldPrivacy
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.NexaChoiceChipRow
import com.nexa.social.ui.components.NexaConfirmDialog
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.launch

@Composable
fun CreateWorldScreen(
    communityId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: CreateWorldViewModel = viewModel(factory = CreateWorldViewModel.factory(communityId))
) {
    val existing by viewModel.existing.collectAsStateWithLifecycle()
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CommunityCategory.OTHER) }
    var privacy by remember { mutableStateOf(WorldPrivacy.PUBLIC_LISTED) }
    var rulesText by remember { mutableStateOf("") }
    var prefilled by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(existing) {
        val current = existing
        if (!prefilled && current != null) {
            name = current.name
            description = current.description
            category = current.category
            privacy = current.privacy
            rulesText = current.rules.joinToString("\n")
            prefilled = true
        }
    }

    val savedMessage = stringResource(R.string.world_saved)
    val deletedMessage = stringResource(R.string.world_deleted)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NexaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
    ) {
        NexaTopBar(
            title = stringResource(if (viewModel.isEditing) R.string.world_edit_title else R.string.world_create_title),
            onBack = onBack
        )
        AccountRequiredBanner(text = stringResource(R.string.world_requires_account))
        if (viewModel.isEditing && viewModel.canEdit(existing)) {
            Text(
                text = stringResource(R.string.world_edit_local_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        NexaTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.world_name_label)
        )
        NexaTextField(
            value = description,
            onValueChange = { description = it },
            label = stringResource(R.string.world_description_label),
            singleLine = false
        )

        Text(text = stringResource(R.string.world_category_label), style = MaterialTheme.typography.titleMedium)
        NexaChoiceChipRow(
            options = CommunityCategory.entries,
            selected = category,
            labelOf = { categoryLabel(it) },
            onSelect = { category = it }
        )

        Text(text = stringResource(R.string.world_privacy_label), style = MaterialTheme.typography.titleMedium)
        NexaChoiceChipRow(
            options = WorldPrivacy.entries,
            selected = privacy,
            labelOf = { privacyLabel(it) },
            onSelect = { privacy = it }
        )

        NexaTextField(
            value = rulesText,
            onValueChange = { rulesText = it },
            label = stringResource(R.string.world_rules_label),
            singleLine = false
        )

        Spacer(Modifier.height(NexaSpacing.sm))
        NexaPrimaryButton(
            text = stringResource(R.string.action_save),
            enabled = name.trim().length >= 3,
            onClick = {
                val rules = rulesText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                viewModel.submit(name, description, category, privacy, rules) { reason, id ->
                    if (reason == null && id != null) {
                        snackbarHost?.let { host -> scope.launch { host.showSnackbar(savedMessage) } }
                        onSaved(id)
                    } else if (reason != null) {
                        val message = context.getString(failureMessageRes(reason))
                        snackbarHost?.let { host -> scope.launch { host.showSnackbar(message) } }
                    }
                }
            }
        )

        if (viewModel.isEditing && viewModel.canEdit(existing)) {
            NexaSecondaryButton(
                text = stringResource(R.string.action_delete),
                onClick = { showDelete = true }
            )
        }
        Spacer(Modifier.height(NexaSpacing.xxxl))
    }

    if (showDelete) {
        NexaConfirmDialog(
            title = stringResource(R.string.world_delete_title),
            message = stringResource(R.string.world_delete_confirm),
            destructive = true,
            onConfirm = {
                showDelete = false
                viewModel.delete {
                    snackbarHost?.let { host -> scope.launch { host.showSnackbar(deletedMessage) } }
                    onBack()
                }
            },
            onDismiss = { showDelete = false }
        )
    }
}

@Composable
private fun privacyLabel(privacy: WorldPrivacy): String = when (privacy) {
    WorldPrivacy.PUBLIC_LISTED -> stringResource(R.string.world_privacy_public)
    WorldPrivacy.PUBLIC_UNLISTED -> stringResource(R.string.world_privacy_unlisted)
    WorldPrivacy.PRIVATE -> stringResource(R.string.world_privacy_private)
}
