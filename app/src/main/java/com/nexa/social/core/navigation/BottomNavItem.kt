package com.nexa.social.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexa.social.R

enum class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    HOME(NexaDestinations.HOME, R.string.nav_home, Icons.Filled.Home),
    WORLDS(NexaDestinations.WORLDS, R.string.nav_worlds, Icons.Filled.Public),
    CREATE(NexaDestinations.CREATE, R.string.nav_create, Icons.Filled.AddCircle),
    MESSAGES(NexaDestinations.MESSAGES, R.string.nav_messages, Icons.AutoMirrored.Filled.Chat),
    PROFILE(NexaDestinations.PROFILE, R.string.nav_profile, Icons.Filled.Person)
}
