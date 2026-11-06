package com.nexa.social.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexa.social.R

/**
 * The six primary destinations shown in the Nexa bottom navigation.
 * Create remains available through its existing in-app entry point.
 */
enum class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    HOME(NexaDestinations.HOME, R.string.nav_home, Icons.Filled.Home),
    REELS(NexaDestinations.CLIPS, R.string.nav_reels, Icons.Filled.VideoLibrary),
    GROUPS(NexaDestinations.GROUPS, R.string.nav_groups, Icons.Filled.Groups),
    WORLDS(NexaDestinations.WORLDS, R.string.nav_worlds, Icons.Filled.Public),
    MESSAGES(NexaDestinations.MESSAGES, R.string.nav_messages, Icons.AutoMirrored.Filled.Chat),
    PROFILE(NexaDestinations.PROFILE, R.string.nav_profile, Icons.Filled.Person)
}
