package com.nexa.social.feature.settings

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.BuildConfig
import com.nexa.social.R
import com.nexa.social.core.common.LocaleManager
import com.nexa.social.core.ui.rememberRemoteMode
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.NotificationType
import com.nexa.social.domain.model.FeedType
import com.nexa.social.domain.model.isAccount
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.LegalDialog
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onSignedOut: () -> Unit,
    onSaved: () -> Unit = {},
    onClips: () -> Unit = {},
    onAchievements: () -> Unit = {},
    onSafety: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onAi: () -> Unit = {},
    onLabs: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onMessages: () -> Unit = {},
    onGroups: () -> Unit = {},
    onFriends: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val notificationCategories by viewModel.notificationCategories.collectAsStateWithLifecycle()
    val videoAutoplay by viewModel.videoAutoplay.collectAsStateWithLifecycle()
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val reduceMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val feedType by viewModel.feedType.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val remoteMode = rememberRemoteMode()

    var dialog by remember { mutableStateOf<String?>(null) }
    var showLanguage by remember { mutableStateOf(false) }
    var showFeedType by remember { mutableStateOf(false) }
    var showSignOut by remember { mutableStateOf(false) }
    var showClearCache by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
        ) {
            SettingsSectionHeader(stringResource(R.string.settings_section_account))
            SettingsRow(
                icon = Icons.Filled.AccountCircle,
                title = stringResource(R.string.settings_edit_profile),
                subtitle = stringResource(R.string.settings_edit_profile_summary),
                onClick = onEditProfile
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = NexaSpacing.xs))
            SettingsSectionHeader(stringResource(R.string.settings_section_preferences))
            SettingsRow(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.settings_appearance),
                subtitle = stringResource(R.string.settings_appearance_summary),
                onClick = onAppearance
            )
            SettingsRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_language),
                subtitle = languageLabel(language),
                onClick = { showLanguage = true }
            )
            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.settings_notifications_enabled),
                subtitle = stringResource(R.string.settings_notifications_device_summary),
                trailing = {
                    Switch(checked = notifications, onCheckedChange = viewModel::setNotificationsEnabled)
                }
            )

            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.settings_os_notifications),
                subtitle = stringResource(R.string.settings_os_notifications_summary),
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )

            if (notifications) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = NexaSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                ) {
                    NotificationCategoryRow(
                        title = stringResource(R.string.settings_notif_likes),
                        checked = notificationCategories[NotificationType.LIKE] != false,
                        onCheckedChange = { viewModel.setNotificationCategory(NotificationType.LIKE, it) }
                    )
                    NotificationCategoryRow(
                        title = stringResource(R.string.settings_notif_comments),
                        checked = notificationCategories[NotificationType.COMMENT] != false,
                        onCheckedChange = { viewModel.setNotificationCategory(NotificationType.COMMENT, it) }
                    )
                    NotificationCategoryRow(
                        title = stringResource(R.string.settings_notif_replies),
                        checked = notificationCategories[NotificationType.REPLY] != false,
                        onCheckedChange = { viewModel.setNotificationCategory(NotificationType.REPLY, it) }
                    )
                    NotificationCategoryRow(
                        title = stringResource(R.string.settings_notif_follows),
                        checked = notificationCategories[NotificationType.FOLLOW] != false,
                        onCheckedChange = { viewModel.setNotificationCategory(NotificationType.FOLLOW, it) }
                    )
                    NotificationCategoryRow(
                        title = stringResource(R.string.settings_notif_messages),
                        checked = notificationCategories[NotificationType.MESSAGE] != false,
                        onCheckedChange = { viewModel.setNotificationCategory(NotificationType.MESSAGE, it) }
                    )
                    NotificationCategoryRow(
                        title = stringResource(R.string.settings_notif_groups),
                        checked = notificationCategories[NotificationType.GROUP_JOIN_REQUEST] != false,
                        onCheckedChange = { viewModel.setNotificationCategory(NotificationType.GROUP_JOIN_REQUEST, it) }
                    )
                    NotificationCategoryRow(
                        title = stringResource(R.string.settings_notif_stories),
                        checked = notificationCategories[NotificationType.STORY_REPLY] != false,
                        onCheckedChange = { viewModel.setNotificationCategory(NotificationType.STORY_REPLY, it) }
                    )
                    NotificationCategoryRow(
                        title = stringResource(R.string.settings_notif_mentions),
                        checked = notificationCategories[NotificationType.MENTION] != false,
                        onCheckedChange = { viewModel.setNotificationCategory(NotificationType.MENTION, it) }
                    )
                    NotificationCategoryRow(
                        title = stringResource(R.string.settings_notif_system),
                        checked = notificationCategories[NotificationType.SYSTEM] != false,
                        onCheckedChange = { viewModel.setNotificationCategory(NotificationType.SYSTEM, it) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = NexaSpacing.xs))
            SettingsSectionHeader(stringResource(R.string.settings_section_media))
            SettingsRow(
                icon = Icons.Filled.Videocam,
                title = stringResource(R.string.settings_video_autoplay),
                subtitle = stringResource(R.string.settings_video_autoplay_summary),
                trailing = { Switch(checked = videoAutoplay, onCheckedChange = viewModel::setVideoAutoplay) }
            )
            SettingsRow(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.settings_data_saver),
                subtitle = stringResource(R.string.settings_data_saver_summary),
                trailing = { Switch(checked = dataSaver, onCheckedChange = viewModel::setDataSaver) }
            )
            SettingsRow(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.settings_reduce_motion),
                subtitle = stringResource(R.string.settings_reduce_motion_summary),
                trailing = { Switch(checked = reduceMotion, onCheckedChange = viewModel::setReduceMotion) }
            )

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = NexaSpacing.sm)) {
                Text(
                    text = stringResource(R.string.settings_font_size, (fontScale * 100).toInt()),
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = fontScale,
                    onValueChange = viewModel::setFontScale,
                    valueRange = 0.85f..1.30f,
                    steps = 8
                )
            }
            SettingsRow(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.settings_clear_cache),
                subtitle = stringResource(R.string.settings_cache_size, formatBytes(cacheSizeBytes)),
                onClick = { showClearCache = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = NexaSpacing.xs))
            SettingsSectionHeader(stringResource(R.string.settings_section_privacy))
            SettingsRow(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.privacy_title),
                subtitle = stringResource(R.string.privacy_section_account_desc),
                onClick = onPrivacy
            )
            SettingsRow(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.safety_title),
                onClick = onSafety
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = NexaSpacing.xs))
            SettingsSectionHeader(stringResource(R.string.settings_section_activity))
            SettingsRow(
                icon = Icons.Filled.FilterList,
                title = stringResource(R.string.settings_feed_preference),
                subtitle = feedTypeLabel(feedType),
                onClick = { showFeedType = true }
            )
            SettingsRow(
                icon = Icons.Filled.Bookmark,
                title = stringResource(R.string.saved_title),
                onClick = onSaved
            )
            SettingsRow(
                icon = Icons.Filled.Videocam,
                title = stringResource(R.string.clips_title),
                onClick = onClips
            )
            SettingsRow(
                icon = Icons.Filled.EmojiEvents,
                title = stringResource(R.string.achievements_title),
                onClick = onAchievements
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = NexaSpacing.xs))
            SettingsSectionHeader(stringResource(R.string.settings_section_connections))
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = stringResource(R.string.settings_messages),
                subtitle = stringResource(R.string.settings_messages_summary),
                onClick = onMessages
            )
            SettingsRow(
                icon = Icons.Filled.Groups,
                title = stringResource(R.string.settings_groups),
                subtitle = stringResource(R.string.settings_groups_summary),
                onClick = onGroups
            )
            SettingsRow(
                icon = Icons.Filled.People,
                title = stringResource(R.string.settings_friends),
                onClick = onFriends
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = NexaSpacing.xs))
            SettingsSectionHeader(stringResource(R.string.settings_section_tools))
            SettingsRow(
                icon = Icons.Filled.AutoAwesome,
                title = stringResource(R.string.ai_title),
                onClick = onAi
            )
            SettingsRow(
                icon = Icons.Filled.Bolt,
                title = stringResource(R.string.labs_title),
                subtitle = stringResource(R.string.labs_subtitle),
                onClick = onLabs
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = NexaSpacing.xs))
            SettingsSectionHeader(stringResource(R.string.settings_section_information))
            SettingsRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about),
                onClick = { dialog = "about" }
            )
            SettingsRow(
                icon = Icons.Filled.Policy,
                title = stringResource(R.string.settings_privacy_policy),
                onClick = { dialog = "privacy" }
            )
            SettingsRow(
                icon = Icons.Filled.Gavel,
                title = stringResource(R.string.settings_terms),
                onClick = { dialog = "terms" }
            )

            Spacer(Modifier.height(NexaSpacing.sm))
            Text(
                text = "${stringResource(R.string.settings_version)} ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (session.isAccount || session is SessionState.Guest) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = if (session is SessionState.Guest) {
                        stringResource(R.string.settings_sign_out_guest)
                    } else {
                        stringResource(R.string.settings_sign_out)
                    },
                    onClick = { showSignOut = true }
                )
            }
            if (remoteMode) {
                LocalDataBanner(text = stringResource(R.string.settings_remote_notice))
            } else {
                AccountRequiredBanner(text = stringResource(R.string.notice_server_pending_body))
            }
            Spacer(Modifier.height(NexaSpacing.xxxl))
        }
    }

    if (showFeedType) {
        AlertDialog(
            onDismissRequest = { showFeedType = false },
            title = { Text(stringResource(R.string.settings_feed_preference)) },
            text = {
                Column {
                    FeedTypeOption(stringResource(R.string.settings_feed_latest), feedType == FeedType.LATEST) {
                        viewModel.setFeedType(FeedType.LATEST); showFeedType = false
                    }
                    FeedTypeOption(stringResource(R.string.settings_feed_engaging), feedType == FeedType.ENGAGING) {
                        viewModel.setFeedType(FeedType.ENGAGING); showFeedType = false
                    }
                    FeedTypeOption(stringResource(R.string.settings_feed_following), feedType == FeedType.FOLLOWING) {
                        viewModel.setFeedType(FeedType.FOLLOWING); showFeedType = false
                    }
                    FeedTypeOption(stringResource(R.string.settings_feed_worlds), feedType == FeedType.WORLDS) {
                        viewModel.setFeedType(FeedType.WORLDS); showFeedType = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFeedType = false }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    LanguageOption(
                        label = stringResource(R.string.settings_language_system),
                        selected = language == LocaleManager.SYSTEM,
                        onSelect = {
                            viewModel.setLanguage(LocaleManager.SYSTEM)
                            showLanguage = false
                            (context as? Activity)?.recreate()
                        }
                    )
                    LanguageOption(
                        label = stringResource(R.string.settings_language_arabic),
                        selected = language == LocaleManager.ARABIC,
                        onSelect = {
                            viewModel.setLanguage(LocaleManager.ARABIC)
                            showLanguage = false
                            (context as? Activity)?.recreate()
                        }
                    )
                    LanguageOption(
                        label = stringResource(R.string.settings_language_english),
                        selected = language == LocaleManager.ENGLISH,
                        onSelect = {
                            viewModel.setLanguage(LocaleManager.ENGLISH)
                            showLanguage = false
                            (context as? Activity)?.recreate()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguage = false }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }

    if (showClearCache) {
        AlertDialog(
            onDismissRequest = { showClearCache = false },
            title = { Text(stringResource(R.string.settings_clear_cache)) },
            text = { Text(stringResource(R.string.settings_clear_cache_confirm, formatBytes(cacheSizeBytes))) },
            confirmButton = {
                TextButton(onClick = {
                    showClearCache = false
                    viewModel.clearCache { }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCache = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out)) },
            text = { Text(stringResource(R.string.settings_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.signOut()
                    showSignOut = false
                    onSignedOut()
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOut = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    dialog?.let { which ->
        when (which) {
            "about" -> LegalDialog(
                title = stringResource(R.string.settings_about),
                body = stringResource(R.string.settings_about_body),
                onDismiss = { dialog = null }
            )
            "privacy" -> LegalDialog(
                title = stringResource(R.string.settings_privacy_policy),
                body = stringResource(R.string.settings_privacy_policy_body),
                onDismiss = { dialog = null }
            )
            "terms" -> LegalDialog(
                title = stringResource(R.string.settings_terms),
                body = stringResource(R.string.settings_terms_body),
                onDismiss = { dialog = null }
            )
        }
    }
}


@Composable
private fun feedTypeLabel(type: FeedType): String = when (type) {
    FeedType.LATEST -> stringResource(R.string.settings_feed_latest)
    FeedType.ENGAGING -> stringResource(R.string.settings_feed_engaging)
    FeedType.FOLLOWING -> stringResource(R.string.settings_feed_following)
    FeedType.WORLDS -> stringResource(R.string.settings_feed_worlds)
}

@Composable
private fun FeedTypeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() }.padding(vertical = NexaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "${bytes} B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun NotificationCategoryRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NexaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NexaSpacing.md, bottom = NexaSpacing.xs)
    )
}

@Composable
private fun languageLabel(tag: String): String = when (tag) {
    LocaleManager.ARABIC -> stringResource(R.string.settings_language_arabic)
    LocaleManager.ENGLISH -> stringResource(R.string.settings_language_english)
    else -> stringResource(R.string.settings_language_system)
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = NexaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = NexaSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = NexaSpacing.md)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) trailing() else if (onClick != null) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
