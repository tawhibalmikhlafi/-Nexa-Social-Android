package com.nexa.social.domain.repository

import android.content.Context
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.domain.model.AchievementSnapshot
import com.nexa.social.domain.model.AiRequest
import com.nexa.social.domain.model.AiResult
import com.nexa.social.domain.model.Audience
import com.nexa.social.domain.model.BlockedUser
import com.nexa.social.domain.model.Clip
import com.nexa.social.domain.model.ClipComment
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.CommunityCategory
import com.nexa.social.domain.model.CommunityMember
import com.nexa.social.domain.model.CommunityPost
import com.nexa.social.domain.model.ContentKind
import com.nexa.social.domain.model.Conversation
import com.nexa.social.domain.model.CounterKind
import com.nexa.social.domain.model.FeedType
import com.nexa.social.domain.model.FollowRequest
import com.nexa.social.domain.model.HiddenContent
import com.nexa.social.domain.model.Message
import com.nexa.social.domain.model.MutedUser
import com.nexa.social.domain.model.NexaNotification
import com.nexa.social.domain.model.NotificationType
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.PostComment
import com.nexa.social.domain.model.PostDraft
import com.nexa.social.domain.model.PostReaction
import com.nexa.social.domain.model.PrivacySettings
import com.nexa.social.domain.model.ReportReason
import com.nexa.social.domain.model.ReportTargetType
import com.nexa.social.domain.model.SessionState
import com.nexa.social.domain.model.SignUpRequest
import com.nexa.social.domain.model.Space
import com.nexa.social.domain.model.SpaceCategory
import com.nexa.social.domain.model.Story
import com.nexa.social.domain.model.StoryAudienceMember
import com.nexa.social.domain.model.StoryGroup
import com.nexa.social.domain.model.ThemeConfig
import com.nexa.social.domain.model.User
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.domain.model.WorldPrivacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Authentication and session handling.
 *
 * When Supabase is configured, sign-up/sign-in use Supabase Auth and the Supabase user id is the
 * primary identity for Supabase and Storage. Accounts created before the migration remain
 * available on this device as local accounts. With no Supabase configuration the app keeps the
 * original local-only behaviour.
 */
interface AuthRepository {
    val session: Flow<SessionState>
    val currentUser: Flow<User?>

    /** True when the Supabase project URL and publishable key are configured. */
    val remoteAuthEnabled: Boolean get() = false

    /** True when an OAuth provider is configured, which Google Sign-In requires. */
    val googleSignInConfigured: Boolean get() = false

    /** True after a sign-up that is waiting for the confirmation email. */
    val emailVerificationPending: Boolean get() = false

    /** Emits true while the active session is backed by Supabase Auth. */
    val remoteMode: Flow<Boolean> get() = flowOf(false)

    suspend fun signUp(request: SignUpRequest): Resource<User>
    suspend fun signIn(username: String, password: String, remember: Boolean = true): Resource<User>
    suspend fun enterGuestMode()
    suspend fun signOut()

    /** Revokes every device's refresh token and clears the local session. */
    suspend fun signOutEverywhere(): Resource<Unit> {
        signOut()
        return Resource.Success(Unit)
    }

    suspend fun requestPasswordReset(email: String): Resource<Unit>
    suspend fun completeProfileSetup(displayName: String, bio: String, avatarUri: String?)

    suspend fun signInWithGoogle(context: Context): Resource<User> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    suspend fun sendEmailVerification(): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    /**
     * Completes an email-confirmation or password-recovery redirect. The tokens are validated with
     * Supabase Auth before any session is persisted.
     */
    suspend fun completeAuthCallback(accessToken: String, refreshToken: String): Resource<User> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    /** Updates the password of the signed-in account after a recovery link was opened. */
    suspend fun updatePassword(password: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    /**
     * Changes the password after verifying the current one. Implementations must not store the
     * current password anywhere and must reject accounts without a remote session.
     */
    suspend fun changePasswordWithVerification(
        currentPassword: String,
        newPassword: String,
    ): Resource<Unit> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    /** Starts syncing the persisted session with Supabase and keeps it refreshed. */
    fun startSessionSync(scope: CoroutineScope) {}
}

interface UserRepository {
    val currentUser: Flow<User?>

    /** 0..100 while profile media is uploading; 0 when idle. */
    val uploadProgress: Flow<Int> get() = flowOf(0)

    fun observeUser(userId: String): Flow<User?>
    suspend fun getUser(userId: String): User?
    fun observeAllUsers(): Flow<List<User>>
    suspend fun searchUsers(query: String): List<User>
    suspend fun usernameTakenLocally(username: String): Boolean
    suspend fun updateProfile(
        userId: String,
        displayName: String,
        bio: String,
        avatarUri: String?,
        coverUri: String?,
        city: String? = null,
        website: String? = null,
        username: String? = null
    ): Resource<User>

    // Local follow graph. Following a sample account is a local preference, not a server relationship.
    fun observeFollowingIds(): Flow<Set<String>>
    fun observeFollowing(): Flow<List<User>>
    fun observeFollowers(): Flow<List<User>>

    /** Followers/following for any profile. Local-only accounts have no server graph. */
    fun observeFollowersOf(userId: String): Flow<List<User>>
    fun observeFollowingOf(userId: String): Flow<List<User>>

    suspend fun setFollowing(userId: String, following: Boolean): Boolean
    suspend fun isFollowing(userId: String): Boolean
    suspend fun hasPendingFollowRequest(userId: String): Boolean? = null
    suspend fun loadIncomingFollowRequests(): List<FollowRequest>? = null
    suspend fun respondToFollowRequest(requestId: String, approve: Boolean): Boolean = false

    suspend fun refresh() {}
}

interface PostRepository {
    /** 0..100 while remote media is uploading; 0 when idle. */
    val uploadProgress: Flow<Int> get() = flowOf(0)

    fun observeFeed(): Flow<List<Post>>
    fun observePostsByAuthor(authorId: String): Flow<List<Post>>
    fun observeSavedPosts(): Flow<List<Post>>
    fun observeHiddenPosts(): Flow<List<Post>>
    fun observePost(postId: String): Flow<Post?>
    suspend fun createPost(
        text: String,
        imageUris: List<String>,
        tags: List<String>,
        audience: Audience,
        communityId: String?
    ): Post
    suspend fun updatePost(
        postId: String,
        text: String,
        imageUris: List<String>,
        tags: List<String>,
        audience: Audience
    ): Post?
    suspend fun toggleLike(postId: String, reaction: String? = null)
    suspend fun getPostReactions(postId: String): List<PostReaction> = emptyList()
    suspend fun refresh() {}
    suspend fun toggleSave(postId: String)
    suspend fun incrementShare(postId: String)
    suspend fun adjustCommentCount(postId: String, delta: Int)
    suspend fun deletePost(postId: String): Boolean
    suspend fun hidePost(postId: String)
    suspend fun unhidePost(postId: String)
    suspend fun togglePin(postId: String)
    fun observeDraft(): Flow<PostDraft?>
    suspend fun saveDraft(draft: PostDraft)
    suspend fun clearDraft()

    /** Updates the embedded author snapshot after a profile/avatar change. */
    suspend fun refreshAuthorSnapshot(author: UserSummary) {}

    /** Requests the next remote feed page; no-op for the local feed. */
    suspend fun loadMoreFeed() {}
}

interface CommentRepository {
    fun observeComments(postId: String): Flow<List<PostComment>>
    suspend fun getCommentPreview(postId: String): List<PostComment>
    fun observeCommentCount(postId: String): Flow<Int>
    suspend fun addComment(
        postId: String,
        text: String,
        parentId: String? = null,
        imageUri: String? = null
    ): Resource<PostComment>
    suspend fun editComment(commentId: String, text: String): Resource<PostComment>
    suspend fun deleteComment(commentId: String): Boolean
    suspend fun toggleCommentLike(commentId: String, emoji: String = "❤️")
    suspend fun getCommentReactions(commentId: String): List<com.nexa.social.domain.model.PostReaction>
    fun observeCommentPageSize(): Flow<Int> = flowOf(100)
    suspend fun loadMoreComments() {}

    /** Updates the embedded author snapshot after a profile/avatar change. */
    suspend fun refreshAuthorSnapshot(author: UserSummary) {}

    companion object {
        const val MIN_COMMENT_LENGTH = 1
        const val MAX_COMMENT_LENGTH = 1_000
    }
}

interface CommunityRepository {
    fun observeCommunities(): Flow<List<Community>>
    fun observeJoinedCommunities(): Flow<List<Community>>
    fun observeCommunity(id: String): Flow<Community?>
    suspend fun toggleMembership(id: String)
    fun observeCommunityMembers(communityId: String): Flow<List<CommunityMember>>
    fun observeCommunityPosts(communityId: String): Flow<List<CommunityPost>>
    suspend fun createCommunityPost(communityId: String, text: String, imageUris: List<String>): CommunityPost
    suspend fun deleteCommunityPost(postId: String): Boolean
    suspend fun toggleCommunityPostLike(communityId: String, postId: String, reaction: String? = null)
    suspend fun refresh() {}
    suspend fun createCommunity(
        name: String,
        description: String,
        category: CommunityCategory,
        privacy: WorldPrivacy,
        rules: List<String>
    ): Resource<Community>
    suspend fun updateCommunity(
        id: String,
        name: String,
        description: String,
        category: CommunityCategory,
        privacy: WorldPrivacy
    ): Community?
    suspend fun deleteCommunity(id: String): Boolean
    suspend fun searchCommunities(query: String): List<Community>
}

interface SpaceRepository {
    fun observeSpaces(communityId: String): Flow<List<Space>>
    fun observeAllSpaces(): Flow<List<Space>>
    fun observeSpace(id: String): Flow<Space?>
    suspend fun createSpace(
        communityId: String,
        title: String,
        description: String,
        category: SpaceCategory
    ): Resource<Space>
    suspend fun updateSpace(spaceId: String, title: String, description: String, category: SpaceCategory): Boolean
    suspend fun deleteSpace(spaceId: String): Boolean
    suspend fun addResource(
        spaceId: String,
        title: String,
        description: String,
        reference: String?
    ): Boolean
    suspend fun removeResource(spaceId: String, resourceId: String): Boolean
    suspend fun addChallenge(spaceId: String, prompt: String): Boolean
    suspend fun toggleChallenge(spaceId: String, challengeId: String): Boolean
    suspend fun refresh() {}
}

interface ClipRepository {
    fun observeClips(): Flow<List<Clip>>
    fun observeClip(id: String): Flow<Clip?>
    suspend fun addClip(videoUri: String, caption: String, coverUri: String? = null): Resource<Clip>
    suspend fun toggleLike(clipId: String)
    suspend fun toggleSave(clipId: String)
    suspend fun getComments(clipId: String): Resource<List<ClipComment>> = Resource.Success(emptyList())
    suspend fun addComment(clipId: String, content: String): Resource<ClipComment> = Resource.Failure(FailureReason.NOT_CONFIGURED)
    suspend fun deleteComment(commentId: String): Boolean = false
    suspend fun recordView(clipId: String) {}
    suspend fun updateCaption(clipId: String, caption: String): Boolean = false
    suspend fun deleteClip(clipId: String): Boolean
    suspend fun refresh() {}
}

interface StoryRepository {
    fun observeActiveStories(): Flow<List<Story>>
    fun observeStoryGroups(): Flow<List<StoryGroup>>
    suspend fun createStory(imageUri: String?, text: String?): Resource<Story>
    suspend fun markViewed(storyId: String)
    suspend fun getStoryAudience(storyId: String): Resource<List<StoryAudienceMember>> =
        Resource.Success(emptyList())
    suspend fun deleteStory(storyId: String): Boolean
    suspend fun refresh() {}
}

interface MessageRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeConversation(id: String): Flow<Conversation?>
    suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachmentUri: String? = null
    ): Resource<Message>
    suspend fun resolveAttachmentUrl(message: Message): String?
    suspend fun deleteMessage(conversationId: String, messageId: String): Boolean
    suspend fun markConversationRead(id: String)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun setMuted(id: String, muted: Boolean)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun startConversation(participantId: String): Conversation?

    suspend fun editMessage(messageId: String, text: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    suspend fun deleteMessageForMe(conversationId: String, messageId: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    suspend fun deleteMessageForEveryone(
        conversationId: String,
        messageId: String,
    ): Resource<Unit> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    suspend fun reactToMessage(messageId: String, emoji: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    suspend fun unreactToMessage(messageId: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    suspend fun sendStoryReply(
        recipientId: String,
        storyId: String,
        storyPreview: String,
        text: String,
    ): Resource<Unit> = Resource.Failure(FailureReason.NOT_CONFIGURED)

    suspend fun setStarred(messageId: String, starred: Boolean): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    fun observeStarredMessages(): Flow<List<Message>> = kotlinx.coroutines.flow.flowOf(emptyList())

    fun observeMessageRequests(): Flow<List<Conversation>> = kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun acceptMessageRequest(conversationId: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    suspend fun rejectMessageRequest(conversationId: String): Resource<Unit> =
        Resource.Failure(FailureReason.NOT_CONFIGURED)

    suspend fun refresh() {}
}

interface NotificationRepository {
    fun observeNotifications(): Flow<List<NexaNotification>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun markRead(id: String)
    suspend fun markAllRead()
    suspend fun delete(id: String)
    suspend fun clearAll()
    fun observeTypeEnabled(): Flow<Map<NotificationType, Boolean>>
    suspend fun setTypeEnabled(type: NotificationType, enabled: Boolean)

    /** Pulls the signed-in account's notification preferences onto this device. */
    suspend fun syncPreferences(): Boolean = false

    /** Persists this device's notification preferences to the signed-in account. */
    suspend fun persistPreferences(): Boolean = false

    /** True while notifications come from Supabase instead of the local cache. */
    fun observeIsRemote(): Flow<Boolean> = flowOf(false)

    suspend fun refresh() {}
}

interface SearchRepository {
    /** Searches local sample and local-dev accounts only; never queries a remote directory. */
    suspend fun searchPeople(query: String): List<User>
    suspend fun searchPosts(query: String): List<Post>
    suspend fun searchWorlds(query: String): List<Community>
    suspend fun searchSpaces(query: String): List<Space>
    suspend fun searchTags(query: String): List<String>
    val history: Flow<List<String>>
    suspend fun addHistory(query: String)
    suspend fun clearHistory()
}

interface FeedRepository {
    /** Deterministic local ranking. Replaceable by a networked recommender later. */
    fun observeFeed(type: FeedType): Flow<List<Post>>
}

interface SafetyRepository {
    fun observeBlockedUsers(): Flow<List<BlockedUser>>
    fun observeMutedUsers(): Flow<List<MutedUser>>
    fun observeHiddenContent(): Flow<List<HiddenContent>>
    fun observePrivacySettings(): Flow<PrivacySettings>
    fun observeBlockedIds(): Flow<Set<String>>
    suspend fun block(user: UserSummary): Resource<Unit>
    suspend fun unblock(userId: String): Resource<Unit>
    suspend fun mute(user: UserSummary): Resource<Unit>
    suspend fun unmute(userId: String): Resource<Unit>
    suspend fun hide(contentId: String, kind: ContentKind)
    suspend fun unhide(contentId: String)
    /** Sends a moderation report to the remote backend for authenticated accounts. */
    suspend fun submitReport(
        targetType: ReportTargetType,
        targetId: String,
        reason: ReportReason,
        note: String
    ): Resource<Unit>
    suspend fun updatePrivacy(settings: PrivacySettings)
}

interface AchievementRepository {
    fun observeSnapshot(): Flow<AchievementSnapshot>
    /** Records one unit of a real local activity and unlocks any achievement it completes. */
    suspend fun record(kind: CounterKind, amount: Int = 1)
}

interface ThemeRepository {
    val themeConfig: Flow<ThemeConfig>
    suspend fun save(config: ThemeConfig)
}

/**
 * Contract for the future Nexa AI provider. No provider is configured in this build, so
 * [com.nexa.social.data.repository.AiRepositoryImpl] reports the feature as unavailable rather
 * than fabricating model output.
 */
interface AiRepository {
    val isConfigured: Boolean
    suspend fun run(request: AiRequest): Resource<AiResult>
}
