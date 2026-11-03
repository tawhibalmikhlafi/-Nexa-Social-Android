package com.nexa.social.data.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.boolean
import com.nexa.social.core.supabase.string
import com.nexa.social.data.remote.SupabaseAuthDataSource
import com.nexa.social.domain.model.AudienceType
import com.nexa.social.domain.model.GroupInvitePolicy
import com.nexa.social.domain.model.MessagePolicy
import com.nexa.social.domain.model.PrivacySettings
import com.nexa.social.domain.repository.PrivacyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PrivacyRepositoryImpl(
    private val http: SupabaseHttp,
    private val auth: SupabaseAuthDataSource,
    private val prefs: PreferenceStorage,
) : PrivacyRepository {

    private val baseUrl: String
        get() = "${http.config.restUrl}/user_privacy_settings"

    override fun observe(): Flow<PrivacySettings> = prefs.privacySettings

    override suspend fun load(): Resource<PrivacySettings> = withContext(Dispatchers.IO) {
        val token = auth.currentAccessToken()
            ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
        val uid = auth.currentUid()
            ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)

        val request = http
            .authenticatedRequest("$baseUrl?user_id=eq.$uid&select=*&limit=1", token)
            .get()
            .build()

        val response = http.execute(request).getOrElse { error ->
            return@withContext Resource.Failure(http.transportFailure(error))
        }

        if (!response.successful) {
            return@withContext Resource.Failure(http.failureReason(response))
        }

        val remote = runCatching {
            JsonParser.parseString(response.body).asJsonArray.firstOrNull()
        }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            ?.let(PrivacyJson::read)
            ?: PrivacySettings.safeDefaults()

        // Sync cache so guest/offline paths show the same values.
        prefs.setPrivacySettings(remote)
        Resource.Success(remote)
    }

    override suspend fun save(settings: PrivacySettings): Resource<PrivacySettings> =
        withContext(Dispatchers.IO) {
            val token = auth.currentAccessToken()
                ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)
            val uid = auth.currentUid()
                ?: return@withContext Resource.Failure(FailureReason.NOT_AUTHENTICATED)

            val body = PrivacyJson.write(settings, uid)
            val request = http
                .authenticatedRequest("$baseUrl?on_conflict=user_id", token)
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(http.jsonBody(body))
                .build()

            val response = http.execute(request).getOrElse { error ->
                return@withContext Resource.Failure(http.transportFailure(error))
            }

            if (!response.successful) {
                return@withContext Resource.Failure(http.failureReason(response))
            }

            // Only cache after server confirms — never on the optimistic UI path.
            prefs.setPrivacySettings(settings)
            Resource.Success(settings)
        }
}

internal object PrivacyJson {
    fun read(obj: JsonObject): PrivacySettings = PrivacySettings(
        privateAccount = obj.boolean("is_private_account") ?: false,
        defaultPostAudience = AudienceType.fromWire(obj.string("default_post_audience")),
        storyAudience = AudienceType.fromWire(obj.string("story_audience")),
        storyRepliesFrom = AudienceType.fromWire(obj.string("story_replies_from")),
        storySharingEnabled = obj.boolean("story_sharing_enabled") ?: true,
        showFollowersList = obj.boolean("show_followers_list") ?: true,
        showFollowingList = obj.boolean("show_following_list") ?: true,
        showActivityStatus = obj.boolean("show_activity_status") ?: true,
        showReadReceipts = obj.boolean("show_read_receipts") ?: true,
        messagesFrom = MessagePolicy.fromWire(obj.string("messages_from")),
        groupInvitesFrom = GroupInvitePolicy.fromWire(obj.string("group_invites_from")),
        hideNotificationContent = obj.boolean("hide_notification_content") ?: false,
        whoCanComment = AudienceType.fromWire(obj.string("who_can_comment")),
        whoCanReact = AudienceType.fromWire(obj.string("who_can_react")),
        whoCanMention = AudienceType.fromWire(obj.string("who_can_mention")),
        mentionApprovalRequired = obj.boolean("mention_approval_required") ?: false,
        showInSearch = obj.boolean("show_in_search") ?: true,
        allowSuggestions = obj.boolean("allow_suggestions") ?: true,
    )

    fun write(settings: PrivacySettings, userId: String): JsonObject = JsonObject().apply {
        addProperty("user_id", userId)
        addProperty("is_private_account", settings.privateAccount)
        addProperty("default_post_audience", settings.defaultPostAudience.wire)
        addProperty("story_audience", settings.storyAudience.wire)
        addProperty("story_replies_from", settings.storyRepliesFrom.wire)
        addProperty("story_sharing_enabled", settings.storySharingEnabled)
        addProperty("show_followers_list", settings.showFollowersList)
        addProperty("show_following_list", settings.showFollowingList)
        addProperty("show_activity_status", settings.showActivityStatus)
        addProperty("show_read_receipts", settings.showReadReceipts)
        addProperty("messages_from", settings.messagesFrom.wire)
        addProperty("group_invites_from", settings.groupInvitesFrom.wire)
        addProperty("hide_notification_content", settings.hideNotificationContent)
        addProperty("who_can_comment", settings.whoCanComment.wire)
        addProperty("who_can_react", settings.whoCanReact.wire)
        addProperty("who_can_mention", settings.whoCanMention.wire)
        addProperty("mention_approval_required", settings.mentionApprovalRequired)
        addProperty("show_in_search", settings.showInSearch)
        addProperty("allow_suggestions", settings.allowSuggestions)
    }
}
