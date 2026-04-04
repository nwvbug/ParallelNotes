package com.nvemuri.parallelnotes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nvemuri.parallelnotes.R
import com.nvemuri.parallelnotes.data.NoteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: NoteViewModel,
    onNavigateToNote: () -> Unit
) {
    // Observe database state
    val notes by viewModel.allNotes.collectAsState()
    val folders by viewModel.allFolders.collectAsState()

    var selectedFolder by remember { mutableStateOf("MyFolder") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMoveNoteDialog by remember { mutableStateOf<NoteEntity?>(null) }
    
    // Track folders locally so new ones show up immediately before they have notes
    var localFolders by remember(folders) { mutableStateOf(folders) }
    
    val displayFolders = (localFolders + folders).distinct()

    // Filter notes based on selection
    val filteredNotes = notes.filter { it.folder == selectedFolder }

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
                    shape = RoundedCornerShape(50), 
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
                    viewModel.createNewNote(selectedFolder) // Create note in selected folder
                    onNavigateToNote()
                },
                containerColor = Color(0xFF02004A),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.border(3.dp, Color.White, RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Note")
            }
        }
    ) { paddingValues ->
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
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White, shape = RoundedCornerShape(16.dp))
                    .border(width = 3.dp, color = Color.Black, shape = RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                item {
                    IconButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier.size(48.dp),
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
                
                // Use dynamic folders from Room + local session folders
                items(items = displayFolders, key = { it }) { folder ->
                    Folder(
                        folderName = folder,
                        isSelected = selectedFolder == folder,
                        onClick = { selectedFolder = folder }
                    )
                }
            }
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredNotes, key = { it.noteId }) { note ->
                    NoteCard(
                        title = note.title,
                        timestamp = note.lastModified,
                        onClick = {
                            viewModel.loadNote(note.noteId)
                            onNavigateToNote()
                        },
                        onLongClick = {
                            showMoveNoteDialog = note
                        }
                    )
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { newFolderName ->
                if (newFolderName.isNotBlank()) {
                    if (!localFolders.contains(newFolderName)) {
                        localFolders = localFolders + newFolderName
                    }
                    selectedFolder = newFolderName
                }
            }
        )
    }

    if (showMoveNoteDialog != null) {
        MoveNoteDialog(
            noteTitle = showMoveNoteDialog?.title ?: "Untitled Note",
            folders = displayFolders,
            onDismiss = { showMoveNoteDialog = null },
            onConfirm = { targetFolder ->
                showMoveNoteDialog?.let { note ->
                    viewModel.moveNoteToFolder(note.noteId, targetFolder)
                }
                showMoveNoteDialog = null
            }
        )
    }
}

@Composable
fun MoveNoteDialog(
    noteTitle: String,
    folders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move '${if(noteTitle.isBlank()) "Untitled Note" else noteTitle}' to...") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                folders.forEach { folder ->
                    TextButton(
                        onClick = { onConfirm(folder) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(folder, color = Color.Black, textAlign = TextAlign.Center)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Folder Name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    focusedLabelColor = Color.Black
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(text)
                    onDismiss()
                }
            ) { Text("Create", color = Color.Black, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
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
            contentAlignment = Alignment.Center
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    title: String, 
    timestamp: Long, 
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateString = formatter.format(Date(timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
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
