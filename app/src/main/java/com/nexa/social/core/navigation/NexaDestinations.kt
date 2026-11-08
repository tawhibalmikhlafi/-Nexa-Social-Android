package com.nexa.social.core.navigation

/** Central route definitions. No raw route strings should be used elsewhere. */
object NexaDestinations {

    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"

    const val WELCOME = "welcome"
    const val SIGN_IN = "sign_in"
    const val SIGN_UP = "sign_up"
    const val FORGOT_PASSWORD = "forgot_password"
    const val RESET_PASSWORD = "reset_password"
    const val EMAIL_VERIFICATION = "email_verification"
    const val PROFILE_SETUP = "profile_setup"

    const val HOME = "home"
    const val WORLDS = "worlds"
    const val CREATE = "create"
    const val MESSAGES = "messages"
    const val PROFILE = "profile"
    const val PROFILE_DETAILS = "profile_details"
    const val OWNER_DASHBOARD = "owner_dashboard"

    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"
    const val EDIT_PROFILE = "edit_profile"

    const val POST_ARG = "postId"
    const val POST = "post/{$POST_ARG}"
    fun post(id: String) = "post/$id"

    const val SAVED = "saved"

    const val STORY_ARG = "authorId"
    const val STORY_VIEWER = "story/{$STORY_ARG}"
    fun story(authorId: String) = "story/$authorId"
    const val CREATE_STORY = "create_story"

    const val CREATE_WORLD = "create_world"
    const val EDIT_WORLD_ARG = "communityId"
    const val EDIT_WORLD = "edit_world/{$EDIT_WORLD_ARG}"
    fun editWorld(id: String) = "edit_world/$id"

    const val SPACES_ARG = "communityId"
    const val SPACES = "spaces/{$SPACES_ARG}"
    fun spaces(communityId: String) = "spaces/$communityId"

    const val CREATE_SPACE_ARG = "communityId"
    const val CREATE_SPACE = "create_space/{$CREATE_SPACE_ARG}"
    fun createSpace(communityId: String) = "create_space/$communityId"

    const val SPACE_ARG = "spaceId"
    const val SPACE = "space/{$SPACE_ARG}"
    fun space(id: String) = "space/$id"

    const val CLIPS = "clips"
    const val CLIP_ARG = "clipId"
    const val CLIP = "clip/{$CLIP_ARG}"
    fun clip(id: String) = "clip/$id"

    const val SAFETY = "safety"
    const val PRIVACY = "privacy"
    const val ACHIEVEMENTS = "achievements"
    const val AI = "ai_assistant"

    const val LABS = "labs"
    const val PULSE = "pulse"
    const val GATHERINGS = "gatherings"
    const val GATHERING_ARG = "gatheringId"
    const val GATHERING = "gathering/{$GATHERING_ARG}"
    fun gathering(id: String) = "gathering/$id"
    const val CREATE_GATHERING = "create_gathering"
    const val CAPSULES = "capsules"
    const val CREATE_CAPSULE = "create_capsule"
    const val STUDIO = "studio"
    const val WELLBEING = "wellbeing"

    const val COMMUNITY_ARG = "communityId"
    const val COMMUNITY = "community/{$COMMUNITY_ARG}"
    fun community(id: String) = "community/$id"

    const val CONVERSATION_ARG = "conversationId"
    const val CONVERSATION = "conversation/{$CONVERSATION_ARG}"
    fun conversation(id: String) = "conversation/$id"

    const val STARRED_MESSAGES = "starred_messages"
    const val MESSAGE_REQUESTS = "message_requests"

    const val USER_ARG = "userId"
    const val USER = "user/{$USER_ARG}"
    fun user(id: String) = "user/$id"

    const val FOLLOWERS = "followers/{$USER_ARG}"
    fun followers(id: String) = "followers/$id"
    const val FOLLOWING = "following/{$USER_ARG}"
    fun following(id: String) = "following/$id"

    const val FRIENDS = "friends"

    const val GROUPS = "groups"
    const val CREATE_GROUP = "create_group"
    const val GROUP_ARG = "groupId"
    const val GROUP = "group/{$GROUP_ARG}"
    fun group(id: String) = "group/$id"

    const val GROUP_MEMBERS = "group/{$GROUP_ARG}/members"
    fun groupMembers(id: String) = "group/$id/members"

    const val GROUP_SETTINGS = "group/{$GROUP_ARG}/settings"
    fun groupSettings(id: String) = "group/$id/settings"

    const val GROUP_REQUESTS = "group/{$GROUP_ARG}/requests"
    fun groupRequests(id: String) = "group/$id/requests"

    const val GROUP_POST_ARG = "postId"
    const val GROUP_COMMENTS = "group/{$GROUP_ARG}/post/{$GROUP_POST_ARG}/comments"
    fun groupComments(groupId: String, postId: String) = "group/$groupId/post/$postId/comments"
}
