package com.nvemuri.parallelnotes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.nvemuri.parallelnotes.data.entities.ImportantStrokeEntity
import com.nvemuri.parallelnotes.data.toCanvasElement
import com.nvemuri.parallelnotes.utils.drawStroke
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: NoteViewModel,
    onNavigateToNote: () -> Unit
) {
    // Observe database state
    val notes by viewModel.allNotes.collectAsState()
    val folders by viewModel.allFolders.collectAsState()
    val importantStrokes by viewModel.importantStrokes.collectAsState()
    val selectedFolder by viewModel.currentFolder.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var noteToManage by remember { mutableStateOf<NoteEntity?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var strokeToDelete by remember { mutableStateOf<ImportantStrokeEntity?>(null) }
    
    // Track folders locally so new ones show up immediately before they have notes
    var localFolders by remember(folders) { mutableStateOf(folders) }
    
    val displayFolders = (localFolders + folders).distinct()

    // Filter notes based on selection
    val filteredNotes = notes.filter { it.folder == selectedFolder }

    // Group important strokes by category
    val groupedStrokes = importantStrokes.groupBy { it.categoryName }

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
                        onClick = { 
                            viewModel.updateFolder(folder)
                        }
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                // Important Strokes Pane - Horizontal Category Panels
                if (importantStrokes.isNotEmpty()) {
                    Text(
                        "Important Summaries",
                        fontWeight = FontWeight.Bold, 
                        modifier = Modifier.padding(8.dp)
                    )
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        // Put "Important" first, then sort the rest alphabetically
                        val sortedCategories = groupedStrokes.keys.sortedByDescending { it == "Important" }
                        
                        items(sortedCategories) { categoryName ->
                            val strokesInCategory = groupedStrokes[categoryName] ?: emptyList()
                            val firstStroke = strokesInCategory.firstOrNull()
                            val categoryColor = if (firstStroke != null) Color(firstStroke.colorArgb) else Color.Black

                            Column(
                                modifier = Modifier
                                    .width(300.dp)
                                    .fillMaxHeight()
                                    .border(3.dp, categoryColor, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = categoryName,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = categoryColor,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                                )
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 8.dp)
                                ) {
                                    items(strokesInCategory) { stroke ->
                                        ImportantStrokeCard(
                                            stroke = stroke,
                                            onClick = {
                                                viewModel.loadNote(stroke.noteId)
                                                onNavigateToNote()
                                            },
                                            onLongClick = {
                                                strokeToDelete = stroke
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier
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
                            borderColor = Color(note.colorArgb),
                            onClick = {
                                viewModel.loadNote(note.noteId)
                                onNavigateToNote()
                            },
                            onLongClick = {
                                noteToManage = note
                            }
                        )
                    }
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
                    viewModel.updateFolder(newFolderName)
                }
            }
        )
    }

    if (noteToManage != null) {
        NoteOptionsDialog(
            note = noteToManage!!,
            folders = displayFolders,
            onDismiss = { noteToManage = null },
            onMove = { targetFolder ->
                noteToManage?.let { note ->
                    viewModel.moveNoteToFolder(note.noteId, targetFolder)
                }
                noteToManage = null
            },
            onDelete = {
                noteToDelete = noteToManage
                noteToManage = null
            },
            onColorChange = { colorArgb ->
                noteToManage?.let { note ->
                    viewModel.updateNoteColor(note.noteId, colorArgb)
                }
            }
        )
    }

    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete Note?") },
            text = { Text("Are you sure you want to delete '${if(noteToDelete?.title?.isBlank() == true) "Untitled Note" else noteToDelete?.title}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        noteToDelete?.let { viewModel.deleteNote(it) }
                        noteToDelete = null
                    }
                ) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel", color = Color.Black)
                }
            }
        )
    }

    if (strokeToDelete != null) {
        AlertDialog(
            onDismissRequest = { strokeToDelete = null },
            title = { Text("Delete Important Writing?") },
            text = { Text("This will remove the writing from the summary pane, but NOT from the original note.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        strokeToDelete?.let { viewModel.deleteImportantStroke(it) }
                        strokeToDelete = null
                    }
                ) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { strokeToDelete = null }) {
                    Text("Cancel", color = Color.Black)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImportantStrokeCard(
    stroke: ImportantStrokeEntity, 
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(2.dp, Color(stroke.colorArgb)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Render the strokes in a small canvas
            Box(modifier = Modifier.size(100.dp, 40.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val elements = stroke.serializedElements.map { it.toCanvasElement() }
                    val padding = 10f
                    val scaleX = (size.width - padding * 2) / (stroke.maxX - stroke.minX).coerceAtLeast(1f)
                    val scaleY = (size.height - padding * 2) / (stroke.maxY - stroke.minY).coerceAtLeast(1f)
                    val scale = minOf(scaleX, scaleY)

                    withTransform({
                        translate(padding, padding)
                        scale(scale, scale, Offset.Zero)
                        translate(-stroke.minX, -stroke.minY)
                    }) {
                        elements.forEach { element ->
                            if (element is com.nvemuri.parallelnotes.data.entities.PenStroke) {
                                drawStroke(element, element.thickness)
                            }
                        }
                    }
                }
            }
            Text(
                text = stroke.noteTitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun NoteOptionsDialog(
    note: NoteEntity,
    folders: List<String>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
    onDelete: () -> Unit,
    onColorChange: (Int) -> Unit
) {
    val noteTitle = if (note.title.isBlank()) "Untitled Note" else note.title
    
    val colorPalette = listOf(
        0xFF000000.toInt(), // Black
        0xFFE53935.toInt(), // Red
        0xFFFF9800.toInt(), // Orange
        0xFFFFEB3B.toInt(), // Yellow
        0xFF4CAF50.toInt(), // Green
        0xFF2196F3.toInt(), // Blue
        0xFF9C27B0.toInt(), // Purple
        0xFFE91E63.toInt(), // Pink
        0xFF795548.toInt(), // Brown
        0xFF607D8B.toInt(), // Blue Grey
        0xFF009688.toInt(), // Teal
        0xFF3F51B5.toInt()  // Indigo
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(noteTitle) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Border Color", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    colorPalette.chunked(6).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowColors.forEach { colorInt ->
                                val isSelected = note.colorArgb == colorInt
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(colorInt))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color.Blue else Color.Gray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onColorChange(colorInt) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                Text("Move to...", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                if (folders.filter { it != note.folder }.isEmpty()) {
                    Text("No other folders", color = Color.Gray, modifier = Modifier.padding(8.dp))
                } else {
                    folders.filter { it != note.folder }.forEach { folder ->
                        TextButton(
                            onClick = { onMove(folder) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(folder, color = Color.Black, textAlign = TextAlign.Center)
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Note")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color.Gray) }
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
    borderColor: Color,
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
        border = BorderStroke(3.dp, borderColor)
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
