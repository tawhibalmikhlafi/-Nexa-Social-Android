package com.nexa.social.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexa.social.core.auth.AuthCallbackRouter
import com.nexa.social.core.notifications.NotificationRouter
import com.nexa.social.core.navigation.DeepLinkRouter
import com.nexa.social.core.navigation.DeepLinkTarget
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.domain.model.NotificationTargetKind
import com.nexa.social.feature.achievements.AchievementsScreen
import com.nexa.social.feature.ai.AiAssistantScreen
import com.nexa.social.feature.appearance.AppearanceScreen
import com.nexa.social.feature.auth.EmailVerificationScreen
import com.nexa.social.feature.auth.ForgotPasswordScreen
import com.nexa.social.feature.auth.ProfileSetupScreen
import com.nexa.social.feature.auth.ResetPasswordScreen
import com.nexa.social.feature.auth.SignInScreen
import com.nexa.social.feature.auth.SignUpScreen
import com.nexa.social.feature.auth.WelcomeScreen
import com.nexa.social.feature.capsules.CapsulesScreen
import com.nexa.social.feature.capsules.CreateCapsuleScreen
import com.nexa.social.feature.clips.ClipDetailScreen
import com.nexa.social.feature.clips.ClipsScreen
import com.nexa.social.feature.community.CommunityDetailsScreen
import com.nexa.social.feature.create.CreatePostScreen
import com.nexa.social.feature.follow.FollowListMode
import com.nexa.social.feature.follow.FollowListScreen
import com.nexa.social.feature.gatherings.CreateGatheringScreen
import com.nexa.social.feature.gatherings.GatheringDetailScreen
import com.nexa.social.feature.gatherings.GatheringsScreen
import com.nexa.social.feature.groups.CreateGroupScreen
import com.nexa.social.feature.groups.GroupCommentsScreen
import com.nexa.social.feature.groups.GroupDetailScreen
import com.nexa.social.feature.groups.GroupJoinRequestsScreen
import com.nexa.social.feature.groups.GroupMembersScreen
import com.nexa.social.feature.groups.GroupSettingsScreen
import com.nexa.social.feature.groups.GroupsScreen
import com.nexa.social.feature.home.HomeScreen
import com.nexa.social.feature.labs.LabsScreen
import com.nexa.social.feature.messages.ConversationScreen
import com.nexa.social.feature.messages.MessageRequestsScreen
import com.nexa.social.feature.messages.MessagesScreen
import com.nexa.social.feature.messages.MessagesViewModel
import com.nexa.social.feature.messages.StarredMessagesScreen
import com.nexa.social.feature.notifications.NotificationsScreen
import com.nexa.social.feature.onboarding.OnboardingScreen
import com.nexa.social.feature.post.PostDetailScreen
import com.nexa.social.feature.post.SavedPostsScreen
import com.nexa.social.feature.profile.EditProfileScreen
import com.nexa.social.feature.profile.FriendsScreen
import com.nexa.social.feature.profile.ProfileScreen
import com.nexa.social.feature.profile.ProfileDetailsScreen
import com.nexa.social.feature.pulse.PulseScreen
import com.nexa.social.feature.safety.SafetyScreen
import com.nexa.social.feature.settings.privacy.PrivacyScreen
import com.nexa.social.feature.search.SearchScreen
import com.nexa.social.feature.settings.SettingsScreen
import com.nexa.social.feature.spaces.CreateSpaceScreen
import com.nexa.social.feature.spaces.SpaceDetailScreen
import com.nexa.social.feature.spaces.SpacesScreen
import com.nexa.social.feature.stories.CreateStoryScreen
import com.nexa.social.feature.stories.StoryViewerScreen
import com.nexa.social.feature.studio.StudioScreen
import com.nexa.social.feature.wellbeing.WellbeingScreen
import com.nexa.social.feature.worlds.CreateWorldScreen
import com.nexa.social.feature.worlds.WorldsScreen
import com.nexa.social.ui.components.NexaPullToRefresh
import com.nexa.social.ui.components.rememberNexaRefresh

@Composable
fun NexaRoot() {
    val navController = rememberNavController()
    val messagesViewModel: MessagesViewModel = viewModel(factory = MessagesViewModel.Factory)
    val conversations by messagesViewModel.conversations.collectAsStateWithLifecycle()
    val unreadMessagesCount = conversations.sumOf { it.unreadCount }.coerceAtLeast(0)
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = BottomNavItem.entries.any { it.route == currentRoute }
    val notificationTarget by NotificationRouter.target.collectAsStateWithLifecycle()
    val deepLinkTarget by DeepLinkRouter.target.collectAsStateWithLifecycle()

    LaunchedEffect(notificationTarget) {
        val target = notificationTarget ?: return@LaunchedEffect
        NotificationRouter.consume()
        val route = when (target.kind) {
            NotificationTargetKind.POST -> NexaDestinations.post(target.id)
            NotificationTargetKind.USER -> NexaDestinations.user(target.id)
            NotificationTargetKind.COMMUNITY -> NexaDestinations.community(target.id)
            NotificationTargetKind.SPACE -> NexaDestinations.space(target.id)
            NotificationTargetKind.CLIP -> NexaDestinations.clip(target.id)
            NotificationTargetKind.GROUP -> NexaDestinations.group(target.id)
            NotificationTargetKind.NONE -> null
        }
        if (route != null) navController.navigate(route)
    }

    LaunchedEffect(deepLinkTarget, currentRoute) {
        val target = deepLinkTarget ?: return@LaunchedEffect
        // Wait until the splash screen has restored the session; navigating earlier would be
        // popped by enterMain(). Auth callbacks never reach this router.
        if (currentRoute == null || currentRoute == NexaDestinations.SPLASH) return@LaunchedEffect
        DeepLinkRouter.consume()
        val route = when (target.kind) {
            DeepLinkTarget.Kind.POST -> NexaDestinations.post(target.id)
            DeepLinkTarget.Kind.USER -> NexaDestinations.user(target.id)
        }
        navController.navigate(route) { launchSingleTop = true }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1A1030), Color(0xFF080511))
                                ),
                                RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color(0xFF8B35E8).copy(alpha = 0.75f),
                                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
                            )
                    ) {
                        NavigationBar(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp
                        ) {
                            BottomNavItem.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = { navController.navigateTab(item.route) },
                                    icon = {
                                        if (item == BottomNavItem.MESSAGES && unreadMessagesCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge {
                                                        Text(
                                                            text = if (unreadMessagesCount > 99) "99+" else unreadMessagesCount.toString(),
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = stringResource(item.labelRes),
                                                    modifier = Modifier.size(25.dp)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = stringResource(item.labelRes),
                                                modifier = Modifier.size(25.dp)
                                            )
                                        }
                                    },
                                    label = { Text(stringResource(item.labelRes)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = Color(0xFFE4C8FF),
                                        indicatorColor = Color(0xFF6420A8),
                                        unselectedIconColor = Color(0xFFB9A6D8),
                                        unselectedTextColor = Color(0xFFB9A6D8)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            val refresher = rememberNexaRefresh()
            NexaPullToRefresh(
                refreshing = refresher.isRefreshing,
                onRefresh = refresher.refresh,
                modifier = Modifier.padding(padding)
            ) {
            NavHost(
                navController = navController,
                startDestination = NexaDestinations.SPLASH
            ) {
                composable(NexaDestinations.SPLASH) {
                    SplashScreen(
                        onReady = { target ->
                            when (target) {
                                AppStartState.Onboarding -> navController.navigate(NexaDestinations.ONBOARDING) {
                                    popUpTo(NexaDestinations.SPLASH) { inclusive = true }
                                }
                                AppStartState.Auth -> navController.navigate(NexaDestinations.WELCOME) {
                                    popUpTo(NexaDestinations.SPLASH) { inclusive = true }
                                }
                                AppStartState.Main -> {
                                    if (AuthCallbackRouter.passwordResetRequired.value) {
                                        AuthCallbackRouter.consumePasswordReset()
                                        navController.navigate(NexaDestinations.RESET_PASSWORD) {
                                            popUpTo(NexaDestinations.SPLASH) { inclusive = true }
                                        }
                                    } else {
                                        navController.enterMain()
                                    }
                                }
                            }
                        }
                    )
                }

                composable(NexaDestinations.ONBOARDING) {
                    OnboardingScreen(
                        onFinished = {
                            navController.navigate(NexaDestinations.WELCOME) {
                                popUpTo(NexaDestinations.ONBOARDING) { inclusive = true }
                            }
                        }
                    )
                }

                composable(NexaDestinations.WELCOME) {
                    WelcomeScreen(
                        onSignIn = { navController.navigate(NexaDestinations.SIGN_IN) },
                        onSignUp = { navController.navigate(NexaDestinations.SIGN_UP) },
                        onContinue = { navController.enterMain() }
                    )
                }

                composable(NexaDestinations.SIGN_IN) {
                    SignInScreen(
                        onBack = { navController.popBackStack() },
                        onSignedIn = { navController.enterMain() },
                        onSignUp = { navController.navigate(NexaDestinations.SIGN_UP) },
                        onForgotPassword = { navController.navigate(NexaDestinations.FORGOT_PASSWORD) }
                    )
                }

                composable(NexaDestinations.SIGN_UP) {
                    SignUpScreen(
                        onBack = { navController.popBackStack() },
                        onAccountCreated = { navController.navigate(NexaDestinations.EMAIL_VERIFICATION) },
                        onSignedIn = { navController.enterMain() },
                        onSignIn = { navController.navigate(NexaDestinations.SIGN_IN) }
                    )
                }

                composable(NexaDestinations.FORGOT_PASSWORD) {
                    ForgotPasswordScreen(
                        onBack = { navController.popBackStack() },
                        onBackToSignIn = { navController.popBackStack() }
                    )
                }

                composable(NexaDestinations.RESET_PASSWORD) {
                    ResetPasswordScreen(
                        onDone = {
                            navController.navigate(NexaDestinations.SIGN_IN) {
                                popUpTo(NexaDestinations.RESET_PASSWORD) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(NexaDestinations.EMAIL_VERIFICATION) {
                    EmailVerificationScreen(
                        onVerified = { navController.navigate(NexaDestinations.PROFILE_SETUP) },
                        onSkip = { navController.enterMain() },
                        onSignIn = { navController.navigate(NexaDestinations.SIGN_IN) }
                    )
                }

                composable(NexaDestinations.PROFILE_SETUP) {
                    ProfileSetupScreen(onCompleted = { navController.enterMain() })
                }

                composable(NexaDestinations.HOME) {
                    HomeScreen(
                        onSearch = { navController.navigate(NexaDestinations.SEARCH) },
                        onNotifications = { navController.navigate(NexaDestinations.NOTIFICATIONS) },
                        onCreatePost = { navController.navigateTab(NexaDestinations.CREATE) },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) },
                        onOpenPost = { id -> navController.navigate(NexaDestinations.post(id)) },
                        onOpenStory = { id -> navController.navigate(NexaDestinations.story(id)) },
                        onCreateStory = { navController.navigate(NexaDestinations.CREATE_STORY) },
                        onOpenLabs = { navController.navigate(NexaDestinations.LABS) },
                        onOpenGatherings = { navController.navigate(NexaDestinations.GATHERINGS) },
                        onOpenGroups = { navController.navigate(NexaDestinations.GROUPS) }
                    )
                }

                composable(NexaDestinations.WORLDS) {
                    WorldsScreen(
                        onOpenCommunity = { id -> navController.navigate(NexaDestinations.community(id)) },
                        onCreateWorld = { navController.navigate(NexaDestinations.CREATE_WORLD) },
                        onOpenGatherings = { navController.navigate(NexaDestinations.GATHERINGS) }
                    )
                }

                composable(NexaDestinations.CREATE) {
                    CreatePostScreen(onPublished = { navController.navigateTab(NexaDestinations.HOME) })
                }

                composable(NexaDestinations.MESSAGES) {
                    MessagesScreen(
                        onOpenConversation = { id -> navController.navigate(NexaDestinations.conversation(id)) },
                        onOpenProfile = { navController.navigate(NexaDestinations.PROFILE) },
                        onNotifications = { navController.navigate(NexaDestinations.NOTIFICATIONS) },
                        onSearch = { navController.navigate(NexaDestinations.SEARCH) },
                        onOpenGroups = { navController.navigate(NexaDestinations.GROUPS) },
                        onOpenFriends = { navController.navigate(NexaDestinations.MESSAGE_REQUESTS) },
                        onOpenSaved = { navController.navigate(NexaDestinations.STARRED_MESSAGES) },
                        onNewMessage = { navController.navigate(NexaDestinations.SEARCH) }
                    )
                }

                composable(NexaDestinations.PROFILE) {
                    ProfileScreen(
                        targetUserId = null,
                        onBack = null,
                        onEditProfile = { navController.navigate(NexaDestinations.EDIT_PROFILE) },
                        onCreateAccount = { navController.navigate(NexaDestinations.SIGN_UP) },
                        onSettings = { navController.navigate(NexaDestinations.SETTINGS) },
                        onOpenLabs = { navController.navigate(NexaDestinations.LABS) },
                        onOpenStudio = { navController.navigate(NexaDestinations.STUDIO) },
                        onOpenSaved = { navController.navigate(NexaDestinations.SAVED) },
                        onOpenNotifications = { navController.navigate(NexaDestinations.NOTIFICATIONS) },
                        onOpenPost = { id -> navController.navigate(NexaDestinations.post(id)) },
                        onOpenFollowers = { id -> navController.navigate(NexaDestinations.followers(id)) },
                        onOpenFollowing = { id -> navController.navigate(NexaDestinations.following(id)) },
                        onCreateStory = { navController.navigate(NexaDestinations.CREATE_STORY) },
                        onCreatePost = { navController.navigateTab(NexaDestinations.CREATE) },
                        onOpenFriends = { navController.navigate(NexaDestinations.FRIENDS) },
                        onOpenProfileDetails = { navController.navigate(NexaDestinations.PROFILE_DETAILS) }
                    )
                }

                composable(NexaDestinations.PROFILE_DETAILS) {
                    ProfileDetailsScreen(onBack = { navController.popBackStack() })
                }

                composable(NexaDestinations.SEARCH) {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) },
                        onOpenPost = { id -> navController.navigate(NexaDestinations.post(id)) },
                        onOpenWorld = { id -> navController.navigate(NexaDestinations.community(id)) },
                        onOpenSpace = { id -> navController.navigate(NexaDestinations.space(id)) }
                    )
                }

                composable(NexaDestinations.NOTIFICATIONS) {
                    NotificationsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) },
                        onOpen = { notification ->
                            val targetId = notification.targetId
                            if (!targetId.isNullOrBlank()) {
                                when (notification.targetKind) {
                                    NotificationTargetKind.POST -> navController.navigate(NexaDestinations.post(targetId))
                                    NotificationTargetKind.USER -> navController.navigate(NexaDestinations.user(targetId))
                                    NotificationTargetKind.COMMUNITY -> navController.navigate(NexaDestinations.community(targetId))
                                    NotificationTargetKind.SPACE -> navController.navigate(NexaDestinations.space(targetId))
                                    NotificationTargetKind.CLIP -> navController.navigate(NexaDestinations.clip(targetId))
                                    NotificationTargetKind.GROUP -> navController.navigate(NexaDestinations.group(targetId))
                                    NotificationTargetKind.NONE -> Unit
                                }
                            }
                        }
                    )
                }

                composable(NexaDestinations.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onAppearance = { navController.navigate(NexaDestinations.APPEARANCE) },
                        onSignedOut = { navController.enterWelcome() },
                        onSaved = { navController.navigate(NexaDestinations.SAVED) },
                        onClips = { navController.navigate(NexaDestinations.CLIPS) },
                        onAchievements = { navController.navigate(NexaDestinations.ACHIEVEMENTS) },
                        onSafety = { navController.navigate(NexaDestinations.SAFETY) },
                        onPrivacy = { navController.navigate(NexaDestinations.PRIVACY) },
                        onAi = { navController.navigate(NexaDestinations.AI) },
                        onLabs = { navController.navigate(NexaDestinations.LABS) },
                        onEditProfile = { navController.navigate(NexaDestinations.EDIT_PROFILE) },
                        onMessages = { navController.navigate(NexaDestinations.MESSAGES) },
                        onGroups = { navController.navigate(NexaDestinations.GROUPS) },
                        onFriends = { navController.navigate(NexaDestinations.FRIENDS) }
                    )
                }

                composable(NexaDestinations.APPEARANCE) {
                    AppearanceScreen(onBack = { navController.popBackStack() })
                }

                composable(NexaDestinations.EDIT_PROFILE) {
                    EditProfileScreen(onBack = { navController.popBackStack() })
                }

                composable(NexaDestinations.USER) { entry ->
                    val userId = entry.arguments?.getString(NexaDestinations.USER_ARG).orEmpty()
                    ProfileScreen(
                        targetUserId = userId,
                        onBack = { navController.popBackStack() },
                        onEditProfile = { navController.navigate(NexaDestinations.EDIT_PROFILE) },
                        onCreateAccount = { navController.navigate(NexaDestinations.SIGN_UP) },
                        onOpenPost = { id -> navController.navigate(NexaDestinations.post(id)) },
                        onOpenFollowers = { id -> navController.navigate(NexaDestinations.followers(id)) },
                        onOpenFollowing = { id -> navController.navigate(NexaDestinations.following(id)) },
                        onOpenConversation = { id -> navController.navigate(NexaDestinations.conversation(id)) },
                        onCreateStory = { navController.navigate(NexaDestinations.CREATE_STORY) },
                        onCreatePost = { navController.navigateTab(NexaDestinations.CREATE) },
                        onOpenFriends = { navController.navigate(NexaDestinations.FRIENDS) }
                    )
                }

                composable(NexaDestinations.FRIENDS) {
                    FriendsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) }
                    )
                }

                composable(NexaDestinations.GROUPS) {
                    GroupsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenGroup = { id -> navController.navigate(NexaDestinations.group(id)) },
                        onCreateGroup = { navController.navigate(NexaDestinations.CREATE_GROUP) }
                    )
                }

                composable(NexaDestinations.CREATE_GROUP) {
                    CreateGroupScreen(
                        onBack = { navController.popBackStack() },
                        onCreated = { id ->
                            navController.navigate(NexaDestinations.group(id)) {
                                popUpTo(NexaDestinations.CREATE_GROUP) { inclusive = true }
                            }
                        }
                    )
                }

                composable(
                    route = NexaDestinations.GROUP,
                    arguments = listOf(navArgument(NexaDestinations.GROUP_ARG) { type = NavType.StringType })
                ) { entry ->
                    val groupId = entry.arguments?.getString(NexaDestinations.GROUP_ARG).orEmpty()
                    GroupDetailScreen(
                        groupId = groupId,
                        onBack = { navController.popBackStack() },
                        onOpenMembers = { id -> navController.navigate(NexaDestinations.groupMembers(id)) },
                        onOpenSettings = { id -> navController.navigate(NexaDestinations.groupSettings(id)) },
                        onOpenRequests = { id -> navController.navigate(NexaDestinations.groupRequests(id)) },
                        onOpenComments = { id, postId ->
                            navController.navigate(NexaDestinations.groupComments(id, postId))
                        },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) }
                    )
                }

                composable(
                    route = NexaDestinations.GROUP_MEMBERS,
                    arguments = listOf(navArgument(NexaDestinations.GROUP_ARG) { type = NavType.StringType })
                ) { entry ->
                    val groupId = entry.arguments?.getString(NexaDestinations.GROUP_ARG).orEmpty()
                    GroupMembersScreen(
                        groupId = groupId,
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) }
                    )
                }

                composable(
                    route = NexaDestinations.GROUP_SETTINGS,
                    arguments = listOf(navArgument(NexaDestinations.GROUP_ARG) { type = NavType.StringType })
                ) { entry ->
                    val groupId = entry.arguments?.getString(NexaDestinations.GROUP_ARG).orEmpty()
                    GroupSettingsScreen(
                        groupId = groupId,
                        onBack = { navController.popBackStack() },
                        onDeleted = {
                            if (!navController.popBackStack(NexaDestinations.GROUPS, false)) {
                                navController.navigate(NexaDestinations.GROUPS)
                            }
                        },
                        onLeft = {
                            if (!navController.popBackStack(NexaDestinations.GROUPS, false)) {
                                navController.navigate(NexaDestinations.GROUPS)
                            }
                        }
                    )
                }

                composable(
                    route = NexaDestinations.GROUP_REQUESTS,
                    arguments = listOf(navArgument(NexaDestinations.GROUP_ARG) { type = NavType.StringType })
                ) { entry ->
                    val groupId = entry.arguments?.getString(NexaDestinations.GROUP_ARG).orEmpty()
                    GroupJoinRequestsScreen(
                        groupId = groupId,
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) }
                    )
                }

                composable(
                    route = NexaDestinations.GROUP_COMMENTS,
                    arguments = listOf(
                        navArgument(NexaDestinations.GROUP_ARG) { type = NavType.StringType },
                        navArgument(NexaDestinations.GROUP_POST_ARG) { type = NavType.StringType }
                    )
                ) { entry ->
                    val groupId = entry.arguments?.getString(NexaDestinations.GROUP_ARG).orEmpty()
                    val postId = entry.arguments?.getString(NexaDestinations.GROUP_POST_ARG).orEmpty()
                    GroupCommentsScreen(
                        groupId = groupId,
                        postId = postId,
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) }
                    )
                }

                composable(
                    route = NexaDestinations.FOLLOWERS,
                    arguments = listOf(navArgument(NexaDestinations.USER_ARG) { type = NavType.StringType })
                ) { entry ->
                    val userId = entry.arguments?.getString(NexaDestinations.USER_ARG).orEmpty()
                    FollowListScreen(
                        targetUserId = userId,
                        mode = FollowListMode.FOLLOWERS,
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) }
                    )
                }

                composable(
                    route = NexaDestinations.FOLLOWING,
                    arguments = listOf(navArgument(NexaDestinations.USER_ARG) { type = NavType.StringType })
                ) { entry ->
                    val userId = entry.arguments?.getString(NexaDestinations.USER_ARG).orEmpty()
                    FollowListScreen(
                        targetUserId = userId,
                        mode = FollowListMode.FOLLOWING,
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) }
                    )
                }

                composable(
                    route = NexaDestinations.COMMUNITY,
                    arguments = listOf(navArgument(NexaDestinations.COMMUNITY_ARG) { type = NavType.StringType })
                ) { entry ->
                    val communityId = entry.arguments?.getString(NexaDestinations.COMMUNITY_ARG).orEmpty()
                    CommunityDetailsScreen(
                        communityId = communityId,
                        onBack = { navController.popBackStack() },
                        onOpenSpaces = { id -> navController.navigate(NexaDestinations.spaces(id)) },
                        onEditWorld = { id -> navController.navigate(NexaDestinations.editWorld(id)) },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) }
                    )
                }

                composable(
                    route = NexaDestinations.POST,
                    arguments = listOf(navArgument(NexaDestinations.POST_ARG) { type = NavType.StringType })
                ) { entry ->
                    val postId = entry.arguments?.getString(NexaDestinations.POST_ARG).orEmpty()
                    PostDetailScreen(
                        postId = postId,
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) }
                    )
                }

                composable(NexaDestinations.SAVED) {
                    SavedPostsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPost = { id -> navController.navigate(NexaDestinations.post(id)) },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) }
                    )
                }

                composable(
                    route = NexaDestinations.STORY_VIEWER,
                    arguments = listOf(navArgument(NexaDestinations.STORY_ARG) { type = NavType.StringType })
                ) { entry ->
                    val authorId = entry.arguments?.getString(NexaDestinations.STORY_ARG).orEmpty()
                    StoryViewerScreen(authorId = authorId, onBack = { navController.popBackStack() })
                }

                composable(NexaDestinations.CREATE_STORY) {
                    CreateStoryScreen(
                        onBack = { navController.popBackStack() },
                        onCreated = { navController.popBackStack() }
                    )
                }

                composable(NexaDestinations.CREATE_WORLD) {
                    CreateWorldScreen(
                        communityId = null,
                        onBack = { navController.popBackStack() },
                        onSaved = { id -> navController.navigate(NexaDestinations.community(id)) {
                            popUpTo(NexaDestinations.CREATE_WORLD) { inclusive = true }
                        } }
                    )
                }

                composable(
                    route = NexaDestinations.EDIT_WORLD,
                    arguments = listOf(navArgument(NexaDestinations.EDIT_WORLD_ARG) { type = NavType.StringType })
                ) { entry ->
                    val communityId = entry.arguments?.getString(NexaDestinations.EDIT_WORLD_ARG).orEmpty()
                    CreateWorldScreen(
                        communityId = communityId,
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() }
                    )
                }

                composable(
                    route = NexaDestinations.SPACES,
                    arguments = listOf(navArgument(NexaDestinations.SPACES_ARG) { type = NavType.StringType })
                ) { entry ->
                    val communityId = entry.arguments?.getString(NexaDestinations.SPACES_ARG).orEmpty()
                    SpacesScreen(
                        communityId = communityId,
                        onBack = { navController.popBackStack() },
                        onOpenSpace = { id -> navController.navigate(NexaDestinations.space(id)) },
                        onCreateSpace = { navController.navigate(NexaDestinations.createSpace(communityId)) }
                    )
                }

                composable(
                    route = NexaDestinations.CREATE_SPACE,
                    arguments = listOf(navArgument(NexaDestinations.CREATE_SPACE_ARG) { type = NavType.StringType })
                ) { entry ->
                    val communityId = entry.arguments?.getString(NexaDestinations.CREATE_SPACE_ARG).orEmpty()
                    CreateSpaceScreen(
                        communityId = communityId,
                        onBack = { navController.popBackStack() },
                        onCreated = { id -> navController.navigate(NexaDestinations.space(id)) {
                            popUpTo(NexaDestinations.CREATE_SPACE) { inclusive = true }
                        } }
                    )
                }

                composable(
                    route = NexaDestinations.SPACE,
                    arguments = listOf(navArgument(NexaDestinations.SPACE_ARG) { type = NavType.StringType })
                ) { entry ->
                    val spaceId = entry.arguments?.getString(NexaDestinations.SPACE_ARG).orEmpty()
                    SpaceDetailScreen(spaceId = spaceId, onBack = { navController.popBackStack() })
                }

                composable(NexaDestinations.CLIPS) {
                    ClipsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenClip = { id -> navController.navigate(NexaDestinations.clip(id)) }
                    )
                }

                composable(
                    route = NexaDestinations.CLIP,
                    arguments = listOf(navArgument(NexaDestinations.CLIP_ARG) { type = NavType.StringType })
                ) { entry ->
                    val clipId = entry.arguments?.getString(NexaDestinations.CLIP_ARG).orEmpty()
                    ClipDetailScreen(clipId = clipId, onBack = { navController.popBackStack() })
                }

                composable(NexaDestinations.SAFETY) {
                    SafetyScreen(onBack = { navController.popBackStack() })
                }

                composable(NexaDestinations.PRIVACY) {
                    PrivacyScreen(
                        onBack = { navController.popBackStack() },
                        onBlockedAccounts = { navController.navigate(NexaDestinations.SAFETY) },
                        onAccountDeleted = { navController.enterWelcome() }
                    )
                }

                composable(NexaDestinations.ACHIEVEMENTS) {
                    AchievementsScreen(onBack = { navController.popBackStack() })
                }

                composable(NexaDestinations.AI) {
                    AiAssistantScreen(onBack = { navController.popBackStack() })
                }

                composable(NexaDestinations.LABS) {
                    LabsScreen(
                        onBack = { navController.popBackStack() },
                        onPulse = { navController.navigate(NexaDestinations.PULSE) },
                        onGatherings = { navController.navigate(NexaDestinations.GATHERINGS) },
                        onCapsules = { navController.navigate(NexaDestinations.CAPSULES) },
                        onStudio = { navController.navigate(NexaDestinations.STUDIO) },
                        onWellbeing = { navController.navigate(NexaDestinations.WELLBEING) }
                    )
                }

                composable(NexaDestinations.PULSE) {
                    PulseScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id -> navController.navigate(NexaDestinations.user(id)) },
                        onOpenWorld = { id -> navController.navigate(NexaDestinations.community(id)) }
                    )
                }

                composable(NexaDestinations.GATHERINGS) {
                    GatheringsScreen(
                        onBack = { navController.popBackStack() },
                        onOpen = { id -> navController.navigate(NexaDestinations.gathering(id)) },
                        onCreate = { navController.navigate(NexaDestinations.CREATE_GATHERING) }
                    )
                }

                composable(NexaDestinations.CREATE_GATHERING) {
                    CreateGatheringScreen(
                        onBack = { navController.popBackStack() },
                        onCreated = { id ->
                            navController.navigate(NexaDestinations.gathering(id)) {
                                popUpTo(NexaDestinations.CREATE_GATHERING) { inclusive = true }
                            }
                        }
                    )
                }

                composable(
                    route = NexaDestinations.GATHERING,
                    arguments = listOf(
                        navArgument(NexaDestinations.GATHERING_ARG) { type = NavType.StringType }
                    )
                ) { entry ->
                    val gatheringId = entry.arguments
                        ?.getString(NexaDestinations.GATHERING_ARG)
                        .orEmpty()
                    GatheringDetailScreen(
                        gatheringId = gatheringId,
                        onBack = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() }
                    )
                }

                composable(NexaDestinations.CAPSULES) {
                    CapsulesScreen(
                        onBack = { navController.popBackStack() },
                        onCreate = { navController.navigate(NexaDestinations.CREATE_CAPSULE) }
                    )
                }

                composable(NexaDestinations.CREATE_CAPSULE) {
                    CreateCapsuleScreen(
                        onBack = { navController.popBackStack() },
                        onCreated = { navController.popBackStack() }
                    )
                }

                composable(NexaDestinations.STUDIO) {
                    StudioScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPost = { id -> navController.navigate(NexaDestinations.post(id)) }
                    )
                }

                composable(NexaDestinations.WELLBEING) {
                    WellbeingScreen(onBack = { navController.popBackStack() })
                }

                composable(NexaDestinations.STARRED_MESSAGES) {
                    StarredMessagesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenConversation = { id -> navController.navigate(NexaDestinations.conversation(id)) }
                    )
                }

                composable(NexaDestinations.MESSAGE_REQUESTS) {
                    MessageRequestsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenConversation = { id -> navController.navigate(NexaDestinations.conversation(id)) }
                    )
                }

                composable(
                    route = NexaDestinations.CONVERSATION,
                    arguments = listOf(navArgument(NexaDestinations.CONVERSATION_ARG) { type = NavType.StringType })
                ) { entry ->
                    val conversationId = entry.arguments?.getString(NexaDestinations.CONVERSATION_ARG).orEmpty()
                    ConversationScreen(
                        conversationId = conversationId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            }
        }
    }
}

private fun NavHostController.enterMain() {
    navigate(NexaDestinations.HOME) {
        popUpTo(graph.id) { inclusive = true }
    }
}

private fun NavHostController.enterWelcome() {
    navigate(NexaDestinations.WELCOME) {
        popUpTo(graph.id) { inclusive = true }
    }
}

private fun NavHostController.navigateTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        // Save each tab's own back stack from the graph's real start destination.
        // Using a hard-coded HOME route can restore an unrelated saved destination.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
