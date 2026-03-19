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
fun PenNoteApp(viewModel: NoteViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

    androidx.compose.animation.Crossfade(targetState = currentScreen, label = "Screen Transition") { screen ->
        when (screen) {
            AppScreen.HOME -> {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToNote = { currentScreen = AppScreen.NOTE }
                )
            }
            AppScreen.NOTE -> {
                NoteTakingScreen(
                    viewModel = viewModel, // Make sure your NoteTakingScreen accepts the ViewModel!
                    onNavigateHome = {
                        // Trigger a save right before we leave the canvas!
                        // (You can replace "My Note" with a real title state later)
                        val actualTitle = viewModel.currentNoteTitle.value
                        viewModel.saveNote(actualTitle, viewModel.currentElements.value)

                        currentScreen = AppScreen.HOME
                    }
                )
            }
        }
    }
}
