package com.nvemuri.parallelnotes.ui
import android.view.Window
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nvemuri.parallelnotes.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NoteViewModel,
    onNavigateToNote: () -> Unit
) {
    // 1. Observe the database list
    val notes by viewModel.allNotes.collectAsState()

    // Inside HomeScreen
    var selectedFolder by remember { mutableStateOf("physics 33") } // Default selection

    Scaffold(
        containerColor = Color(0xFFebebeb),
        topBar = {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(50), // Makes it a pill shape
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(3.dp, Color.Black)

                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.parallelnotesiconblack),
                            contentDescription = "Go to Homepage",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(35.dp)
                                .clickable { /* Handle click */ }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.createNewNote()
                    onNavigateToNote()
                },
                containerColor = Color(0xFF02004A),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                // To match the aesthetic, you can add a border here too
                modifier = Modifier.border(3.dp, Color.White, RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Note")
            }
        }
    ) { paddingValues ->
        // 3. Draw the grid of notes

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues)
                .padding(top = 16.dp, start = 16.dp, end = 8.dp)
        ){
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()

                    .width(250.dp)
                    // 1. Shadow comes first (so it spreads outside the shape)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    // 2. Background color
                    .background(Color.White, shape = RoundedCornerShape(16.dp))
                    // 3. Border
                    .border(width = 3.dp, color = Color.Black, shape = RoundedCornerShape(16.dp))
                    .padding(16.dp),

                horizontalAlignment = Alignment.CenterHorizontally


            ){
                item {
                    IconButton(
                        onClick = { /*TODO*/ },
                        modifier = Modifier
                            .size(48.dp),
                        content = {
                            Icon(
                                painter=painterResource(id = R.drawable.plus),
                                contentDescription = "New Folder",
                                tint = Color.Unspecified,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    )
                }
                items(items=listOf<String>("physics 33", "ecen 50", "class"), key={it}){folder: String->
                    Folder(folderName = folder,
                        isSelected = selectedFolder == folder,
                        onClick = {selectedFolder = folder})
                }

            }
            //actual notes grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues( bottom = 88.dp), // Extra bottom padding for the FAB
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.noteId }) { note ->
                    NoteCard(
                        title = note.title,
                        timestamp = note.lastModified,
                        onClick = {
                            // 4. Trigger the background loading process, then navigate
                            viewModel.loadNote(note.noteId)
                            onNavigateToNote()
                        }
                    )
                }
            }
        }
        }


}

@Composable
fun Folder(folderName: String, isSelected : Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = if (isSelected) BorderStroke(3.dp, Color.Black) else null
        ){
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center // This centers both Vertically and Horizontally
        ) {
            Text(
                text = folderName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = Color.Black
            )
        }
    }
}

@Composable
fun NoteCard(title: String, timestamp: Long, onClick: () -> Unit) {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateString = formatter.format(Date(timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(3.dp, Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (title.isNotBlank()) title else "Untitled Note",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateString,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}