package com.nexa.social.feature.settings.privacy

import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.domain.model.AudienceType
import com.nexa.social.domain.model.MessagePolicy
import com.nexa.social.domain.model.SessionDeviceKind
import com.nexa.social.domain.model.UserSession
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.NexaBottomSheet
import com.nexa.social.ui.components.SheetTitle
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.delay

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onBlockedAccounts: () -> Unit = {},
    onAccountDeleted: () -> Unit = {},
    viewModel: PrivacyViewModel = viewModel(factory = PrivacyViewModel.Factory),
    actionsViewModel: PrivacyActionsViewModel = viewModel(factory = PrivacyActionsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions by actionsViewModel.state.collectAsStateWithLifecycle()
    var pendingChoice by remember { mutableStateOf<PendingChoice?>(null) }
    var pendingAction by remember { mutableStateOf<SecurityAction?>(null) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showDevicesSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val payload = actions.exportPayload
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(payload.toByteArray())
                }
            }
        }
        actionsViewModel.consumeExport()
    }

    LaunchedEffect(actions.exportPayload) {
        val payload = actions.exportPayload ?: return@LaunchedEffect
        exportLauncher.launch("nexa-data-${System.currentTimeMillis()}.json")
        // payload stays until the launcher result consumes it
        if (payload.isEmpty()) actionsViewModel.consumeExport()
    }

    LaunchedEffect(actions.accountDeleted) {
        if (actions.accountDeleted) onAccountDeleted()
    }

    LaunchedEffect(state.error, state.successNonce) {
        if (state.error != null || state.successNonce != null) {
            delay(4_000)
            viewModel.clearTransientMessages()
        }
    }

    LaunchedEffect(actions.error, actions.passwordUpdated, actions.sessionsRevoked) {
        if (actions.error != null || actions.passwordUpdated || actions.sessionsRevoked) {
            delay(4_000)
            actionsViewModel.clearError()
            actionsViewModel.consumePasswordUpdated()
            actionsViewModel.consumeSessionsRevoked()
        }
    }

    Scaffold(
        topBar = { NexaTopBar(title = stringResource(R.string.privacy_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.xs),
        ) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(NexaSpacing.sm))
            }

            if (!state.isRemote) {
                AccountRequiredBanner(text = stringResource(R.string.privacy_requires_account))
                Spacer(Modifier.height(NexaSpacing.sm))
            }

            state.error?.let { ErrorBanner(it.toMessage(), onRetry = viewModel::retry) }
            state.successNonce?.let { SuccessBanner(stringResource(R.string.privacy_saved)) }
            actions.error?.let {
                ErrorBanner(actionErrorMessage(it), onRetry = { actionsViewModel.clearError() })
            }
            if (actions.passwordUpdated) {
                SuccessBanner(stringResource(R.string.privacy_password_updated))
            }
            if (actions.sessionsRevoked) {
                SuccessBanner(stringResource(R.string.privacy_sessions_revoked))
            }

            val locked = !state.isRemote

            // ── 1. Account ────────────────────────────────────────────────
            SectionHeader(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.privacy_section_account),
                subtitle = stringResource(R.string.privacy_section_account_desc),
            )
            SwitchItem(
                title = stringResource(R.string.privacy_private_account),
                subtitle = stringResource(R.string.privacy_private_account_desc),
                checked = state.settings.privateAccount,
                enabled = !locked,
                saving = "privateAccount" in state.savingKeys,
                onChange = {
                    viewModel.updateSettings("privateAccount") {
                        it.copy(privateAccount = !it.privateAccount)
                    }
                },
            )

            SectionDivider()

            // ── 2. Posts ──────────────────────────────────────────────────
            SectionHeader(
                icon = Icons.Filled.Visibility,
                title = stringResource(R.string.privacy_section_posts),
                subtitle = stringResource(R.string.privacy_section_posts_desc),
            )
            ChoiceItem(
                title = stringResource(R.string.privacy_default_post_audience),
                subtitle = stringResource(R.string.privacy_default_post_audience_desc),
                current = audienceLabel(state.settings.defaultPostAudience),
                enabled = !locked,
                saving = "defaultPostAudience" in state.savingKeys,
                onClick = {
                    pendingChoice = PendingChoice.Audience(
                        key = "defaultPostAudience",
                        current = state.settings.defaultPostAudience,
                    )
                },
            )

            SectionDivider()

            // ── 3. Stories ────────────────────────────────────────────────
            SectionHeader(
                icon = Icons.Filled.Visibility,
                title = stringResource(R.string.privacy_section_stories),
                subtitle = stringResource(R.string.privacy_section_stories_desc),
            )
            ChoiceItem(
                title = stringResource(R.string.privacy_story_audience),
                subtitle = stringResource(R.string.privacy_story_audience_desc),
                current = audienceLabel(state.settings.storyAudience),
                enabled = !locked,
                saving = "storyAudience" in state.savingKeys,
                onClick = {
                    pendingChoice = PendingChoice.Audience(
                        key = "storyAudience",
                        current = state.settings.storyAudience,
                    )
                },
            )
            ChoiceItem(
                title = stringResource(R.string.privacy_story_replies),
                subtitle = stringResource(R.string.privacy_story_replies_desc),
                current = audienceLabel(state.settings.storyRepliesFrom),
                enabled = !locked,
                saving = "storyRepliesFrom" in state.savingKeys,
                onClick = {
                    pendingChoice = PendingChoice.Audience(
                        key = "storyRepliesFrom",
                        current = state.settings.storyRepliesFrom,
                    )
                },
            )
            SwitchItem(
                title = stringResource(R.string.privacy_story_sharing),
                subtitle = stringResource(R.string.privacy_story_sharing_desc),
                checked = state.settings.storySharingEnabled,
                enabled = !locked,
                saving = "storySharingEnabled" in state.savingKeys,
                onChange = {
                    viewModel.updateSettings("storySharingEnabled") {
                        it.copy(storySharingEnabled = !it.storySharingEnabled)
                    }
                },
            )

            SectionDivider()

            // ── 4. Profile ────────────────────────────────────────────────
            SectionHeader(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.privacy_section_profile),
                subtitle = stringResource(R.string.privacy_section_profile_desc),
            )
            SwitchItem(
                title = stringResource(R.string.privacy_show_followers),
                subtitle = stringResource(R.string.privacy_show_followers_desc),
                checked = state.settings.showFollowersList,
                enabled = !locked,
                saving = "showFollowersList" in state.savingKeys,
                onChange = {
                    viewModel.updateSettings("showFollowersList") {
                        it.copy(showFollowersList = !it.showFollowersList)
                    }
                },
            )
            SwitchItem(
                title = stringResource(R.string.privacy_show_following),
                subtitle = stringResource(R.string.privacy_show_following_desc),
                checked = state.settings.showFollowingList,
                enabled = !locked,
                saving = "showFollowingList" in state.savingKeys,
                onChange = {
                    viewModel.updateSettings("showFollowingList") {
                        it.copy(showFollowingList = !it.showFollowingList)
                    }
                },
            )
            SwitchItem(
                title = stringResource(R.string.privacy_show_activity),
                subtitle = stringResource(R.string.privacy_show_activity_desc),
                checked = state.settings.showActivityStatus,
                enabled = !locked,
                saving = "showActivityStatus" in state.savingKeys,
                onChange = {
                    viewModel.updateSettings("showActivityStatus") {
                        it.copy(showActivityStatus = !it.showActivityStatus)
                    }
                },
            )

            SectionDivider()

            // ── 5. Messages ───────────────────────────────────────────────
            SectionHeader(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.privacy_section_messages),
                subtitle = stringResource(R.string.privacy_section_messages_desc),
            )
            ChoiceItem(
                title = stringResource(R.string.privacy_messages_from),
                subtitle = stringResource(R.string.privacy_messages_from_desc),
                current = messagePolicyLabel(state.settings.messagesFrom),
                enabled = !locked,
                saving = "messagesFrom" in state.savingKeys,
                onClick = {
                    pendingChoice = PendingChoice.MessageChoice(
                        key = "messagesFrom",
                        current = state.settings.messagesFrom,
                    )
                },
            )
            SwitchItem(
                title = stringResource(R.string.privacy_read_receipts),
                subtitle = stringResource(R.string.privacy_read_receipts_desc),
                checked = state.settings.showReadReceipts,
                enabled = !locked,
                saving = "showReadReceipts" in state.savingKeys,
                onChange = {
                    viewModel.updateSettings("showReadReceipts") {
                        it.copy(showReadReceipts = !it.showReadReceipts)
                    }
                },
            )
            SwitchItem(
                title = stringResource(R.string.privacy_hide_notification_content),
                subtitle = stringResource(R.string.privacy_hide_notification_content_desc),
                checked = state.settings.hideNotificationContent,
                enabled = !locked,
                saving = "hideNotificationContent" in state.savingKeys,
                onChange = {
                    viewModel.updateSettings("hideNotificationContent") {
                        it.copy(hideNotificationContent = !it.hideNotificationContent)
                    }
                },
            )

            SectionDivider()

            // ── 6. Interactions ───────────────────────────────────────────
            SectionHeader(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.privacy_section_interactions),
                subtitle = stringResource(R.string.privacy_section_interactions_desc),
            )
            ChoiceItem(
                title = stringResource(R.string.privacy_who_can_comment),
                subtitle = stringResource(R.string.privacy_who_can_comment_desc),
                current = audienceLabel(state.settings.whoCanComment),
                enabled = !locked,
                saving = "whoCanComment" in state.savingKeys,
                onClick = {
                    pendingChoice = PendingChoice.Audience(
                        key = "whoCanComment",
                        current = state.settings.whoCanComment,
                    )
                },
            )
            ChoiceItem(
                title = stringResource(R.string.privacy_who_can_react),
                subtitle = stringResource(R.string.privacy_who_can_react_desc),
                current = audienceLabel(state.settings.whoCanReact),
                enabled = !locked,
                saving = "whoCanReact" in state.savingKeys,
                onClick = {
                    pendingChoice = PendingChoice.Audience(
                        key = "whoCanReact",
                        current = state.settings.whoCanReact,
                    )
                },
            )
            ChoiceItem(
                title = stringResource(R.string.privacy_who_can_mention),
                subtitle = stringResource(R.string.privacy_who_can_mention_desc),
                current = audienceLabel(state.settings.whoCanMention),
                enabled = !locked,
                saving = "whoCanMention" in state.savingKeys,
                onClick = {
                    pendingChoice = PendingChoice.Audience(
                        key = "whoCanMention",
                        current = state.settings.whoCanMention,
                    )
                },
            )

            SectionDivider()

            // ── 7. Blocking & Restrictions ────────────────────────────────
            SectionHeader(
                icon = Icons.Filled.Block,
                title = stringResource(R.string.privacy_section_blocking),
                subtitle = stringResource(R.string.privacy_section_blocking_desc),
            )
            NavItem(
                title = stringResource(R.string.privacy_blocked_accounts),
                subtitle = stringResource(R.string.privacy_blocked_accounts_desc),
                onClick = onBlockedAccounts,
            )

            SectionDivider()

            // ── 8. Discovery ──────────────────────────────────────────────
            SectionHeader(
                icon = Icons.Filled.Search,
                title = stringResource(R.string.privacy_section_discovery),
                subtitle = stringResource(R.string.privacy_section_discovery_desc),
            )
            SwitchItem(
                title = stringResource(R.string.privacy_show_in_search),
                subtitle = stringResource(R.string.privacy_show_in_search_desc),
                checked = state.settings.showInSearch,
                enabled = !locked,
                saving = "showInSearch" in state.savingKeys,
                onChange = {
                    viewModel.updateSettings("showInSearch") {
                        it.copy(showInSearch = !it.showInSearch)
                    }
                },
            )
            SwitchItem(
                title = stringResource(R.string.privacy_allow_suggestions),
                subtitle = stringResource(R.string.privacy_allow_suggestions_desc),
                checked = state.settings.allowSuggestions,
                enabled = !locked,
                saving = "allowSuggestions" in state.savingKeys,
                onChange = {
                    viewModel.updateSettings("allowSuggestions") {
                        it.copy(allowSuggestions = !it.allowSuggestions)
                    }
                },
            )

            SectionDivider()

            // ── 9. Security ───────────────────────────────────────────────
            SectionHeader(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.privacy_section_security),
                subtitle = stringResource(R.string.privacy_section_security_desc),
            )
            NavItem(
                title = stringResource(R.string.privacy_change_password),
                subtitle = stringResource(R.string.privacy_change_password_desc),
                enabled = state.isRemote && !actions.busy,
                onClick = {
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                    pendingAction = SecurityAction.CHANGE_PASSWORD
                },
            )
            NavItem(
                title = stringResource(R.string.privacy_manage_sessions),
                subtitle = stringResource(R.string.privacy_manage_sessions_desc),
                enabled = state.isRemote && !actions.busy,
                onClick = { showDevicesSheet = true },
            )
            NavItem(
                title = stringResource(R.string.privacy_download_data),
                subtitle = stringResource(R.string.privacy_download_data_desc),
                enabled = state.isRemote && !actions.busy,
                onClick = { actionsViewModel.exportData() },
            )
            NavItem(
                title = stringResource(R.string.privacy_delete_account),
                subtitle = stringResource(R.string.privacy_delete_account_desc),
                enabled = state.isRemote && !actions.busy,
                onClick = { pendingAction = SecurityAction.DELETE_ACCOUNT },
            )

            Spacer(Modifier.height(NexaSpacing.xxxl))
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────
    (pendingChoice as? PendingChoice.Audience)?.let { choice ->
        val options = listOf(
            AudienceType.EVERYONE to stringResource(R.string.privacy_audience_everyone),
            AudienceType.FOLLOWERS to stringResource(R.string.privacy_audience_followers),
            AudienceType.MUTUALS to stringResource(R.string.privacy_audience_mutuals),
            AudienceType.ONLY_ME to stringResource(R.string.privacy_audience_only_me),
        )
        ChoiceDialog(
            title = audienceLabel(choice.current),
            current = choice.current,
            options = options,
            onDismiss = { pendingChoice = null },
            onSelect = { value ->
                viewModel.updateSettings(choice.key) { s ->
                    when (choice.key) {
                        "defaultPostAudience" -> s.copy(defaultPostAudience = value)
                        "storyAudience" -> s.copy(storyAudience = value)
                        "storyRepliesFrom" -> s.copy(storyRepliesFrom = value)
                        "whoCanComment" -> s.copy(whoCanComment = value)
                        "whoCanReact" -> s.copy(whoCanReact = value)
                        "whoCanMention" -> s.copy(whoCanMention = value)
                        else -> s
                    }
                }
                pendingChoice = null
            },
        )
    }

    (pendingChoice as? PendingChoice.MessageChoice)?.let { choice ->
        val options = listOf(
            MessagePolicy.EVERYONE to stringResource(R.string.privacy_audience_everyone),
            MessagePolicy.FOLLOWERS to stringResource(R.string.privacy_audience_followers),
            MessagePolicy.MUTUALS to stringResource(R.string.privacy_audience_mutuals),
            MessagePolicy.NOBODY to stringResource(R.string.privacy_audience_nobody),
        )
        ChoiceDialog(
            title = stringResource(R.string.privacy_messages_from),
            current = choice.current,
            options = options,
            onDismiss = { pendingChoice = null },
            onSelect = { value ->
                viewModel.updateSettings("messagesFrom") { it.copy(messagesFrom = value) }
                pendingChoice = null
            },
        )
    }

    LaunchedEffect(showDevicesSheet) {
        if (showDevicesSheet) {
            actionsViewModel.loadSessions()
        }
    }

    if (showDevicesSheet) {
        NexaBottomSheet(onDismiss = { showDevicesSheet = false }) {
            SheetTitle(stringResource(R.string.sessions_title))
            Spacer(Modifier.height(NexaSpacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.sessions_count, actions.sessions.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    enabled = !actions.loadingSessions,
                    onClick = { actionsViewModel.loadSessions() },
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(NexaSpacing.xs))
                    Text(stringResource(R.string.sessions_refresh))
                }
            }

            Spacer(Modifier.height(NexaSpacing.sm))

            actions.error?.let { reason ->
                ErrorBanner(
                    message = actionErrorMessage(reason),
                    onRetry = { actionsViewModel.loadSessions() },
                )
                Spacer(Modifier.height(NexaSpacing.sm))
            }

            val currentSession = actions.sessions.firstOrNull { it.isCurrent }
            val otherSessions = actions.sessions.filterNot { it.isCurrent }

            when {
                (actions.loadingSessions || !actions.sessionsLoaded) && actions.sessions.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(NexaSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }

                actions.sessions.isEmpty() && actions.error == null && actions.sessionsLoaded -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(NexaSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.sessions_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                actions.sessions.isNotEmpty() -> {
                    currentSession?.let { session ->
                        SessionCard(
                            session = session,
                            revoking = actions.revokingSessionId == session.id,
                            onRevoke = null,
                        )
                        Spacer(Modifier.height(NexaSpacing.sm))
                    }

                    if (otherSessions.isNotEmpty()) {
                        Text(
                            text = if (currentSession != null) {
                                stringResource(R.string.sessions_other)
                            } else {
                                stringResource(R.string.sessions_all)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(
                                top = NexaSpacing.xs,
                                bottom = NexaSpacing.xs,
                            ),
                        )
                        otherSessions.forEach { session ->
                            SessionCard(
                                session = session,
                                revoking = actions.revokingSessionId == session.id,
                                onRevoke = { actionsViewModel.revokeSession(session.id) },
                            )
                            Spacer(Modifier.height(NexaSpacing.sm))
                        }
                    }
                }
            }

            Spacer(Modifier.height(NexaSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md)) {
                NexaSecondaryButton(
                    text = stringResource(R.string.sessions_close),
                    onClick = { showDevicesSheet = false },
                    modifier = Modifier.weight(1f),
                )
                NexaPrimaryButton(
                    text = stringResource(R.string.sessions_sign_out_others),
                    onClick = { actionsViewModel.revokeOtherSessions() },
                    modifier = Modifier.weight(1f),
                    enabled = otherSessions.isNotEmpty() && !actions.busy,
                    loading = actions.busy,
                )
            }
        }
    }

    when (pendingAction) {
        SecurityAction.CHANGE_PASSWORD -> AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.privacy_change_password_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text(stringResource(R.string.privacy_current_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(NexaSpacing.sm))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.privacy_new_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(NexaSpacing.sm))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(stringResource(R.string.privacy_confirm_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (newPassword.isNotEmpty() && newPassword.length < 8) {
                        Spacer(Modifier.height(NexaSpacing.xs))
                        Text(
                            stringResource(R.string.privacy_password_too_short),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (confirmPassword.isNotEmpty() && confirmPassword != newPassword) {
                        Spacer(Modifier.height(NexaSpacing.xs))
                        Text(
                            stringResource(R.string.privacy_password_mismatch),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                val valid = currentPassword.isNotBlank() &&
                    newPassword.length >= 8 && newPassword == confirmPassword
                TextButton(
                    enabled = valid && !actions.busy,
                    onClick = {
                        actionsViewModel.changePassword(currentPassword, newPassword)
                        pendingAction = null
                    },
                ) { Text(stringResource(R.string.privacy_action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

        SecurityAction.REVOKE_SESSIONS -> AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.privacy_manage_sessions_title)) },
            text = { Text(stringResource(R.string.privacy_manage_sessions_warning)) },
            confirmButton = {
                TextButton(
                    enabled = !actions.busy,
                    onClick = {
                        actionsViewModel.revokeOtherSessions()
                        pendingAction = null
                    },
                ) { Text(stringResource(R.string.privacy_action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

        SecurityAction.DELETE_ACCOUNT -> AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.privacy_delete_account_title)) },
            text = { Text(stringResource(R.string.privacy_delete_account_warning)) },
            confirmButton = {
                TextButton(
                    enabled = !actions.busy,
                    onClick = {
                        actionsViewModel.deleteAccount()
                        pendingAction = null
                    },
                ) {
                    Text(
                        stringResource(R.string.privacy_action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

        null -> Unit
    }
}

private enum class SecurityAction {
    CHANGE_PASSWORD,
    REVOKE_SESSIONS,
    DELETE_ACCOUNT,
}

@Composable
private fun actionErrorMessage(reason: com.nexa.social.core.common.FailureReason): String =
    when (reason) {
        com.nexa.social.core.common.FailureReason.NETWORK ->
            stringResource(R.string.privacy_error_network)
        com.nexa.social.core.common.FailureReason.NOT_AUTHENTICATED ->
            stringResource(R.string.privacy_error_session)
        com.nexa.social.core.common.FailureReason.PERMISSION_DENIED ->
            stringResource(R.string.privacy_error_permission)
        com.nexa.social.core.common.FailureReason.INVALID_CREDENTIALS ->
            stringResource(R.string.privacy_error_current_password)
        else -> stringResource(R.string.privacy_error_generic)
    }

@Composable
private fun SessionCard(
    session: UserSession,
    revoking: Boolean,
    onRevoke: (() -> Unit)?,
) {
    NexaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = sessionIcon(session.deviceKind),
                contentDescription = null,
                tint = if (session.isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(NexaSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.deviceName,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (session.isCurrent) {
                        Spacer(Modifier.width(NexaSpacing.xs))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = stringResource(R.string.sessions_this_device),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(
                                    horizontal = NexaSpacing.sm,
                                    vertical = NexaSpacing.xxs,
                                ),
                            )
                        }
                    }
                }
                session.createdAt?.let {
                    SessionMetaLine(
                        stringResource(R.string.sessions_created, formatSessionTime(it)),
                    )
                }
                session.lastActiveAt?.let { at ->
                    SessionMetaLine(
                        stringResource(R.string.sessions_last_active, relativeSessionTime(at)),
                    )
                }
                SessionMetaLine(
                    when {
                        session.isExpired -> stringResource(R.string.sessions_expired)
                        session.expiresAt != null -> stringResource(
                            R.string.sessions_expires,
                            formatSessionTime(session.expiresAt),
                        )
                        else -> stringResource(R.string.sessions_no_expiry)
                    },
                )
                session.ipAddress?.let { ip ->
                    SessionMetaLine(stringResource(R.string.sessions_ip, ip))
                }
            }
            if (onRevoke != null) {
                if (revoking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    TextButton(onClick = onRevoke, enabled = !revoking) {
                        Text(
                            text = stringResource(R.string.sessions_sign_out),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionMetaLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun sessionIcon(kind: SessionDeviceKind): ImageVector = when (kind) {
    SessionDeviceKind.ANDROID_APP -> Icons.Filled.PhoneAndroid
    SessionDeviceKind.CHROME -> Icons.Filled.Language
    SessionDeviceKind.BROWSER -> Icons.Filled.Computer
    SessionDeviceKind.OTHER -> Icons.Filled.Devices
}

private fun formatSessionTime(millis: Long?): String {
    if (millis == null || millis <= 0L) return "—"
    return java.text.SimpleDateFormat(
        "yyyy/MM/dd HH:mm",
        java.util.Locale.getDefault(),
    ).format(java.util.Date(millis))
}

private fun relativeSessionTime(millis: Long?): String {
    if (millis == null || millis <= 0L) return "—"
    val minutes = (System.currentTimeMillis() - millis) / 60_000L
    return when {
        minutes < 1 -> "منذ قليل"
        minutes < 60 -> "منذ $minutes دقيقة"
        minutes < 1_440 -> "منذ ${minutes / 60} ساعة"
        else -> "منذ ${minutes / 1_440} يوم"
    }
}

// ── Internal state types ───────────────────────────────────────────────
private sealed interface PendingChoice {
    data class Audience(
        val key: String,
        val current: AudienceType,
    ) : PendingChoice

    data class MessageChoice(
        val key: String,
        val current: MessagePolicy,
    ) : PendingChoice
}

// ── Reusable composables ───────────────────────────────────────────────
@Composable
private fun SectionHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.padding(top = NexaSpacing.md, bottom = NexaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.padding(start = NexaSpacing.sm)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = NexaSpacing.sm))
}

@Composable
private fun SwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    saving: Boolean,
    onChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .padding(start = NexaSpacing.sm)
                .size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Switch(checked = checked, enabled = enabled, onCheckedChange = { onChange() })
            }
        }
    }
}

@Composable
private fun ChoiceItem(
    title: String,
    subtitle: String,
    current: String,
    enabled: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !saving, onClick = onClick)
            .padding(vertical = NexaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (saving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = current,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = NexaSpacing.sm),
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NavItem(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = NexaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    current: T,
    options: List<Pair<T, String>>,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = NexaSpacing.xs)
                            .semantics { role = Role.RadioButton },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == current, onClick = null)
                        Text(label, modifier = Modifier.padding(start = NexaSpacing.sm))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(NexaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = NexaSpacing.sm),
            )
            TextButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun SuccessBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(NexaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = NexaSpacing.sm),
            )
        }
    }
}

@Composable
private fun audienceLabel(value: AudienceType): String = when (value) {
    AudienceType.EVERYONE -> stringResource(R.string.privacy_audience_everyone)
    AudienceType.FOLLOWERS -> stringResource(R.string.privacy_audience_followers)
    AudienceType.MUTUALS -> stringResource(R.string.privacy_audience_mutuals)
    AudienceType.ONLY_ME -> stringResource(R.string.privacy_audience_only_me)
}

@Composable
private fun messagePolicyLabel(value: MessagePolicy): String = when (value) {
    MessagePolicy.EVERYONE -> stringResource(R.string.privacy_audience_everyone)
    MessagePolicy.FOLLOWERS -> stringResource(R.string.privacy_audience_followers)
    MessagePolicy.MUTUALS -> stringResource(R.string.privacy_audience_mutuals)
    MessagePolicy.NOBODY -> stringResource(R.string.privacy_audience_nobody)
}

@Composable
private fun PrivacyError.toMessage(): String = when (this) {
    is PrivacyError.Network -> stringResource(R.string.privacy_error_network)
    is PrivacyError.SessionExpired -> stringResource(R.string.privacy_error_session)
    is PrivacyError.Permission -> stringResource(R.string.privacy_error_permission)
    is PrivacyError.Generic -> stringResource(R.string.privacy_error_generic)
}
