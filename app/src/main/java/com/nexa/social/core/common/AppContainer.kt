package com.nexa.social.core.common

import android.app.Application
import android.content.Context
import com.nexa.social.core.lifecycle.PresenceTracker
import com.nexa.social.core.network.NetworkMonitor
import com.nexa.social.core.sync.SyncCoordinator
import com.nexa.social.core.sync.Syncable
import com.nexa.social.data.local.AccountLocalDataSource
import com.nexa.social.data.local.CapsuleLocalDataSource
import com.nexa.social.data.local.ClipLocalDataSource
import com.nexa.social.data.local.CommentLocalDataSource
import com.nexa.social.data.local.CommunityLocalDataSource
import com.nexa.social.data.local.GatheringLocalDataSource
import com.nexa.social.data.local.MessageLocalDataSource
import com.nexa.social.data.local.NotificationLocalDataSource
import com.nexa.social.data.local.PostLocalDataSource
import com.nexa.social.data.local.SafetyLocalDataSource
import com.nexa.social.data.local.SearchLocalDataSource
import com.nexa.social.data.local.SpaceLocalDataSource
import com.nexa.social.data.local.StoryLocalDataSource
import com.nexa.social.data.local.WellbeingLocalDataSource
import com.nexa.social.data.repository.AchievementRepositoryImpl
import com.nexa.social.data.repository.AccountRepositoryImpl
import com.nexa.social.data.repository.AiRepositoryImpl
import com.nexa.social.data.repository.AuthRepositoryImpl
import com.nexa.social.data.repository.CapsuleRepositoryImpl
import com.nexa.social.data.repository.ClipRepositoryImpl
import com.nexa.social.data.repository.CommentRepositoryImpl
import com.nexa.social.data.repository.CommunityRepositoryImpl
import com.nexa.social.data.repository.FeedRepositoryImpl
import com.nexa.social.data.repository.GatheringRepositoryImpl
import com.nexa.social.data.repository.GroupRepositoryImpl
import com.nexa.social.data.repository.LocalPrivacyRepository
import com.nexa.social.data.repository.MessageRepositoryImpl
import com.nexa.social.data.repository.NotificationRepositoryImpl
import com.nexa.social.data.repository.PostRepositoryImpl
import com.nexa.social.data.repository.PresenceRepositoryImpl
import com.nexa.social.data.repository.PrivacyRepositoryImpl
import com.nexa.social.data.repository.SafetyRepositoryImpl
import com.nexa.social.data.repository.SearchRepositoryImpl
import com.nexa.social.data.repository.SessionRepositoryImpl
import com.nexa.social.data.repository.SpaceRepositoryImpl
import com.nexa.social.data.repository.StoryRepositoryImpl
import com.nexa.social.data.repository.ThemeRepositoryImpl
import com.nexa.social.data.repository.UnconfiguredGroupRepository
import com.nexa.social.data.repository.UserRepositoryImpl
import com.nexa.social.data.repository.WellbeingRepositoryImpl
import com.nexa.social.core.auth.AuthSession
import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.supabase.AccessTokenProvider
import com.nexa.social.core.supabase.CurrentUidProvider
import com.nexa.social.core.supabase.SupabaseConfig
import com.nexa.social.core.supabase.SupabaseHttp
import com.nexa.social.core.supabase.SupabaseTokenStore
import com.nexa.social.data.media.MediaStorage
import com.nexa.social.data.remote.SupabaseAuthDataSource
import com.nexa.social.data.remote.SupabaseCommentDataSource
import com.nexa.social.data.remote.SupabaseClipDataSource
import com.nexa.social.data.remote.SupabaseMessageDataSource
import com.nexa.social.data.remote.SupabaseNotificationDataSource
import com.nexa.social.data.remote.SupabaseNotesDataSource
import com.nexa.social.data.remote.SupabaseLifeDataSource
import com.nexa.social.data.remote.SupabasePostDataSource
import com.nexa.social.data.remote.SupabaseSafetyDataSource
import com.nexa.social.data.remote.SupabaseStorageDataSource
import com.nexa.social.data.remote.SupabaseStoryDataSource
import com.nexa.social.data.remote.SupabaseUserDataSource
import com.nexa.social.data.remote.SupabaseProfileExperienceDataSource
import com.nexa.social.feature.pulse.SupabaseDailyPulseRepository
import com.nexa.social.domain.model.UserSession
import com.nexa.social.domain.model.accountUserId
import com.nexa.social.domain.repository.AchievementRepository
import com.nexa.social.domain.repository.AiRepository
import com.nexa.social.domain.repository.AccountRepository
import com.nexa.social.domain.repository.AuthRepository
import com.nexa.social.domain.repository.CapsuleRepository
import com.nexa.social.domain.repository.ClipRepository
import com.nexa.social.domain.repository.CommentRepository
import com.nexa.social.domain.repository.CommunityRepository
import com.nexa.social.domain.repository.FeedRepository
import com.nexa.social.domain.repository.GatheringRepository
import com.nexa.social.domain.repository.GroupRepository
import com.nexa.social.domain.repository.MessageRepository
import com.nexa.social.domain.repository.NotificationRepository
import com.nexa.social.domain.repository.PostRepository
import com.nexa.social.domain.repository.PresenceRepository
import com.nexa.social.domain.repository.PrivacyRepository
import com.nexa.social.domain.repository.SafetyRepository
import com.nexa.social.domain.repository.SearchRepository
import com.nexa.social.domain.repository.SessionRepository
import com.nexa.social.domain.repository.SpaceRepository
import com.nexa.social.domain.repository.StoryRepository
import com.nexa.social.domain.repository.ThemeRepository
import com.nexa.social.domain.repository.UserRepository
import com.nexa.social.domain.repository.WellbeingRepository
import com.nexa.social.domain.usecase.CreatePostUseCase
import com.nexa.social.domain.usecase.SearchPeopleUseCase
import com.nexa.social.domain.usecase.ValidateUsernameUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/** Manual dependency container. Keeps construction in one place without a DI framework. */
interface AppContainer {
    val preferenceStorage: PreferenceStorage
    val authRepository: AuthRepository
    val userRepository: UserRepository
    val postRepository: PostRepository
    val commentRepository: CommentRepository
    val communityRepository: CommunityRepository
    val storyRepository: StoryRepository
    val spaceRepository: SpaceRepository
    val clipRepository: ClipRepository
    val messageRepository: MessageRepository
    val profileExperienceDataSource: SupabaseProfileExperienceDataSource?
    val notesDataSource: SupabaseNotesDataSource?
    val notificationRepository: NotificationRepository
    val searchRepository: SearchRepository
    val feedRepository: FeedRepository
    val safetyRepository: SafetyRepository
    val achievementRepository: AchievementRepository
    val aiRepository: AiRepository
    val themeRepository: ThemeRepository
    val gatheringRepository: GatheringRepository
    val capsuleRepository: CapsuleRepository
    val wellbeingRepository: WellbeingRepository
    val privacyRepository: PrivacyRepository
    val sessionRepository: SessionRepository
    val accountRepository: AccountRepository
    val presenceRepository: PresenceRepository
    val groupRepository: GroupRepository
    val dailyPulseRepository: SupabaseDailyPulseRepository?
    val presenceTracker: PresenceTracker
    val syncCoordinator: SyncCoordinator

    /** Null when Supabase is not configured; repositories then use local storage only. */
    val authSession: AuthSession?

    val validateUsername: ValidateUsernameUseCase
    val createPost: CreatePostUseCase
    val searchPeople: SearchPeopleUseCase

    /**
     * Starts the best-effort background work that needs the network and session restore). Deliberately not called from Application.onCreate: the UI must be
     * able to open even when the network or Google Play services are unavailable.
     */
    fun startBackgroundSync()
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext
    private val jsonStore = JsonFileStore(appContext)

    override val preferenceStorage = PreferenceStorage(appContext)

    private val accountLocal = AccountLocalDataSource(jsonStore)
    private val postLocal = PostLocalDataSource(jsonStore)
    private val commentLocal = CommentLocalDataSource(jsonStore)
    private val communityLocal = CommunityLocalDataSource(jsonStore)
    private val storyLocal = StoryLocalDataSource(jsonStore)
    private val spaceLocal = SpaceLocalDataSource(jsonStore)
    private val clipLocal = ClipLocalDataSource(jsonStore)
    private val messageLocal = MessageLocalDataSource(jsonStore)
    private val notificationLocal = NotificationLocalDataSource(jsonStore)
    private val safetyLocal = SafetyLocalDataSource(jsonStore)
    private val searchLocal = SearchLocalDataSource(jsonStore)
    private val gatheringLocal = GatheringLocalDataSource(jsonStore)
    private val capsuleLocal = CapsuleLocalDataSource(jsonStore)
    private val wellbeingLocal = WellbeingLocalDataSource(jsonStore)

    // Session sync is best-effort background work. It must never
    // terminate the process: a failure here (e.g. the server is unreachable) is isolated to this
    // scope instead of reaching the uncaught-exception handler right after app start.
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> }
    )
    private val supabaseConfig = SupabaseConfig.fromBuild()
    private val supabaseHttp = if (supabaseConfig.configured) SupabaseHttp(supabaseConfig) else null
    private val supabaseTokens = supabaseHttp?.let { SupabaseTokenStore(appContext) }
    private val mediaStorage: MediaStorage? =
        if (supabaseHttp != null && supabaseTokens != null) {
            SupabaseStorageDataSource(
                context = appContext,
                config = supabaseConfig,
                http = supabaseHttp,
                accessToken = { supabaseTokens.currentSession()?.accessToken }
            )
        } else {
            null
        }

    // Accounts, profiles, usernames, the follow graph, posts and messages live in Supabase.
    private val remoteUsers = if (supabaseHttp != null && supabaseTokens != null) {
        SupabaseUserDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            accessToken = { supabaseTokens.currentSession()?.accessToken },
            currentUid = { supabaseTokens.currentSession()?.userId },
            emailProvider = { supabaseTokens.currentSession()?.email }
        )
    } else {
        null
    }
    private val supabaseAuth = if (supabaseHttp != null && supabaseTokens != null) {
        SupabaseAuthDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            tokens = supabaseTokens,
            users = remoteUsers,
            storage = mediaStorage
        )
    } else {
        null
    }
    override val authSession: AuthSession? = supabaseAuth

    private val accessTokenProvider = object : AccessTokenProvider {
        override suspend fun token(): String? = supabaseAuth?.currentAccessToken()
    }

    private val currentUidProvider = object : CurrentUidProvider {
        override suspend fun uid(): String? =
            supabaseAuth?.currentUid() ?: supabaseTokens?.currentSession()?.userId
    }

    override val presenceRepository: PresenceRepository = PresenceRepositoryImpl(
        http = supabaseHttp,
        accessToken = accessTokenProvider,
        currentUid = currentUidProvider,
        prefs = preferenceStorage,
    )

    override val dailyPulseRepository: SupabaseDailyPulseRepository? =
        if (supabaseHttp != null && supabaseAuth != null) {
            SupabaseDailyPulseRepository(
                config = supabaseConfig,
                http = supabaseHttp,
                accessToken = { supabaseAuth.currentAccessToken() },
                currentUid = { supabaseAuth.currentUid() },
                mediaStorage = mediaStorage
            )
        } else {
            null
        }

    override val groupRepository: GroupRepository =
        supabaseHttp?.let {
            GroupRepositoryImpl(it, accessTokenProvider, currentUidProvider, mediaStorage, jsonStore, appContext)
        } ?: UnconfiguredGroupRepository

    private val networkMonitor by lazy { NetworkMonitor(appContext) }

    override val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(
            networkMonitor = networkMonitor,
            targets = listOf(
                object : Syncable {
                    override suspend fun onNetworkRestored() {
                        presenceRepository.pingNow()
                    }
                },
                object : Syncable {
                    override suspend fun onNetworkRestored() {
                        postRepository.refresh()
                        storyRepository.refresh()
                        clipRepository.refresh()
                        messageRepository.refresh()
                        notificationRepository.refresh()
                        groupRepository.refreshMyGroups()
                        gatheringRepository.refresh()
                        capsuleRepository.refresh()
                        wellbeingRepository.refresh()
                    }
                },
            ),
            scope = applicationScope,
        )
    }

    override val presenceTracker: PresenceTracker by lazy {
        PresenceTracker(
            application = appContext as Application,
            presenceRepository = presenceRepository,
        )
    }

    private val remotePosts = if (supabaseHttp != null && supabaseTokens != null) {
        SupabasePostDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            accessToken = { supabaseTokens.currentSession()?.accessToken },
            currentUid = { supabaseTokens.currentSession()?.userId }
        )
    } else {
        null
    }
    private val remoteStories = if (supabaseHttp != null && supabaseTokens != null) {
        SupabaseStoryDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            accessToken = { supabaseTokens.currentSession()?.accessToken },
            currentUid = { supabaseTokens.currentSession()?.userId }
        )
    } else {
        null
    }
    private val remoteClips = if (supabaseHttp != null && supabaseTokens != null) {
        SupabaseClipDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            accessToken = { supabaseTokens.currentSession()?.accessToken },
            currentUid = { supabaseTokens.currentSession()?.userId }
        )
    } else {
        null
    }
    private val remoteComments = if (supabaseHttp != null && supabaseTokens != null) {
        SupabaseCommentDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            accessToken = { supabaseTokens.currentSession()?.accessToken },
            currentUid = { supabaseTokens.currentSession()?.userId }
        )
    } else {
        null
    }
    private val remoteMessages = if (supabaseHttp != null && supabaseTokens != null) {
        SupabaseMessageDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            accessToken = { supabaseTokens.currentSession()?.accessToken },
            remoteUsers = remoteUsers
        )
    } else {
        null
    }
    private val remoteNotes = if (supabaseHttp != null && supabaseTokens != null) {
        SupabaseNotesDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            accessToken = { supabaseTokens.currentSession()?.accessToken },
            currentUid = { supabaseTokens.currentSession()?.userId }
        )
    } else {
        null
    }

    private val remoteLife = if (supabaseHttp != null && supabaseTokens != null) {
        SupabaseLifeDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            accessToken = { supabaseTokens.currentSession()?.accessToken },
            currentUid = { supabaseTokens.currentSession()?.userId }
        )
    } else {
        null
    }

    private val remoteProfileExperience = if (supabaseHttp != null && supabaseTokens != null) {
        SupabaseProfileExperienceDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            accessToken = { supabaseTokens.currentSession()?.accessToken },
            currentUid = { supabaseTokens.currentSession()?.userId }
        )
    } else null

    // The remote achievement catalog is authoritative whenever Supabase is configured.
    override val achievementRepository: AchievementRepository =
        AchievementRepositoryImpl(
            remote = remoteProfileExperience,
            currentUid = { supabaseTokens?.currentSession()?.userId }
        )

    override val profileExperienceDataSource: SupabaseProfileExperienceDataSource? get() = remoteProfileExperience
    override val notesDataSource: SupabaseNotesDataSource? get() = remoteNotes

    private val remoteNotifications = if (supabaseHttp != null && supabaseTokens != null) {
        SupabaseNotificationDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            accessToken = { supabaseTokens.currentSession()?.accessToken }
        )
    } else {
        null
    }
    private val remoteSafety = if (supabaseHttp != null && supabaseTokens != null) {
        SupabaseSafetyDataSource(
            config = supabaseConfig,
            http = supabaseHttp,
            accessToken = { supabaseTokens.currentSession()?.accessToken },
            currentUid = { supabaseTokens.currentSession()?.userId }
        )
    } else {
        null
    }

    override val userRepository: UserRepository =
        UserRepositoryImpl(
            preferenceStorage,
            accountLocal,
            remoteUsers,
            mediaStorage
        )
    private val authRepositoryImpl =
        AuthRepositoryImpl(preferenceStorage, accountLocal, userRepository, supabaseAuth)
    override val authRepository: AuthRepository = authRepositoryImpl
    override val postRepository: PostRepository =
        PostRepositoryImpl(
            postLocal,
            userRepository,
            preferenceStorage,
            achievementRepository,
            remotePosts,
            mediaStorage
        )
    override val commentRepository: CommentRepository =
        CommentRepositoryImpl(
            commentLocal,
            postRepository,
            userRepository,
            preferenceStorage.session,
            achievementRepository,
            remoteComments,
            mediaStorage
        )
    override val communityRepository: CommunityRepository =
        CommunityRepositoryImpl(
            communityLocal,
            userRepository,
            preferenceStorage.session,
            achievementRepository
        )
    override val storyRepository: StoryRepository =
        StoryRepositoryImpl(
            local = storyLocal,
            userRepository = userRepository,
            sessionFlow = preferenceStorage.session,
            achievements = achievementRepository,
            prefs = preferenceStorage,
            mediaStorage = mediaStorage,
            remote = remoteStories
        )
    override val spaceRepository: SpaceRepository =
        SpaceRepositoryImpl(spaceLocal, userRepository, preferenceStorage.session)
    override val clipRepository: ClipRepository =
        ClipRepositoryImpl(clipLocal, userRepository, preferenceStorage.session, achievementRepository, mediaStorage, remoteClips, appContext)
    override val messageRepository: MessageRepository =
        MessageRepositoryImpl(
            preferenceStorage,
            messageLocal,
            userRepository,
            mediaStorage,
            appContext,
            remoteMessages
        )
    override val notificationRepository: NotificationRepository =
        NotificationRepositoryImpl(notificationLocal, preferenceStorage, remoteNotifications)
    override val searchRepository: SearchRepository =
        SearchRepositoryImpl(
            userRepository,
            postRepository,
            communityRepository,
            spaceRepository,
            searchLocal
        )
    override val feedRepository: FeedRepository =
        FeedRepositoryImpl(postRepository, userRepository, communityRepository)
    override val safetyRepository: SafetyRepository =
        SafetyRepositoryImpl(safetyLocal, preferenceStorage, remoteSafety)
    override val aiRepository: AiRepository = AiRepositoryImpl()
    override val themeRepository: ThemeRepository = ThemeRepositoryImpl(preferenceStorage)
    override val gatheringRepository: GatheringRepository = GatheringRepositoryImpl(
        gatheringLocal,
        userRepository,
        preferenceStorage.session,
        remoteLife
    )
    override val capsuleRepository: CapsuleRepository = CapsuleRepositoryImpl(
        capsuleLocal,
        userRepository,
        preferenceStorage.session,
        remoteLife
    )
    override val wellbeingRepository: WellbeingRepository = WellbeingRepositoryImpl(
        wellbeingLocal,
        remoteLife
    )

    override val privacyRepository: PrivacyRepository =
        if (supabaseHttp != null && supabaseAuth != null) {
            PrivacyRepositoryImpl(supabaseHttp, supabaseAuth, preferenceStorage)
        } else {
            LocalPrivacyRepository(preferenceStorage)
        }

    override val sessionRepository: SessionRepository =
        if (supabaseHttp != null && supabaseAuth != null) {
            SessionRepositoryImpl(supabaseHttp, supabaseAuth)
        } else {
            object : SessionRepository {
                override suspend fun getActiveSessions(): Resource<List<UserSession>> = Resource.Success<List<UserSession>>(emptyList())
                override suspend fun revokeSession(sessionId: String) = Resource.Success(Unit)
                override suspend fun revokeOtherSessions() = Resource.Success(Unit)
                override suspend fun registerCurrentDevice(deviceName: String, ipAddress: String?) = Resource.Success(Unit)
            }
        }

    override val accountRepository: AccountRepository =
        if (supabaseHttp != null && supabaseAuth != null) {
            AccountRepositoryImpl(supabaseHttp, supabaseAuth)
        } else {
            object : AccountRepository {
                override suspend fun exportData() =
                    Resource.Failure(FailureReason.NOT_CONFIGURED)
                override suspend fun deactivateAccount() =
                    Resource.Failure(FailureReason.NOT_CONFIGURED)
                override suspend fun deleteAccount() =
                    Resource.Failure(FailureReason.NOT_CONFIGURED)
            }
        }

    override val validateUsername = ValidateUsernameUseCase(userRepository)
    override val createPost = CreatePostUseCase(postRepository)
    override val searchPeople = SearchPeopleUseCase(searchRepository)

    @Volatile
    private var backgroundSyncStarted = false

    override fun startBackgroundSync() {
        if (backgroundSyncStarted) return
        backgroundSyncStarted = true
        authRepositoryImpl.startSessionSync(applicationScope)
        applicationScope.launch {
            gatheringRepository.refresh()
            capsuleRepository.refresh()
            wellbeingRepository.refresh()
        }
    }

    init {
        // Network may already be online when a user signs in. Observe account changes as well
        // as network-restored events so local migration always runs after authentication.
        applicationScope.launch {
            var lastUserId: String? = null
            preferenceStorage.session.collect { state ->
                val userId = state.accountUserId
                if (userId == null) {
                    lastUserId = null
                } else if (userId != lastUserId) {
                    lastUserId = userId
                    launch {
                        gatheringRepository.refresh()
                        capsuleRepository.refresh()
                        wellbeingRepository.refresh()
                    }
                }
            }
        }
    }
}
