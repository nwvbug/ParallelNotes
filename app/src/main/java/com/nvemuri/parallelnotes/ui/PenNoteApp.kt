package com.nvemuri.parallelnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppScreen {
    HOME,
    NOTE
}

@Composable
fun PenNoteApp(viewModel: NoteViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    val isLoading by viewModel.isLoading.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
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
                        viewModel = viewModel,
                        onNavigateHome = {
                            val actualTitle = viewModel.currentNoteTitle.value
                            viewModel.saveNote(actualTitle, viewModel.currentElements.value)
                            currentScreen = AppScreen.HOME
                        }
                    )
                }
            }
        }

        if (isLoading) {
            // Full screen overlay to block interactions while loading
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = Color.White,
                    strokeWidth = 6.dp
                )
            }
        }
    }
}
