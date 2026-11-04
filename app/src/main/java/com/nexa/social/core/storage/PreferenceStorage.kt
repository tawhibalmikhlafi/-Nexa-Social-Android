package com.nexa.social.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.domain.model.BackgroundStyle
import com.nexa.social.domain.model.FeedType
import com.nexa.social.domain.model.NexaAccent
import com.nexa.social.domain.model.MessagePolicy
import com.nexa.social.domain.model.NotificationType
import com.nexa.social.domain.model.PrivacySettings
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.ThemeConfig
import com.nexa.social.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.nexaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nexa_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Single source for lightweight user preferences persisted with DataStore.
 * Structured content (posts, communities, messages) lives in separate JSON stores.
 */
class PreferenceStorage(private val context: Context) {

    /**
     * Single resilient read source for every preference flow. If the on-disk preferences are
     * corrupt, or a key was persisted with an incompatible type by an older build, readers fall
     * back to defaults instead of propagating the exception into the UI collectors at startup.
     * Writes still go through [context.nexaDataStore], so locally stored accounts and data are kept.
     */
    private val preferences: Flow<Preferences> =
        context.nexaDataStore.data.catch { emit(emptyPreferences()) }

    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val themeMode = stringPreferencesKey("theme_mode")
        val themeAccent = stringPreferencesKey("theme_accent")
        val themeCustomAccent = longPreferencesKey("theme_custom_accent")
        val themeBackground = stringPreferencesKey("theme_background")
        val themeDynamic = booleanPreferencesKey("theme_dynamic")
        val sessionType = stringPreferencesKey("session_type")
        val sessionUserId = stringPreferencesKey("session_user_id")
        val sessionBackend = stringPreferencesKey("session_backend")
        val pendingVerificationEmail = stringPreferencesKey("pending_verification_email")
        val hiddenPostIds = stringSetPreferencesKey("hidden_post_ids")
        val pinnedPostIds = stringSetPreferencesKey("pinned_post_ids")
        val reactionOverridesJson = stringPreferencesKey("reaction_overrides_json")
        val viewedStoryIds = stringSetPreferencesKey("viewed_story_ids")
        val pinnedConversationIds = stringSetPreferencesKey("pinned_conversation_ids")
        val mutedConversationIds = stringSetPreferencesKey("muted_conversation_ids")
        val archivedConversationIds = stringSetPreferencesKey("archived_conversation_ids")
        val rememberMe = booleanPreferencesKey("remember_me")
        val language = stringPreferencesKey("language")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val privateAccount = booleanPreferencesKey("private_account")
        val showActivityStatus = booleanPreferencesKey("show_activity_status")
        val allowMessagesEveryone = booleanPreferencesKey("allow_messages_everyone")
        val showInLocalSearch = booleanPreferencesKey("show_in_local_search")
        val privacySettingsJson = stringPreferencesKey("privacy_settings_json")
        val followingIds = stringSetPreferencesKey("following_ids")
        val feedType = stringPreferencesKey("feed_type")
        val videoAutoplay = booleanPreferencesKey("video_autoplay")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val fontScale = floatPreferencesKey("font_scale")
        val dataSaver = booleanPreferencesKey("data_saver")
        val notifLike = booleanPreferencesKey("notif_like")
        val notifComment = booleanPreferencesKey("notif_comment")
        val notifReply = booleanPreferencesKey("notif_reply")
        val notifFollow = booleanPreferencesKey("notif_follow")
        val notifCommunity = booleanPreferencesKey("notif_community")
        val notifSpace = booleanPreferencesKey("notif_space")
        val notifMessages = booleanPreferencesKey("notif_messages")
        val notifGroups = booleanPreferencesKey("notif_groups")
        val notifStories = booleanPreferencesKey("notif_stories")
        val notifMentions = booleanPreferencesKey("notif_mentions")
        val notifSystem = booleanPreferencesKey("notif_system")
    }

    val onboardingCompleted: Flow<Boolean> =
        preferences.map { it[Keys.onboardingCompleted] ?: false }

    val themeConfig: Flow<ThemeConfig> = preferences.map { prefs ->
        ThemeConfig(
            mode = enumOrDefault(prefs[Keys.themeMode], ThemeMode.DARK),
            accent = enumOrDefault(prefs[Keys.themeAccent], NexaAccent.PURPLE),
            customAccentArgb = prefs[Keys.themeCustomAccent] ?: 0xFF7C4DFF,
            background = enumOrDefault(prefs[Keys.themeBackground], BackgroundStyle.DEEP_SPACE),
            useDynamicColor = prefs[Keys.themeDynamic] ?: false
        )
    }

    val session: Flow<SessionState> = preferences.map { prefs ->
        when (prefs[Keys.sessionType]) {
            SessionType.GUEST -> SessionState.Guest
            SessionType.ACCOUNT -> {
                val id = prefs[Keys.sessionUserId]
                if (id.isNullOrBlank()) {
                    SessionState.None
                } else if (prefs[Keys.sessionBackend] == SessionBackend.SUPABASE) {
                    SessionState.RemoteAccount(id)
                } else if (prefs[Keys.sessionBackend] == SessionBackend.LEGACY_REMOTE) {
                    // Supabase Auth is the only remote authentication source. Force the migration
                    // path to re-authenticate with Supabase instead of opening a broken session.
                    SessionState.None
                } else {
                    SessionState.LocalAccount(id)
                }
            }
            else -> SessionState.None
        }
    }

    val sessionBackend: Flow<String> = preferences.map { prefs ->
        prefs[Keys.sessionBackend] ?: SessionBackend.NONE
    }

    val pendingVerificationEmail: Flow<String?> = preferences.map { prefs ->
        prefs[Keys.pendingVerificationEmail]
    }

    val hiddenPostIds: Flow<Set<String>> =
        preferences.map { it[Keys.hiddenPostIds] ?: emptySet() }

    val pinnedPostIds: Flow<Set<String>> =
        preferences.map { it[Keys.pinnedPostIds] ?: emptySet() }

    /** Optimistic reaction state ("" means the user removed their reaction). */
    val reactionOverrides: Flow<Map<String, String>> =
        preferences.map { decodeReactionOverrides(it[Keys.reactionOverridesJson]) }

    val viewedStoryIds: Flow<Set<String>> =
        preferences.map { it[Keys.viewedStoryIds] ?: emptySet() }

    val pinnedConversationIds: Flow<Set<String>> =
        preferences.map { it[Keys.pinnedConversationIds] ?: emptySet() }

    val mutedConversationIds: Flow<Set<String>> =
        preferences.map { it[Keys.mutedConversationIds] ?: emptySet() }

    val archivedConversationIds: Flow<Set<String>> =
        preferences.map { it[Keys.archivedConversationIds] ?: emptySet() }

    val rememberMe: Flow<Boolean> = preferences.map { it[Keys.rememberMe] ?: true }

    val language: Flow<String> = preferences.map { it[Keys.language] ?: LanguageTag.SYSTEM }

    val notificationsEnabled: Flow<Boolean> =
        preferences.map { it[Keys.notificationsEnabled] ?: true }

    val privacySettings: Flow<PrivacySettings> = preferences.map { prefs ->
        val cached = prefs[Keys.privacySettingsJson]?.let { decodePrivacySettings(it) }
        cached ?: PrivacySettings(
            privateAccount = prefs[Keys.privateAccount] ?: false,
            showActivityStatus = prefs[Keys.showActivityStatus] ?: true,
            messagesFrom = if (prefs[Keys.allowMessagesEveryone] == false) {
                MessagePolicy.NOBODY
            } else {
                MessagePolicy.EVERYONE
            },
            showInSearch = prefs[Keys.showInLocalSearch] ?: true
        )
    }

    val followingIds: Flow<Set<String>> =
        preferences.map { it[Keys.followingIds] ?: emptySet() }

    val feedType: Flow<FeedType> =
        preferences.map { enumOrDefault(it[Keys.feedType], FeedType.LATEST) }

    val videoAutoplay: Flow<Boolean> = preferences.map { it[Keys.videoAutoplay] ?: true }

    val reduceMotion: Flow<Boolean> = preferences.map { it[Keys.reduceMotion] ?: false }

    val fontScale: Flow<Float> = preferences.map { (it[Keys.fontScale] ?: 1.0f).coerceIn(0.85f, 1.30f) }

    val dataSaver: Flow<Boolean> = preferences.map { it[Keys.dataSaver] ?: false }

    val notificationTypeEnabled: Flow<Map<NotificationType, Boolean>> =
        preferences.map { prefs ->
            mapOf(
                NotificationType.LIKE to (prefs[Keys.notifLike] ?: true),
                NotificationType.COMMENT_LIKE to (prefs[Keys.notifLike] ?: true),
                NotificationType.COMMENT to (prefs[Keys.notifComment] ?: true),
                NotificationType.REPLY to (prefs[Keys.notifReply] ?: true),
                NotificationType.FOLLOW to (prefs[Keys.notifFollow] ?: true),
                NotificationType.COMMUNITY to (prefs[Keys.notifCommunity] ?: true),
                NotificationType.SPACE to (prefs[Keys.notifSpace] ?: true),
                NotificationType.MESSAGE to (prefs[Keys.notifMessages] ?: true),
                NotificationType.GROUP_JOIN_REQUEST to (prefs[Keys.notifGroups] ?: true),
                NotificationType.GROUP_JOIN_APPROVED to (prefs[Keys.notifGroups] ?: true),
                NotificationType.GROUP_JOIN_REJECTED to (prefs[Keys.notifGroups] ?: true),
                NotificationType.STORY_REPLY to (prefs[Keys.notifStories] ?: true),
                NotificationType.MENTION to (prefs[Keys.notifMentions] ?: true),
                NotificationType.WELCOME to (prefs[Keys.notifSystem] ?: true),
                NotificationType.SYSTEM to (prefs[Keys.notifSystem] ?: true)
            )
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.nexaDataStore.edit { it[Keys.onboardingCompleted] = completed }
    }

    suspend fun saveTheme(config: ThemeConfig) {
        context.nexaDataStore.edit {
            it[Keys.themeMode] = config.mode.name
            it[Keys.themeAccent] = config.accent.name
            it[Keys.themeCustomAccent] = config.customAccentArgb
            it[Keys.themeBackground] = config.background.name
            it[Keys.themeDynamic] = config.useDynamicColor
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.nexaDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setSession(state: SessionState) {
        context.nexaDataStore.edit { prefs ->
            when (state) {
                is SessionState.Guest -> {
                    prefs[Keys.sessionType] = SessionType.GUEST
                    prefs.remove(Keys.sessionUserId)
                    prefs.remove(Keys.sessionBackend)
                }
                is SessionState.LocalAccount -> {
                    prefs[Keys.sessionType] = SessionType.ACCOUNT
                    prefs[Keys.sessionUserId] = state.userId
                    prefs[Keys.sessionBackend] = SessionBackend.LOCAL
                }
                is SessionState.RemoteAccount -> {
                    prefs[Keys.sessionType] = SessionType.ACCOUNT
                    prefs[Keys.sessionUserId] = state.userId
                    prefs[Keys.sessionBackend] = SessionBackend.SUPABASE
                }
                SessionState.None, SessionState.Loading -> {
                    prefs.remove(Keys.sessionType)
                    prefs.remove(Keys.sessionUserId)
                    prefs.remove(Keys.sessionBackend)
                }
            }
        }
    }

    suspend fun setRememberMe(remember: Boolean) {
        context.nexaDataStore.edit { it[Keys.rememberMe] = remember }
    }

    suspend fun setPendingVerificationEmail(email: String) {
        context.nexaDataStore.edit { it[Keys.pendingVerificationEmail] = email.trim() }
    }

    suspend fun clearPendingVerificationEmail() {
        context.nexaDataStore.edit { it.remove(Keys.pendingVerificationEmail) }
    }

    suspend fun setLanguage(tag: String) {
        context.nexaDataStore.edit { it[Keys.language] = tag }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.nexaDataStore.edit { it[Keys.notificationsEnabled] = enabled }
    }

    suspend fun setPrivacySettings(settings: PrivacySettings) {
        context.nexaDataStore.edit {
            it[Keys.privateAccount] = settings.privateAccount
            it[Keys.showActivityStatus] = settings.showActivityStatus
            it[Keys.allowMessagesEveryone] = settings.messagesFrom == MessagePolicy.EVERYONE
            it[Keys.showInLocalSearch] = settings.showInSearch
            it[Keys.privacySettingsJson] = encodePrivacySettings(settings)
        }
    }

    suspend fun setFollowingIds(ids: Set<String>) {
        context.nexaDataStore.edit { it[Keys.followingIds] = ids }
    }

    suspend fun setHiddenPostIds(ids: Set<String>) {
        context.nexaDataStore.edit { it[Keys.hiddenPostIds] = ids }
    }

    suspend fun setPinnedPostIds(ids: Set<String>) {
        context.nexaDataStore.edit { it[Keys.pinnedPostIds] = ids }
    }

    suspend fun setViewedStoryIds(ids: Set<String>) {
        context.nexaDataStore.edit { it[Keys.viewedStoryIds] = ids }
    }

    suspend fun setPinnedConversationIds(ids: Set<String>) {
        context.nexaDataStore.edit { it[Keys.pinnedConversationIds] = ids }
    }

    suspend fun setMutedConversationIds(ids: Set<String>) {
        context.nexaDataStore.edit { it[Keys.mutedConversationIds] = ids }
    }

    suspend fun setArchivedConversationIds(ids: Set<String>) {
        context.nexaDataStore.edit { it[Keys.archivedConversationIds] = ids }
    }

    suspend fun setFeedType(type: FeedType) {
        context.nexaDataStore.edit { it[Keys.feedType] = type.name }
    }

    suspend fun setVideoAutoplay(enabled: Boolean) {
        context.nexaDataStore.edit { it[Keys.videoAutoplay] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.nexaDataStore.edit { it[Keys.reduceMotion] = enabled }
    }

    suspend fun setFontScale(scale: Float) {
        context.nexaDataStore.edit { it[Keys.fontScale] = scale.coerceIn(0.85f, 1.30f) }
    }

    suspend fun setDataSaver(enabled: Boolean) {
        context.nexaDataStore.edit { it[Keys.dataSaver] = enabled }
    }

    suspend fun setReactionOverride(postId: String, emoji: String?) {
        context.nexaDataStore.edit { prefs ->
            val current = decodeReactionOverrides(prefs[Keys.reactionOverridesJson]).toMutableMap()
            current[postId] = emoji.orEmpty()
            prefs[Keys.reactionOverridesJson] = encodeReactionOverrides(current)
        }
    }

    suspend fun applyNotificationPreferences(
        notificationsEnabled: Boolean,
        categories: Map<NotificationType, Boolean>
    ) {
        context.nexaDataStore.edit { prefs ->
            prefs[Keys.notificationsEnabled] = notificationsEnabled
            categories.forEach { (type, enabled) ->
                val key = when (type) {
                    NotificationType.LIKE, NotificationType.COMMENT_LIKE -> Keys.notifLike
                    NotificationType.COMMENT -> Keys.notifComment
                    NotificationType.REPLY -> Keys.notifReply
                    NotificationType.FOLLOW,
                    NotificationType.FOLLOW_REQUEST_ACCEPTED -> Keys.notifFollow
                    NotificationType.COMMUNITY -> Keys.notifCommunity
                    NotificationType.SPACE -> Keys.notifSpace
                    NotificationType.MESSAGE -> Keys.notifMessages
                    NotificationType.GROUP_JOIN_REQUEST,
                    NotificationType.GROUP_JOIN_APPROVED,
                    NotificationType.GROUP_JOIN_REJECTED -> Keys.notifGroups
                    NotificationType.STORY_REPLY -> Keys.notifStories
                    NotificationType.MENTION -> Keys.notifMentions
                    NotificationType.WELCOME, NotificationType.SYSTEM -> Keys.notifSystem
                }
                prefs[key] = enabled
            }
        }
    }

    suspend fun setNotificationTypeEnabled(type: NotificationType, enabled: Boolean) {
        val key = when (type) {
            NotificationType.LIKE, NotificationType.COMMENT_LIKE -> Keys.notifLike
            NotificationType.COMMENT -> Keys.notifComment
            NotificationType.REPLY -> Keys.notifReply
            NotificationType.FOLLOW,
            NotificationType.FOLLOW_REQUEST_ACCEPTED -> Keys.notifFollow
            NotificationType.COMMUNITY -> Keys.notifCommunity
            NotificationType.SPACE -> Keys.notifSpace
            NotificationType.MESSAGE -> Keys.notifMessages
            NotificationType.GROUP_JOIN_REQUEST,
            NotificationType.GROUP_JOIN_APPROVED,
            NotificationType.GROUP_JOIN_REJECTED -> Keys.notifGroups
            NotificationType.STORY_REPLY -> Keys.notifStories
            NotificationType.MENTION -> Keys.notifMentions
            NotificationType.WELCOME,
            NotificationType.SYSTEM -> Keys.notifSystem

        } ?: return
        context.nexaDataStore.edit { it[key] = enabled }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun encodePrivacySettings(settings: PrivacySettings): String =
        JsonObject().apply {
            addProperty("privateAccount", settings.privateAccount)
            addProperty("defaultPostAudience", settings.defaultPostAudience.name)
            addProperty("storyAudience", settings.storyAudience.name)
            addProperty("storyRepliesFrom", settings.storyRepliesFrom.name)
            addProperty("storySharingEnabled", settings.storySharingEnabled)
            addProperty("showFollowersList", settings.showFollowersList)
            addProperty("showFollowingList", settings.showFollowingList)
            addProperty("showActivityStatus", settings.showActivityStatus)
            addProperty("showReadReceipts", settings.showReadReceipts)
            addProperty("messagesFrom", settings.messagesFrom.name)
            addProperty("groupInvitesFrom", settings.groupInvitesFrom.name)
            addProperty("hideNotificationContent", settings.hideNotificationContent)
            addProperty("whoCanComment", settings.whoCanComment.name)
            addProperty("whoCanReact", settings.whoCanReact.name)
            addProperty("whoCanMention", settings.whoCanMention.name)
            addProperty("mentionApprovalRequired", settings.mentionApprovalRequired)
            addProperty("showInSearch", settings.showInSearch)
            addProperty("allowSuggestions", settings.allowSuggestions)
        }.toString()

    private fun decodePrivacySettings(raw: String): PrivacySettings? = runCatching {
        val json = JsonParser.parseString(raw).asJsonObject
        val defaults = PrivacySettings()
        PrivacySettings(
            privateAccount = json.boolean("privateAccount") ?: defaults.privateAccount,
            defaultPostAudience = enumOrDefault(
                json.string("defaultPostAudience"),
                defaults.defaultPostAudience,
            ),
            storyAudience = enumOrDefault(json.string("storyAudience"), defaults.storyAudience),
            storyRepliesFrom = enumOrDefault(
                json.string("storyRepliesFrom"),
                defaults.storyRepliesFrom,
            ),
            storySharingEnabled = json.boolean("storySharingEnabled")
                ?: defaults.storySharingEnabled,
            showFollowersList = json.boolean("showFollowersList") ?: defaults.showFollowersList,
            showFollowingList = json.boolean("showFollowingList") ?: defaults.showFollowingList,
            showActivityStatus = json.boolean("showActivityStatus") ?: defaults.showActivityStatus,
            showReadReceipts = json.boolean("showReadReceipts") ?: defaults.showReadReceipts,
            messagesFrom = enumOrDefault(json.string("messagesFrom"), defaults.messagesFrom),
            groupInvitesFrom = enumOrDefault(
                json.string("groupInvitesFrom"),
                defaults.groupInvitesFrom,
            ),
            hideNotificationContent = json.boolean("hideNotificationContent")
                ?: defaults.hideNotificationContent,
            whoCanComment = enumOrDefault(json.string("whoCanComment"), defaults.whoCanComment),
            whoCanReact = enumOrDefault(json.string("whoCanReact"), defaults.whoCanReact),
            whoCanMention = enumOrDefault(json.string("whoCanMention"), defaults.whoCanMention),
            mentionApprovalRequired = json.boolean("mentionApprovalRequired")
                ?: defaults.mentionApprovalRequired,
            showInSearch = json.boolean("showInSearch") ?: defaults.showInSearch,
            allowSuggestions = json.boolean("allowSuggestions") ?: defaults.allowSuggestions,
        )
    }.getOrNull()

    private fun encodeReactionOverrides(overrides: Map<String, String>): String =
        JsonObject().apply {
            overrides.forEach { (postId, emoji) -> addProperty(postId, emoji) }
        }.toString()

    private fun decodeReactionOverrides(raw: String?): Map<String, String> = runCatching {
        if (raw.isNullOrBlank()) {
            emptyMap()
        } else {
            JsonParser.parseString(raw).asJsonObject.entrySet().associate { (postId, value) ->
                postId to if (value.isJsonNull) "" else value.asString
            }
        }
    }.getOrDefault(emptyMap())

    private fun JsonObject.boolean(name: String): Boolean? =
        get(name)?.takeIf { !it.isJsonNull }?.asBoolean

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull }?.asString

    object SessionType {
        const val GUEST = "guest"
        const val ACCOUNT = "account"
    }

    object SessionBackend {
        const val NONE = "none"
        const val LOCAL = "local"
        const val LEGACY_REMOTE = "firebase"
        const val SUPABASE = "supabase"
    }

    object LanguageTag {
        const val SYSTEM = "system"
        const val ARABIC = "ar"
        const val ENGLISH = "en"
    }
}
