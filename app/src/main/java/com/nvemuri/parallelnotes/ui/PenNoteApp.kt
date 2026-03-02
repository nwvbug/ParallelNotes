package com.nvemuri.parallelnotes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

enum class AppScreen {
    HOME,
    NOTE
}
@Composable
fun PenNoteApp() {
    var currentScreen by remember { mutableStateOf(AppScreen.NOTE) }

    when (currentScreen) {
        AppScreen.HOME -> {
            HomeScreen(
                onNavigateToNote = { currentScreen = AppScreen.NOTE }
            )
        }
        AppScreen.NOTE -> {
            NoteTakingScreen(
                onNavigateHome = { currentScreen = AppScreen.HOME }
            )
        }
    }
}
