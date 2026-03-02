package com.nvemuri.parallelnotes.ui
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


@Composable
fun HomeScreen(onNavigateToNote: () -> Unit) {
    // A simple placeholder UI
    Surface(modifier = Modifier.fillMaxSize(), color = Color.LightGray) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Your Folders & Notes", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = { onNavigateToNote() }) {
                Text("Open Existing Note")
            }
        }
    }
}