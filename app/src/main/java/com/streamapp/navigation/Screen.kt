package com.streamapp.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Studio : Screen(
        route = "studio",
        title = "Studio",
        selectedIcon = Icons.Filled.Camera,
        unselectedIcon = Icons.Outlined.Camera
    )

    data object GameStream : Screen(
        route = "gamestream",
        title = "Game",
        selectedIcon = Icons.Filled.Gamepad,
        unselectedIcon = Icons.Outlined.Gamepad
    )

    data object Soundbar : Screen(
        route = "soundbar",
        title = "Audio",
        selectedIcon = Icons.Filled.Equalizer,
        unselectedIcon = Icons.Outlined.Equalizer
    )

    data object Destinations : Screen(
        route = "destinations",
        title = "Platforms",
        selectedIcon = Icons.Filled.Public,
        unselectedIcon = Icons.Outlined.Public
    )

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val bottomNavItems: List<Screen>
            get() = listOf(Studio, GameStream, Soundbar, Destinations, Settings)
    }
}
