package com.nvemuri.parallelnotes.ui

import androidx.compose.animation.*
import kotlinx.coroutines.delay
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.padding
import kotlin.math.sqrt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

import androidx.activity.compose.BackHandler
import com.nvemuri.parallelnotes.R
import com.nvemuri.parallelnotes.data.entities.ImageElement
import com.nvemuri.parallelnotes.data.entities.PenStroke
import com.nvemuri.parallelnotes.data.entities.Point
import com.nvemuri.parallelnotes.utils.bezierSmoothStroke
import com.nvemuri.parallelnotes.utils.createImagePicture
import com.nvemuri.parallelnotes.utils.drawStroke
import com.nvemuri.parallelnotes.utils.isPointInPolygon
import com.nvemuri.parallelnotes.data.entities.CanvasAction
import com.nvemuri.parallelnotes.data.entities.CanvasElement
import com.nvemuri.parallelnotes.data.entities.PenStyle
import com.nvemuri.parallelnotes.utils.getOverlappingChunkKeys
import com.nvemuri.parallelnotes.data.CanvasChunk
import com.nvemuri.parallelnotes.utils.detectMultiFingerTap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

import android.graphics.Picture
import android.graphics.Paint as NativePaint
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint.Cap
import android.graphics.Paint.Join
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import com.nvemuri.parallelnotes.data.AppDatabase
import java.util.UUID

import android.content.Intent
import androidx.compose.foundation.verticalScroll
import androidx.core.content.FileProvider
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.nvemuri.parallelnotes.data.entities.ImportantCategoryEntity
import com.nvemuri.parallelnotes.data.entities.toSerializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize the database
        val database = AppDatabase.getDatabase(applicationContext)
        val noteDao = database.noteDao()
        val importantStrokeDao = database.importantStrokeDao()
        val importantCategoryDao = database.importantCategoryDao()

        val viewModelFactory = NoteViewModelFactory(applicationContext, noteDao, importantStrokeDao, importantCategoryDao)

        setContent {
            // Grab the ViewModel
            val viewModel: NoteViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = viewModelFactory
            )

            // Pass it to your app
            PenNoteApp(viewModel)
        }
    }
}


@Composable
fun NoteTakingScreen(viewModel: NoteViewModel, onNavigateHome: () -> Unit){
    BackHandler { onNavigateHome() }

    var currentTool by remember { mutableStateOf(ActiveTool.DRAW)}

    //pen settings
    var isPenMenuOpen by remember { mutableStateOf(false)}
    var currentPenStyle by remember { mutableStateOf(PenStyle.SOLID) }
    // Increased default thickness since we're starting zoomed out
    var penThickness by remember { mutableFloatStateOf(35f) }
    var solidColor by remember { mutableStateOf(Color.Black) }
    var dashedColor by remember { mutableStateOf(Color.Black) }
    var highlighterColor by remember { mutableStateOf(Color.Yellow) }
    val penColor = when (currentPenStyle) {
        PenStyle.SOLID -> solidColor
        PenStyle.DASHED -> dashedColor
        PenStyle.HIGHLIGHTER -> highlighterColor
    }
    val onPenColorSelected: (Color) -> Unit = { color ->
        when (currentPenStyle) {
            PenStyle.SOLID -> solidColor = color
            PenStyle.DASHED -> dashedColor = color
            PenStyle.HIGHLIGHTER -> highlighterColor = color
        }
    }
    var isColorSelectorOpen by remember { mutableStateOf(false)}
    var arcSmoothingEnabled by remember { mutableStateOf(true) }
    var removeJitterAmount by remember { mutableFloatStateOf(25f) }
    var smoothCurrentStroke by remember { mutableStateOf(true) }
    //name states
    val noteTitle by viewModel.currentNoteTitle.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }

    // More Options Menu
    var isMoreMenuOpen by remember { mutableStateOf(false) }
    var showColorToolbar by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Important Pen Menu
    var isImportantPenMenuOpen by remember { mutableStateOf(false) }
    val selectedImportantCategory by viewModel.selectedImportantCategory.collectAsState()

    // Image insertion trigger
    var shouldOpenImagePicker by remember { mutableStateOf(false) }

    // Text Box insertion trigger
    var shouldAddTextBox by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()){
        DrawingCanvas(
            currentTool, penThickness, penColor, currentPenStyle, arcSmoothingEnabled, smoothCurrentStroke,
            removeJitterAmount, viewModel,
            shouldOpenImagePicker = shouldOpenImagePicker,
            onImagePickerConsumed = { shouldOpenImagePicker = false },
            shouldAddTextBox = shouldAddTextBox,
            onTextBoxConsumed = { shouldAddTextBox = false }
        )

        Column (
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        )
        {

            if (showColorToolbar) {
                ColorToolbar(
                    currentColor = penColor,
                    onColorSelected = onPenColorSelected,
                    modifier = Modifier
//                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 16.dp)
                )
            }
            Surface(
                modifier = Modifier
//                    .align(Alignment.CenterStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(3.dp, Color.Black)

            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ){
                    val isDraw = currentTool == ActiveTool.DRAW
                    IconButton(onClick = {
                        if (currentTool == ActiveTool.DRAW){
                            isPenMenuOpen = !isPenMenuOpen
                        } else {
                            currentTool = ActiveTool.DRAW
                            isPenMenuOpen = false
                        }
                    },
                        modifier = Modifier.border(
                            width = if (isDraw) 3.dp else 0.dp,
                            color = if (isDraw) Color.Black else Color.Transparent,
                            shape = CircleShape
                        )
                    ) {
                        var tint = penColor
                        Icon(
                            painter = painterResource(id = R.drawable.draw),
                            contentDescription = "Draw Tool",
                            tint = tint,
                            modifier = Modifier.padding(6.dp)
                        )
                    }

                    val isImportant = currentTool == ActiveTool.IMPORTANT_PEN
                    IconButton(onClick = {
                        if (currentTool == ActiveTool.IMPORTANT_PEN) {
                            isImportantPenMenuOpen = !isImportantPenMenuOpen
                        } else {
                            currentTool = ActiveTool.IMPORTANT_PEN
                            isImportantPenMenuOpen = false
                        }
                    },
                        modifier = Modifier.border(
                            width = if (isImportant) 3.dp else 0.dp,
                            color = if (isImportant) Color.Black else Color.Transparent,
                            shape = CircleShape
                        )
                    ) {
                        val iconColor = if (selectedImportantCategory != null) Color(selectedImportantCategory!!.colorArgb) else Color(0xFFFFD700)
                        Icon(
                            painter = painterResource(id = R.drawable.temp_important_stroke_icon),
                            contentDescription = "Important Pen",
                            tint = iconColor,
                            modifier = Modifier.padding(6.dp)
                        )
                    }

                    val isErase = currentTool == ActiveTool.ERASESTROKE
                    IconButton(onClick = {
                        currentTool = ActiveTool.ERASESTROKE
                    },
                        modifier = Modifier.border(
                            width = if (isErase) 3.dp else 0.dp,
                            color = if (isErase) Color.Black else Color.Transparent,
                            shape = CircleShape
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.erase),
                            contentDescription = "Eraser Tool",
                            modifier = Modifier.padding(6.dp)
                        )
                    }

                    val isLasso = currentTool == ActiveTool.LASSO
                    IconButton(onClick = {
                        currentTool = ActiveTool.LASSO
                    },
                        modifier = Modifier.border(
                            width = if (isLasso) 3.dp else 0.dp,
                            color = if (isLasso) Color.Black else Color.Transparent,
                            shape = CircleShape
                        )
                    ) {

                        Icon(
                            painter = painterResource(id = R.drawable.lasso),
                            contentDescription = "Lasso Tool",
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    // Insert Image Button
                    IconButton(
                        onClick = { shouldOpenImagePicker = true }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.image_add),
                            contentDescription = "Insert Image",
                            tint = Color.Black,
                            modifier = Modifier.padding(6.dp)
                        )
                    }

                    // Insert Text Box Button
                    IconButton(
                        onClick = { shouldAddTextBox = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Add Text Box",
                            tint = Color.Black,
                            modifier = Modifier.padding(6.dp)
                        )
                    }

                    // NEW: More Options Button
                    IconButton(
                        onClick = { isMoreMenuOpen = !isMoreMenuOpen },
                        modifier = Modifier.border(
                            width = if (isMoreMenuOpen) 3.dp else 0.dp,
                            color = if (isMoreMenuOpen) Color.Black else Color.Transparent,
                            shape = CircleShape
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.more),
                            contentDescription = "More Options",
                            modifier = Modifier.padding(6.dp)
                        )
                    }

                }
            }

        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp,
            color = Color.White,
            border = BorderStroke(3.dp, Color.Black)
        ) {
            IconButton(
                onClick = { onNavigateHome() },
                modifier = Modifier.size(64.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.parallel_notes_logo),
                    contentDescription = "Go to Homepage",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        if (isPenMenuOpen){

            Box( //invisible tap area to exit
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            // Close the menu
                            isPenMenuOpen = false
                        }
                    }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 85.dp)
            ) {
                PenCustomizationPanel(
                    style = currentPenStyle,
                    onStyleChange = { currentPenStyle = it },
                    thickness = penThickness,
                    onThicknessChange = { penThickness = it },
                    arcSmoothing = arcSmoothingEnabled,
                    onArcSmoothingChange = { arcSmoothingEnabled = it },
                    smoothCurrentStroke = smoothCurrentStroke,
                    onSmoothChange = { smoothCurrentStroke = it },
                    jitterAmount = removeJitterAmount,
                    onJitterChange = { removeJitterAmount = it },
                    onColorPickerClick = { isColorSelectorOpen = true }
                )
            }
        }

        if (isImportantPenMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            isImportantPenMenuOpen = false
                        }
                    }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 105.dp)
            ) {
                ImportantPenMenu(viewModel = viewModel)
            }
        }

        if (isMoreMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            isMoreMenuOpen = false
                        }
                    }
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 105.dp)
                    .width(300.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(3.dp, Color.Black)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showColorToolbar = !showColorToolbar }
                    ) {
                        Text("Show Color Toolbar", modifier = Modifier.width(130.dp), color = Color.Black, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = showColorToolbar,
                            onCheckedChange = { showColorToolbar = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color.Black)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(
                        onClick = {
                            isMoreMenuOpen = false
                            viewModel.exportToPdf { file ->
                                if (file != null) {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Note PDF"))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export to PDF", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isColorSelectorOpen) {
            PresetColorPickerDialog(
                onDismiss = { isColorSelectorOpen = false },
                onColorSelected = onPenColorSelected
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .clickable { showRenameDialog = true }, // Opens the dialog!
            shape = RoundedCornerShape(50), // Makes it a pill shape
            color = Color.White,
            shadowElevation = 8.dp,
            border = BorderStroke(2.dp, Color.Black)
        ) {
            Text(
                text = noteTitle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
        if (showRenameDialog) {
            RenameNoteDialog(
                currentTitle = noteTitle,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newTitle -> viewModel.updateTitle(newTitle) }
            )
        }
    }
}


enum class ActiveTool {
    DRAW,
    IMPORTANT_PEN,
    ERASESTROKE,
    LASSO
}

enum class ResizeHandle { TL, T, TR, L, R, BL, B, BR }



@Composable
fun DrawingCanvas(
    currentTool: ActiveTool,
    thickness: Float,
    pencolor: Color,
    penStyle: PenStyle,
    arcSmoothing: Boolean,
    smoothCurrentStroke: Boolean,
    removeJitterAmount: Float,
    viewModel: NoteViewModel,
    shouldOpenImagePicker: Boolean = false,
    onImagePickerConsumed: () -> Unit = {},
    shouldAddTextBox: Boolean = false,
    onTextBoxConsumed: () -> Unit = {}
) {
    //Bitmap States
    // state integer to force Compose to redraw when we mutate the bitmap
    var cacheVersion by remember { mutableIntStateOf(0) }
    var activeChunks by remember { mutableStateOf(mutableMapOf<String, CanvasChunk>()) }
    val CHUNK_SIZE = 512
    // Helper function for rebuilding chunks
    val rebuildTargetedChunks: (List<CanvasElement>, List<String>) -> Unit = { allElements, dirtyChunkKeys ->
        dirtyChunkKeys.forEach { key ->
            // Get the chunk (or create it if it doesn't exist yet)
            val chunk = activeChunks.getOrPut(key) {
                val parts = key.split(",")
                CanvasChunk(parts[0].toInt(), parts[1].toInt(), CHUNK_SIZE)
            }

            // Clear this specific chunk
            chunk.clear()

            // Find ALL elements that overlap this chunk's bounding box and redraw them
            allElements.forEach { element ->
                if (element.boundingBox.overlaps(chunk.bounds)) {
                    // have to shift the canvas negatively by the chunk's starting position so the stroke draws in the right local spot!
                    chunk.canvas.save()
                    chunk.canvas.translate(-chunk.bounds.left + element.minX, -chunk.bounds.top + element.minY)
                    chunk.canvas.drawPicture(element.picture)
                    chunk.canvas.restore()
                }
            }
        }
        cacheVersion++ // Trigger Compose to re-render the chunks
    }

    var undoStack by remember { mutableStateOf(emptyList<CanvasAction>()) }
    var redoStack by remember { mutableStateOf(emptyList<CanvasAction>()) }
    val loadedElements by viewModel.currentElements.collectAsState()
    //Drawing States (Vector)
    var canvasElements by remember { mutableStateOf(emptyList<CanvasElement>()) }
    var currentRawStroke by remember { mutableStateOf(emptyList<Point>())}

    //Cursor State
    var cursorPosition by remember { mutableStateOf<Offset?>(null) }

    //Lasso States
    var lassoPath  by remember {mutableStateOf(emptyList<Offset>())}
    var selectedElements by remember { mutableStateOf(emptyList<CanvasElement>()) }
    var isDraggingSelection by remember { mutableStateOf(false) }
    var dragLastPosition by remember { mutableStateOf(Offset.Zero) }
    var lassoTotalDx by remember { mutableFloatStateOf(0f) }
    var lassoTotalDy by remember { mutableFloatStateOf(0f) }



    //Viewport states
    var viewportPan by remember { mutableStateOf(Offset.Zero) }
    // Start zoomed out (0.5x) to give a larger overview by default
    var viewportScale by remember { mutableFloatStateOf(0.5f) }

    val selectedImportantCategory by viewModel.selectedImportantCategory.collectAsState()
    val lastProcessedBounds by viewModel.lastProcessedStrokeBounds.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    // Bounding box confirmation state for Important Pen
    var confirmedBoundingBox by remember { mutableStateOf<Rect?>(null) }
    var confirmedCategoryColor by remember { mutableStateOf<Color?>(null) }
    var boundingBoxJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Track the pending bounds from ViewModel
    var pendingBoundsInfo by remember { mutableStateOf<NoteViewModel.StrokeBoundsInfo?>(null) }

    // Canvas size (tracked via onSizeChanged for viewport-center image placement)
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    // Long-press delete state for images
    var imageToDelete by remember { mutableStateOf<ImageElement?>(null) }

    // Resize state (only for ImageElement selections)
    var resizingHandle by remember { mutableStateOf<ResizeHandle?>(null) }
    var resizeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resizePreviewRect by remember { mutableStateOf<Rect?>(null) }
    // Original elements captured at start of resize (for undo)
    var resizeOriginalElements by remember { mutableStateOf(emptyList<CanvasElement>()) }
    // Delta-based resize: anchor point and starting rect
    var resizeStartPos by remember { mutableStateOf(Offset.Zero) }
    var resizeStartRect by remember { mutableStateOf<Rect?>(null) }

    val context = LocalContext.current

    // Screen-to-canvas and canvas-to-screen helpers
    val toScreen: (Offset) -> Offset = { canvasPos ->
        Offset(canvasPos.x * viewportScale + viewportPan.x, canvasPos.y * viewportScale + viewportPan.y)
    }

    // Image picker launcher — processes the picked URI on a background thread
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            val uuid = UUID.randomUUID().toString()
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = mimeType.substringAfter("/").substringBefore(";").ifBlank { "jpg" }
            val imagesDir = File(context.filesDir, "images").also { it.mkdirs() }
            val destFile = File(imagesDir, "$uuid.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            val bitmap = BitmapFactory.decodeFile(destFile.absolutePath) ?: return@launch
            val maxDim = 600f
            val scale = minOf(maxDim / bitmap.width, maxDim / bitmap.height, 1f)
            val displayWidth = bitmap.width * scale
            val displayHeight = bitmap.height * scale
            val picture = createImagePicture(bitmap, displayWidth, displayHeight)
            // Place at canvas center of current viewport
            val screenCenterX = canvasSize.width / 2f
            val screenCenterY = canvasSize.height / 2f
            val canvasCenterX = (screenCenterX - viewportPan.x) / viewportScale
            val canvasCenterY = (screenCenterY - viewportPan.y) / viewportScale
            val element = ImageElement(
                imagePath = "images/$uuid.$ext",
                minX = canvasCenterX - displayWidth / 2f,
                minY = canvasCenterY - displayHeight / 2f,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                picture = picture
            )
            withContext(Dispatchers.Main) {
                canvasElements = canvasElements + element
                val chunkKeys = getOverlappingChunkKeys(element.boundingBox, CHUNK_SIZE)
                rebuildTargetedChunks(canvasElements, chunkKeys)
                undoStack = undoStack + CanvasAction.AddElements(listOf(element))
                redoStack = emptyList()
            }
        }
    }

    // Trigger the image picker when parent requests it
    LaunchedEffect(shouldOpenImagePicker) {
        if (shouldOpenImagePicker) {
            imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            onImagePickerConsumed()
        }
    }

    LaunchedEffect(shouldAddTextBox) {
        if (shouldAddTextBox) {
            val centerWorldX = (canvasSize.width / 2f - viewportPan.x) / viewportScale
            val centerWorldY = (canvasSize.height / 2f - viewportPan.y) / viewportScale
            val element = com.nvemuri.parallelnotes.data.entities.TextElement(
                text = "Double tap to edit markdown...",
                minX = centerWorldX - 250f,
                minY = centerWorldY - 50f,
                displayWidth = 500f,
                displayHeight = 100f
            )
            canvasElements = canvasElements + element
            undoStack = undoStack + CanvasAction.AddElements(listOf(element))
            redoStack = emptyList()
            onTextBoxConsumed()
        }
    }

    // React to ViewModel's bounds updates
    LaunchedEffect(lastProcessedBounds) {
        lastProcessedBounds?.let { boundsInfo ->
            pendingBoundsInfo = boundsInfo
        }
    }

    // screen coord to actual canvas coord
    val screenToWorld: (Offset) -> Offset = { screenPos ->
        (screenPos - viewportPan) / viewportScale
    }
    LaunchedEffect(loadedElements) {
        if (loadedElements.isNotEmpty() && canvasElements.isEmpty()) {
            canvasElements = loadedElements
        }
    }

    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var feedbackTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(feedbackMessage, feedbackTrigger) {
        if (feedbackMessage != null) {
            delay(1500)
            feedbackMessage = null
        }
    }

    val performUndo: () -> Unit = {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.last()
            undoStack = undoStack.dropLast(1)
            redoStack = redoStack + action

            val dirtyChunkKeys = mutableSetOf<String>()
            when (action) {
                is CanvasAction.AddElements -> {
                    action.elements.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    val actionIds = action.elements.map { it.id }.toSet()
                    canvasElements = canvasElements.filterNot { it.id in actionIds }
                    feedbackMessage = "Undo: Stroke"
                }
                is CanvasAction.RemoveElements -> {
                    action.elements.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    canvasElements = canvasElements + action.elements
                    feedbackMessage = "Undo: Erase"
                }
                is CanvasAction.MoveElements -> {
                    val actionIds = action.elements.map { it.id }.toSet()
                    val unmodified = canvasElements.filterNot { it.id in actionIds }
                    val translatedBack = action.elements.map { it.translate(-action.dx, -action.dy) }
                    action.elements.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    translatedBack.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    canvasElements = unmodified + translatedBack
                    feedbackMessage = "Undo: Lasso"
                }
                is CanvasAction.ResizeElements -> {
                    val resizedIds = action.resizedElements.map { it.id }.toSet()
                    action.resizedElements.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    action.originalElements.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    canvasElements = canvasElements.filterNot { it.id in resizedIds } + action.originalElements
                    feedbackMessage = "Undo: Resize"
                }
            }
            feedbackTrigger++
            rebuildTargetedChunks(canvasElements, dirtyChunkKeys.toList())
        }
    }

    val performRedo: () -> Unit = {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.last()
            redoStack = redoStack.dropLast(1)
            undoStack = undoStack + action

            val dirtyChunkKeys = mutableSetOf<String>()
            when (action) {
                is CanvasAction.AddElements -> {
                    action.elements.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    canvasElements = canvasElements + action.elements
                    feedbackMessage = "Redo: Stroke"
                }
                is CanvasAction.RemoveElements -> {
                    action.elements.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    val actionIds = action.elements.map { it.id }.toSet()
                    canvasElements = canvasElements.filterNot { it.id in actionIds }
                    feedbackMessage = "Redo: Erase"
                }
                is CanvasAction.MoveElements -> {
                    val actionIds = action.elements.map { it.id }.toSet()
                    val unmodified = canvasElements.filterNot { it.id in actionIds }
                    val translatedBack = action.elements.map { it.translate(-action.dx, -action.dy) }
                    translatedBack.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    action.elements.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    canvasElements = unmodified + action.elements
                    feedbackMessage = "Redo: Lasso"
                }
                is CanvasAction.ResizeElements -> {
                    val originalIds = action.originalElements.map { it.id }.toSet()
                    action.originalElements.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    action.resizedElements.forEach { dirtyChunkKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                    canvasElements = canvasElements.filterNot { it.id in originalIds } + action.resizedElements
                    feedbackMessage = "Redo: Resize"
                }
            }
            feedbackTrigger++
            rebuildTargetedChunks(canvasElements, dirtyChunkKeys.toList())
        }
    }
    LaunchedEffect(canvasElements) {
        viewModel.updateCurrentElements(canvasElements)

        // If we just loaded an existing note (elements exist but chunks don't yet), build the chunks!
        if (canvasElements.isNotEmpty() && activeChunks.isEmpty()) {
            val allKeys = mutableSetOf<String>()
            canvasElements.forEach { element ->
                allKeys.addAll(getOverlappingChunkKeys(element.boundingBox, CHUNK_SIZE))
            }
            rebuildTargetedChunks(canvasElements, allKeys.toList())
        }
    }
    LaunchedEffect(Unit) {
        if (canvasElements.isNotEmpty() && activeChunks.isEmpty()) {
            // Find every chunk that needs to exist
            val allKeys = mutableSetOf<String>()
            canvasElements.forEach { element ->
                allKeys.addAll(getOverlappingChunkKeys(element.boundingBox, CHUNK_SIZE))
            }
            // Build them all at once
            rebuildTargetedChunks(canvasElements, allKeys.toList())
        }
    }

    var editingTextElementId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                // If they tap outside an editing box, close it.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed && editingTextElementId != null) {
                            editingTextElementId = null
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    // save the old scale before mutating it
                    val oldScale = viewportScale
                    // apply the new zoom
                    viewportScale = (viewportScale * zoom).coerceIn(0.1f, 5.0f)
                    // calculate the scale ratio
                    val scaleRatio = viewportScale / oldScale
                    // apply the true focal point math
                    viewportPan = (viewportPan - centroid) * scaleRatio + centroid + pan
                }
            }
            .pointerInput(Unit){
                detectMultiFingerTap(
                    onTwoFingerTap = {
                        performUndo()
                    },
                    onThreeFingerTap = {
                        performRedo()
                    }
                )
            }
            .pointerInput(Unit) {
                // Long-press on ImageElement or TextElement to trigger delete menu (finger touch only)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.type != PointerType.Stylus) {
                        val canvasPos = screenToWorld(down.position)
                        val hitElement = canvasElements.firstOrNull {
                            (it is ImageElement || it is com.nvemuri.parallelnotes.data.entities.TextElement) && it.boundingBox.contains(canvasPos)
                        }
                        if (hitElement != null) {
                            val longPressResult = withTimeoutOrNull(500L) {
                                // Wait for pointer up or significant movement — either cancels long press
                                var pressed = true
                                while (pressed) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if ((change.position - down.position).getDistance() > 20f) return@withTimeoutOrNull Unit
                                    pressed = event.changes.any { it.pressed }
                                }
                                Unit // pointer released before timeout → not a long press
                            }
                            if (longPressResult == null) {
                                // Timeout elapsed = long press confirmed
                                if (hitElement is ImageElement) {
                                    imageToDelete = hitElement
                                } else if (hitElement is com.nvemuri.parallelnotes.data.entities.TextElement) {
                                    // Instantly delete text elements for now, or show a prompt.
                                    // Wait, let's just delete it directly since there's no menu.
                                    canvasElements = canvasElements.filterNot { it.id == hitElement.id }
                                    undoStack = undoStack + CanvasAction.RemoveElements(listOf(hitElement))
                                    redoStack = emptyList()
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit){
                awaitPointerEventScope {
                    while(true){
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.type == PointerType.Stylus){
                            when (event.type){
                                PointerEventType.Move,
                                PointerEventType.Press,
                                PointerEventType.Release -> {
                                    cursorPosition = change.position
                                }
                                PointerEventType.Exit -> {
                                    cursorPosition = null
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(currentTool, removeJitterAmount, smoothCurrentStroke, arcSmoothing, pencolor, thickness, penStyle, selectedImportantCategory){
                awaitEachGesture {
                    // POINTER DOWN
                    val down = awaitFirstDown()
                    down.consume()

                    // check if its a stylus and get pressure
                    val isStylus = down.type == PointerType.Stylus
                    val hasSelection = selectedElements.isNotEmpty()
                    if (!isStylus && !hasSelection && (currentTool == ActiveTool.DRAW || currentTool == ActiveTool.IMPORTANT_PEN || currentTool == ActiveTool.ERASESTROKE)) return@awaitEachGesture //only take pen for drawing
                    val startPressure = down.pressure

                    // Streamline stuff (position)
                    var virtualBrush = screenToWorld(down.position)
                    val stringLength = removeJitterAmount

                    //streamline stuff (velocity)
                    var lastTime = down.uptimeMillis
                    var lastHardwarePos = screenToWorld(down.position)

                    val maxVelo = 5.0f // may need to be tweaked

                    // Cancel bounding box display when user starts drawing
                    if (currentTool == ActiveTool.IMPORTANT_PEN) {
                        boundingBoxJob?.cancel()
                        confirmedBoundingBox = null
                        confirmedCategoryColor = null
                    }

                    //make a single dot if just tapped
                    if ((currentTool == ActiveTool.DRAW || currentTool == ActiveTool.IMPORTANT_PEN) && !hasSelection) {
                        currentRawStroke = listOf(Point(screenToWorld(down.position), startPressure))

                    }

                    //determining what theyre doing if its lasso or there's an active selection to interact with
                    val isTextOnlySelection = selectedElements.isNotEmpty() && selectedElements.all { it is com.nvemuri.parallelnotes.data.entities.TextElement }
                    if (currentTool == ActiveTool.LASSO || hasSelection) {
                        // Check for resize handle hit (image-only selections, in screen space)
                        val allResizable = selectedElements.isNotEmpty() && selectedElements.all { it is ImageElement || it is com.nvemuri.parallelnotes.data.entities.TextElement }
                        val hitHandle: ResizeHandle? = if (allResizable) {
                            val selBounds = selectedElements.drop(1).fold(selectedElements.first().boundingBox) { acc, el ->
                                Rect(minOf(acc.left, el.minX), minOf(acc.top, el.minY), maxOf(acc.right, el.maxX), maxOf(acc.bottom, el.maxY))
                            }
                            val htl = toScreen(Offset(selBounds.left, selBounds.top))
                            val hbr = toScreen(Offset(selBounds.right, selBounds.bottom))
                            val isTextOnly = selectedElements.all { it is com.nvemuri.parallelnotes.data.entities.TextElement }
                            val handleMap = if (isTextOnly) mapOf(
                                ResizeHandle.L to Offset(htl.x, (htl.y + hbr.y) / 2f),
                                ResizeHandle.R to Offset(hbr.x, (htl.y + hbr.y) / 2f)
                            ) else mapOf(
                                ResizeHandle.TL to htl,
                                ResizeHandle.T  to Offset((htl.x + hbr.x) / 2f, htl.y),
                                ResizeHandle.TR to Offset(hbr.x, htl.y),
                                ResizeHandle.L  to Offset(htl.x, (htl.y + hbr.y) / 2f),
                                ResizeHandle.R  to Offset(hbr.x, (htl.y + hbr.y) / 2f),
                                ResizeHandle.BL to Offset(htl.x, hbr.y),
                                ResizeHandle.B  to Offset((htl.x + hbr.x) / 2f, hbr.y),
                                ResizeHandle.BR to hbr
                            )
                            handleMap.entries.firstOrNull { (_, pos) -> (down.position - pos).getDistance() < 30f }?.key
                        } else null

                        if (hitHandle != null) {
                            // Enter resize mode — decode bitmap now so we only do it once
                            resizingHandle = hitHandle
                            resizeOriginalElements = selectedElements.toList()
                            val firstElement = selectedElements.first()
                            if (firstElement is ImageElement) {
                                resizeBitmap = BitmapFactory.decodeFile(File(context.filesDir, firstElement.imagePath).absolutePath)
                            } else {
                                resizeBitmap = null
                            }
                            val selBounds = selectedElements.drop(1).fold(selectedElements.first().boundingBox) { acc, el ->
                                Rect(minOf(acc.left, el.minX), minOf(acc.top, el.minY), maxOf(acc.right, el.maxX), maxOf(acc.bottom, el.maxY))
                            }
                            resizePreviewRect = selBounds
                            resizeStartRect = selBounds
                            resizeStartPos = screenToWorld(down.position)
                        } else if (selectedElements.isNotEmpty() && !isTextOnlySelection && isPointInPolygon(virtualBrush, lassoPath)) {
                            // Check if they tapped inside an active selection for dragging
                            isDraggingSelection = true
                            dragLastPosition = virtualBrush
                        }
                    }
                    // clear lasso if they tapped outside regardless of what tool is selected
                    // (but not if we just started a resize gesture on a handle)
                    val isOutsideSelection = if (isTextOnlySelection) {
                        val sb = selectedElements.drop(1).fold(selectedElements.first().boundingBox) { acc, el ->
                            Rect(minOf(acc.left, el.minX), minOf(acc.top, el.minY), maxOf(acc.right, el.maxX), maxOf(acc.bottom, el.maxY))
                        }
                        !sb.contains(virtualBrush)
                    } else {
                        !isPointInPolygon(virtualBrush, lassoPath)
                    }
                    if (resizingHandle == null && selectedElements.isNotEmpty() && isOutsideSelection) {

                        // tapped outside, commit ink back to the chunks
                        if (selectedElements.isNotEmpty()) {

                            selectedElements.forEach { element ->
                                // Find the chunks this specific element overlaps
                                val affectedKeys = getOverlappingChunkKeys(element.boundingBox, CHUNK_SIZE)

                                // Fast append it to those chunks
                                affectedKeys.forEach { key ->
                                    val chunk = activeChunks.getOrPut(key) {
                                        val parts = key.split(",")
                                        CanvasChunk(parts[0].toInt(), parts[1].toInt(), CHUNK_SIZE)
                                    }

                                    chunk.canvas.save()
                                    chunk.canvas.translate(-chunk.bounds.left + element.minX, -chunk.bounds.top + element.minY)
                                    chunk.canvas.drawPicture(element.picture)
                                    chunk.canvas.restore()
                                }
                            }
                            cacheVersion++ // Trigger UI update

                            if (lassoTotalDx != 0f || lassoTotalDy != 0f) {
                                undoStack = undoStack + CanvasAction.MoveElements(selectedElements, lassoTotalDx, lassoTotalDy)
                                redoStack = emptyList()
                                lassoTotalDx = 0f
                                lassoTotalDy = 0f
                            }
                            canvasElements = canvasElements + selectedElements
                            selectedElements = emptyList()
                            editingTextElementId = null
                        }
                        // Start a fresh lasso path
                        isDraggingSelection = false
                        if (currentTool == ActiveTool.LASSO){
                            lassoPath = listOf(virtualBrush)
                        } else {
                            lassoPath = emptyList()
                        }
                    }

                    // while pen is on screen
                    do {
                        val event: PointerEvent = awaitPointerEvent()
                        val change = event.changes.first()

                        if (change.pressed) {
                            change.consume()
                            val stylusPos = screenToWorld(change.position) //position of actual pointer
                            if ((currentTool == ActiveTool.DRAW || currentTool == ActiveTool.IMPORTANT_PEN) && isStylus) {
                                val movePressure = if (change.type == PointerType.Stylus) change.pressure else 1.0f

                                val currentTime = change.uptimeMillis

                                //streamline logic, velocity
                                val dt = (currentTime - lastTime).toFloat()
                                val hardwareDx = stylusPos.x - lastHardwarePos.x
                                val hardwareDy = stylusPos.y - lastHardwarePos.y
                                val hardwareDistance = sqrt((hardwareDx * hardwareDx) + (hardwareDy * hardwareDy))
                                val velocity = if (dt > 0f) hardwareDistance / dt else 0f //instantaneous velocity of the pen
                                val velocityScalar = (velocity / maxVelo).coerceIn(0.1f, 1.0f)

                                val dynamicStringLength = stringLength * velocityScalar

                                //streamline logic, position
                                val dx = virtualBrush.x - stylusPos.x
                                val dy = virtualBrush.y - stylusPos.y
                                val distance = sqrt((dx * dx) + (dy * dy))

                                if (distance > dynamicStringLength) {
                                    val newBrushX = stylusPos.x + (dx / distance) * dynamicStringLength
                                    val newBrushY = stylusPos.y + (dy / distance) * dynamicStringLength

                                    virtualBrush = Offset(newBrushX, newBrushY)

                                    // Only save the virtual brush's coordinates to the stroke array
                                    currentRawStroke = currentRawStroke + Point(
                                        virtualBrush,
                                        movePressure
                                    )
                                }
                            }
                            else if (currentTool == ActiveTool.ERASESTROKE && isStylus) {

                                val eraserRadius = 50f
                                // Create the bounding box for the eraser touch
                                val touchRect = Rect(
                                    left = stylusPos.x - eraserRadius,
                                    top = stylusPos.y - eraserRadius,
                                    right = stylusPos.x + eraserRadius,
                                    bottom = stylusPos.y + eraserRadius
                                )

                                // 1. Find the elements the user just touched
                                val toErase = canvasElements.filter { element ->
                                    // Fast bounding box check first
                                    if (!element.boundingBox.overlaps(touchRect)) return@filter false

                                    // check precisely if first is true
                                    when (element) {
                                        is PenStroke -> element.points.any {
                                            (it.offset - stylusPos).getDistance() < eraserRadius
                                        }
                                        is ImageElement -> false // images are deleted via long press only
                                        is com.nvemuri.parallelnotes.data.entities.TextElement -> false // same
                                    }
                                }

                                if (toErase.isNotEmpty()) {
                                    // Remove them from the main list immediately
                                    canvasElements = canvasElements.filterNot { it in toErase }
                                    undoStack = undoStack + CanvasAction.RemoveElements(toErase)
                                    redoStack = emptyList()

                                    // Notify ViewModel to remove from important strokes
                                    viewModel.removeImportantStrokes(toErase)

                                    // Figure out which chunks need to be redrawn
                                    val dirtyChunkKeys = mutableSetOf<String>()
                                    toErase.forEach { erasedElement ->
                                        dirtyChunkKeys.addAll(getOverlappingChunkKeys(erasedElement.boundingBox, CHUNK_SIZE))
                                    }

                                    // Trigger the targeted rebuild
                                    rebuildTargetedChunks(canvasElements, dirtyChunkKeys.toList())
                                }

                            } else if (currentTool == ActiveTool.LASSO || selectedElements.isNotEmpty()) {
                                if (resizingHandle != null) {
                                    // Delta-based resize: apply drag delta to the original rect captured at resize-start.
                                    // This prevents snapping when the touch lands slightly off the handle corner,
                                    // and allows the box to grow beyond any initial offset.
                                    val start = resizeStartRect ?: resizePreviewRect ?: selectedElements.drop(1).fold(selectedElements.first().boundingBox) { acc, el ->
                                        Rect(minOf(acc.left, el.minX), minOf(acc.top, el.minY), maxOf(acc.right, el.maxX), maxOf(acc.bottom, el.maxY))
                                    }
                                    val dx = stylusPos.x - resizeStartPos.x
                                    val dy = stylusPos.y - resizeStartPos.y
                                    val minDim = 20f
                                    resizePreviewRect = when (resizingHandle) {
                                        ResizeHandle.BR -> Rect(start.left, start.top, maxOf(start.left + minDim, start.right + dx), maxOf(start.top + minDim, start.bottom + dy))
                                        ResizeHandle.BL -> Rect(minOf(start.right - minDim, start.left + dx), start.top, start.right, maxOf(start.top + minDim, start.bottom + dy))
                                        ResizeHandle.TR -> Rect(start.left, minOf(start.bottom - minDim, start.top + dy), maxOf(start.left + minDim, start.right + dx), start.bottom)
                                        ResizeHandle.TL -> Rect(minOf(start.right - minDim, start.left + dx), minOf(start.bottom - minDim, start.top + dy), start.right, start.bottom)
                                        ResizeHandle.R  -> Rect(start.left, start.top, maxOf(start.left + minDim, start.right + dx), start.bottom)
                                        ResizeHandle.L  -> Rect(minOf(start.right - minDim, start.left + dx), start.top, start.right, start.bottom)
                                        ResizeHandle.B  -> Rect(start.left, start.top, start.right, maxOf(start.top + minDim, start.bottom + dy))
                                        ResizeHandle.T  -> Rect(start.left, minOf(start.bottom - minDim, start.top + dy), start.right, start.bottom)
                                        null -> start
                                    }
                                } else if (isDraggingSelection) {
                                    // Calculate the distance moved since the last frame
                                    val dx = stylusPos.x - dragLastPosition.x
                                    val dy = stylusPos.y - dragLastPosition.y
                                    lassoTotalDx += dx
                                    lassoTotalDy += dy

                                    // Translate all selected strokes to new location
                                    selectedElements = selectedElements.map { element ->
                                        element.translate(dx, dy)
                                    }

                                    // Translate the lasso path so the lasso moves with the ink
                                    lassoPath = lassoPath.map { offset ->
                                        Offset(offset.x + dx, offset.y + dy)
                                    }

                                    dragLastPosition = stylusPos
                                } else if (currentTool == ActiveTool.LASSO) {
                                    // Just drawing the lasso loop
                                    lassoPath = lassoPath + stylusPos
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Commit resize on pointer release — auto-commits element back to canvas so undo works cleanly
                    if (resizingHandle != null) {
                        val finalRect = resizePreviewRect
                        val bitmap = resizeBitmap
                        if (finalRect != null && finalRect.width > 0f && finalRect.height > 0f) {
                            val resized = resizeOriginalElements.map { el ->
                                if (el is ImageElement && bitmap != null) {
                                    val newPicture = createImagePicture(bitmap, finalRect.width, finalRect.height)
                                    el.copy(minX = finalRect.left, minY = finalRect.top, displayWidth = finalRect.width, displayHeight = finalRect.height, picture = newPicture)
                                } else if (el is com.nvemuri.parallelnotes.data.entities.TextElement) {
                                    // Height is auto-managed by content; only update position and width.
                                    el.copy(minX = finalRect.left, minY = el.minY, displayWidth = finalRect.width)
                                } else el
                            }
                            // Auto-commit: put resized elements back into canvas and rebuild chunks
                            val dirtyKeys = mutableSetOf<String>()
                            resizeOriginalElements.forEach { dirtyKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                            resized.forEach { dirtyKeys.addAll(getOverlappingChunkKeys(it.boundingBox, CHUNK_SIZE)) }
                            canvasElements = canvasElements + resized
                            selectedElements = emptyList()
                            editingTextElementId = null
                            lassoPath = emptyList()
                            isDraggingSelection = false
                            lassoTotalDx = 0f
                            lassoTotalDy = 0f
                            rebuildTargetedChunks(canvasElements, dirtyKeys.toList())
                            undoStack = undoStack + CanvasAction.ResizeElements(resizeOriginalElements, resized)
                            redoStack = emptyList()
                        }
                        resizingHandle = null
                        resizeBitmap = null
                        resizePreviewRect = null
                        resizeOriginalElements = emptyList()
                        resizeStartRect = null
                        resizeStartPos = Offset.Zero
                    }

                    // FOR LASSO, CHECK IF THE USER CAPTURED ANYTHING
                    if (currentTool == ActiveTool.LASSO && lassoPath.isNotEmpty()) {
                        val newlySelected = mutableListOf<CanvasElement>()
                        val unselected = mutableListOf<CanvasElement>()

                        for (element in canvasElements) {
                            val isSelected = when (element) {
                                is PenStroke -> element.points.any { isPointInPolygon(it.offset, lassoPath) }
                                is ImageElement -> listOf(
                                    Offset(element.minX, element.minY),
                                    Offset(element.maxX, element.minY),
                                    Offset(element.maxX, element.maxY),
                                    Offset(element.minX, element.maxY),
                                    element.boundingBox.center
                                ).any { isPointInPolygon(it, lassoPath) }
                                is com.nvemuri.parallelnotes.data.entities.TextElement -> false
                            }

                            if (isSelected) {
                                newlySelected.add(element)
                            } else {
                                unselected.add(element)
                            }
                        }

                        selectedElements = selectedElements + newlySelected
                        canvasElements = unselected

                        // If we successfully picked something up, rebuild ONLY the chunks it came from
                        if (newlySelected.isNotEmpty()) {
                            val dirtyChunkKeys = mutableSetOf<String>()
                            newlySelected.forEach { pickedUpElement ->
                                dirtyChunkKeys.addAll(getOverlappingChunkKeys(pickedUpElement.boundingBox, CHUNK_SIZE))
                            }

                            rebuildTargetedChunks(canvasElements, dirtyChunkKeys.toList())
                        }

                        if (selectedElements.isEmpty()){
                            lassoPath = emptyList()
                        }
                    }

                    // IF DRAW, ADD LAST STROKE TO COMPLETED
                    if ((currentTool == ActiveTool.DRAW || currentTool == ActiveTool.IMPORTANT_PEN) && currentRawStroke.isNotEmpty()) {
                        //Convert new strokes to pictures so they can be displayed and moved efficiently
                        //first find the size of the picture
                        var minX = Float.MAX_VALUE
                        var minY = Float.MAX_VALUE
                        var maxX = -Float.MAX_VALUE
                        var maxY = -Float.MAX_VALUE

                        // Loop through all points to find the extreme edges
                        for (point in currentRawStroke) {
                            val x = point.offset.x
                            val y = point.offset.y

                            if (x < minX) minX = x
                            if (y < minY) minY = y
                            if (x > maxX) maxX = x
                            if (y > maxY) maxY = y
                        }

                        val strokePenStyle = if (currentTool == ActiveTool.IMPORTANT_PEN) PenStyle.SOLID else penStyle
                        val maxWidth = if (strokePenStyle == PenStyle.HIGHLIGHTER) thickness * 1.5f else thickness
                        val padding = maxWidth / 2f

                        minX -= padding
                        minY -= padding
                        maxX += padding
                        maxY += padding
                        val picture = Picture()
                        val width = (maxX - minX).toInt() + 1
                        val height = (maxY - minY).toInt() + 1
                        val nativeCanvas: NativeCanvas = picture.beginRecording(width, height)

                        //actually draw and take the picture
                        val actualColor = if (currentTool == ActiveTool.IMPORTANT_PEN) {
                            if (selectedImportantCategory != null) Color(selectedImportantCategory!!.colorArgb) else Color(0xFFFFD700)
                        } else pencolor

                        val nativePaint = NativePaint().apply {
                            color = actualColor.toArgb()
                            isAntiAlias = true
                            style = NativePaint.Style.STROKE
                            strokeJoin = Join.ROUND
                        }
                        val pointsToSave = if (arcSmoothing) {
                            bezierSmoothStroke(currentRawStroke)
                        } else {
                            currentRawStroke
                        }

                        when (strokePenStyle) {
                            PenStyle.SOLID -> {
                                nativePaint.strokeCap = Cap.ROUND
                                if (pointsToSave.size == 1) {
                                    val p = pointsToSave.first()
                                    nativePaint.strokeWidth = (0.2f + (p.pressure * 0.8f)) * thickness
                                    nativeCanvas.drawPoint(p.offset.x - minX, p.offset.y - minY, nativePaint)
                                } else {
                                    for (i in 0 until pointsToSave.size - 1) {
                                        val start = pointsToSave[i]
                                        val end = pointsToSave[i + 1]
                                        nativePaint.strokeWidth = (0.2f + (end.pressure * 0.8f)) * thickness
                                        nativeCanvas.drawLine(
                                            start.offset.x - minX, start.offset.y - minY,
                                            end.offset.x - minX, end.offset.y - minY,
                                            nativePaint
                                        )
                                    }
                                }
                            }
                            PenStyle.DASHED -> {
                                val avgPressure = pointsToSave.map { 0.2f + it.pressure * 0.8f }.average().toFloat()
                                nativePaint.strokeWidth = avgPressure * thickness
                                nativePaint.strokeCap = Cap.ROUND
                                nativePaint.pathEffect = android.graphics.DashPathEffect(
                                    floatArrayOf(thickness * 1.5f, thickness * 1f), 0f
                                )
                                val path = android.graphics.Path().apply {
                                    moveTo(pointsToSave.first().offset.x - minX, pointsToSave.first().offset.y - minY)
                                    for (i in 1 until pointsToSave.size) {
                                        lineTo(pointsToSave[i].offset.x - minX, pointsToSave[i].offset.y - minY)
                                    }
                                }
                                nativeCanvas.drawPath(path, nativePaint)
                            }
                            PenStyle.HIGHLIGHTER -> {
                                nativePaint.alpha = 100
                                nativePaint.strokeWidth = thickness * 1.5f
                                nativePaint.strokeCap = Cap.SQUARE
                                if (pointsToSave.size == 1) {
                                    val p = pointsToSave.first()
                                    nativeCanvas.drawPoint(p.offset.x - minX, p.offset.y - minY, nativePaint)
                                } else {
                                    val path = android.graphics.Path().apply {
                                        moveTo(pointsToSave.first().offset.x - minX, pointsToSave.first().offset.y - minY)
                                        for (i in 1 until pointsToSave.size) {
                                            lineTo(pointsToSave[i].offset.x - minX, pointsToSave[i].offset.y - minY)
                                        }
                                    }
                                    nativeCanvas.drawPath(path, nativePaint)
                                }
                            }
                        }
                        picture.endRecording()

                        //create the data representation
                        val newStroke = PenStroke(
                            id = UUID.randomUUID().toString(),
                            zIndex = 0f,
                            rawPoints = currentRawStroke,
                            points = pointsToSave,
                            arcSmoothing = arcSmoothing,
                            picture = picture,
                            thickness = thickness,
                            color = actualColor,
                            penStyle = strokePenStyle,
                            minX = minX, minY = minY, maxX = maxX, maxY = maxY
                        )
                        //find the chunk keys that need to be updated
                        val affectedKeys = getOverlappingChunkKeys(newStroke.boundingBox, CHUNK_SIZE)

                        // fast append to those specific chunks
                        affectedKeys.forEach { key ->
                            val chunk = activeChunks.getOrPut(key) {
                                val parts = key.split(",")
                                CanvasChunk(parts[0].toInt(), parts[1].toInt(), CHUNK_SIZE)
                            }

                            chunk.canvas.save()
                            // Shift the canvas backward so the chunk's top-left is 0,0 locally
                            chunk.canvas.translate(-chunk.bounds.left, -chunk.bounds.top)

                            // translate to the stroke's actual coordinates and draw it
                            chunk.canvas.translate(newStroke.minX, newStroke.minY)
                            chunk.canvas.drawPicture(newStroke.picture)

                            chunk.canvas.restore()
                        }

                        // 3. Update states
                        cacheVersion++
                        canvasElements = canvasElements + newStroke
                        undoStack = undoStack + CanvasAction.AddElements(listOf(newStroke))
                        redoStack = emptyList()

                        if (currentTool == ActiveTool.IMPORTANT_PEN) {
                            viewModel.processImportantStroke(newStroke)

                            // Cancel any existing timer
                            boundingBoxJob?.cancel()

                            // Launch debounce timer
                            boundingBoxJob = coroutineScope.launch {
                                // Debounce: wait 300ms for user to stop drawing
                                kotlinx.coroutines.delay(300)

                                // Use the latest pending bounds (from ViewModel)
                                pendingBoundsInfo?.let { boundsInfo ->
                                    confirmedBoundingBox = Rect(boundsInfo.minX, boundsInfo.minY, boundsInfo.maxX, boundsInfo.maxY)
                                    confirmedCategoryColor = Color(boundsInfo.colorArgb)

                                    // Auto-hide after 1 second
                                    kotlinx.coroutines.delay(1000)
                                    confirmedBoundingBox = null
                                    confirmedCategoryColor = null
                                    pendingBoundsInfo = null
                                }
                            }
                        }

                        currentRawStroke = emptyList()
                    }
                }
            }
    ) {
        //draw the strokes and canvas elements with transform
        withTransform({
            translate(viewportPan.x, viewportPan.y)
            scale(viewportScale, viewportScale, Offset.Zero)
        }) {
            // Draw the strokes
            // efficient displaying with bitmap for completed strokes
            // bitmap chunked so for easier rerendering and expansion
            val trigger = cacheVersion
            activeChunks.values.forEach { chunk ->
                drawImage(
                    image = chunk.bitmap.asImageBitmap(),
                    topLeft = Offset(chunk.bounds.left, chunk.bounds.top)
                )
            }

            // less efficient drawing with points for current stroke
            if (currentRawStroke.isNotEmpty()) {
                val strokeToDraw = if (smoothCurrentStroke && arcSmoothing) {
                    bezierSmoothStroke(currentRawStroke)
                } else {
                    currentRawStroke
                }
                val actualColor = if (currentTool == ActiveTool.IMPORTANT_PEN) {
                    if (selectedImportantCategory != null) Color(selectedImportantCategory!!.colorArgb) else Color(0xFFFFD700)
                } else pencolor

                val currentStroke = PenStroke(
                    id = "temp",
                    zIndex = 0f,
                    rawPoints = currentRawStroke,
                    points = strokeToDraw,
                    arcSmoothing = arcSmoothing,
                    thickness = thickness,
                    color = actualColor,
                    penStyle = if (currentTool == ActiveTool.IMPORTANT_PEN) PenStyle.SOLID else penStyle,
                    picture = Picture(),
                    minX = 0f,
                    minY = 0f,
                    maxX = 0f,
                    maxY = 0f
                ) //create temp stroke with empty pic to pass to drawStroke
                drawStroke(currentStroke, thickness)
            }
            //selected elements use more optimized picture movement
            drawIntoCanvas { canvas ->
                selectedElements.forEach { element ->
                    canvas.save()
                    canvas.translate(element.minX, element.minY)
                    canvas.nativeCanvas.drawPicture(element.picture)
                    canvas.restore()
                }
            }

            //draw the lasso path if it exists (only while actively drawing, not when selection is active)
            if (lassoPath.size > 1 && selectedElements.isEmpty()) {
                val path = Path().apply {
                    moveTo(lassoPath.first().x, lassoPath.first().y)
                    lassoPath.drop(1).forEach { lineTo(it.x, it.y) }
                    close() // Connect the end back to the start
                }

                // Draw a dashed line
                drawPath(
                    path = path,
                    color = Color.Gray,
                    style = Stroke(
                        width = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    )
                )
            }

            // Draw bounding box confirmation for Important Pen
            confirmedBoundingBox?.let { box ->
                val boxColor = confirmedCategoryColor ?: Color(0xFFFFD700)
                val threshold = 300f
                val padding = 8f

                // Draw outer threshold box (shows proximity area)
                drawRect(
                    color = boxColor.copy(alpha = 0.2f),
                    topLeft = Offset(box.left - threshold - padding, box.top - threshold - padding),
                    size = Size(box.width + threshold * 2 + padding * 2, box.height + threshold * 2 + padding * 2),
                    style = Stroke(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                    )
                )

                // Draw inner bounding box (around actual stroke group with padding)
                drawRect(
                    color = boxColor,
                    topLeft = Offset(box.left - padding, box.top - padding),
                    size = Size(box.width + padding * 2, box.height + padding * 2),
                    style = Stroke(
                        width = 15f, //want this to be this thickness and to be solid
                    )
                )
            }
        }
        // non transformed things

        // Resize handles for image-only selections (drawn in screen space for constant visual size)
        if (selectedElements.isNotEmpty() && selectedElements.all { it is ImageElement || it is com.nvemuri.parallelnotes.data.entities.TextElement }) {
            val selBounds = selectedElements.drop(1).fold(selectedElements.first().boundingBox) { acc, el ->
                Rect(
                    minOf(acc.left, el.minX), minOf(acc.top, el.minY),
                    maxOf(acc.right, el.maxX), maxOf(acc.bottom, el.maxY)
                )
            }
            val tl = toScreen(Offset(selBounds.left, selBounds.top))
            val br = toScreen(Offset(selBounds.right, selBounds.bottom))
            val tm = Offset((tl.x + br.x) / 2f, tl.y)
            val bm = Offset((tl.x + br.x) / 2f, br.y)
            val ml = Offset(tl.x, (tl.y + br.y) / 2f)
            val mr = Offset(br.x, (tl.y + br.y) / 2f)
            val tr = Offset(br.x, tl.y)
            val bl = Offset(tl.x, br.y)

            // Selection outline — thick black dashes matching app style
            drawRect(
                color = Color.Black,
                topLeft = tl,
                size = Size(br.x - tl.x, br.y - tl.y),
                style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f), 0f))
            )

            // Resize preview outline during active resize (solid, slightly thicker)
            resizePreviewRect?.let { rect ->
                val rtl = toScreen(Offset(rect.left, rect.top))
                val rbr = toScreen(Offset(rect.right, rect.bottom))
                drawRect(
                    color = Color.Black,
                    topLeft = rtl,
                    size = Size(rbr.x - rtl.x, rbr.y - rtl.y),
                    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f), 0f))
                )
            }

            // Circular handles — text-only selections get only L/R since height is auto-managed
            val handleRadius = 14f
            val isTextOnlySelection = selectedElements.all { it is com.nvemuri.parallelnotes.data.entities.TextElement }
            val handlesToShow = if (isTextOnlySelection) listOf(ml, mr) else listOf(tl, tm, tr, ml, mr, bl, bm, br)
            handlesToShow.forEach { pos ->
                drawCircle(color = Color.White, radius = handleRadius, center = pos)
                drawCircle(color = Color.Black, radius = handleRadius, center = pos, style = Stroke(width = 3.5f))
            }
        }

        // move the cursor
        cursorPosition?.let { pos ->
            if (currentTool == ActiveTool.DRAW || currentTool == ActiveTool.IMPORTANT_PEN) {
                val cursorColor = if (currentTool == ActiveTool.IMPORTANT_PEN) {
                    if (selectedImportantCategory != null) Color(selectedImportantCategory!!.colorArgb) else Color(0xFFFFD700)
                } else Color.Black

                // A small, solid black dot
                drawCircle(
                    color = cursorColor,
                    radius = 5f,
                    center = pos,
                    style = Stroke(width = 2f)
                )
            } else if (currentTool == ActiveTool.ERASESTROKE) {
                // A large, semi-transparent circle matching your eraser radius in world units
                drawCircle(
                    color = Color.Gray,
                    radius = 50f * viewportScale, // Correctly scaled with viewport
                    center = pos,
                    alpha = 0.5f
                )
            }
        }
    }

    // Compose Text Element Overlay
    val allTextElements = (canvasElements + selectedElements).filterIsInstance<com.nvemuri.parallelnotes.data.entities.TextElement>()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = viewportPan.x
                translationY = viewportPan.y
                scaleX = viewportScale
                scaleY = viewportScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
    ) {
        allTextElements.forEach { textEl ->
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(textEl.minX.toInt(), textEl.minY.toInt()) }
                    .width(with(density) { textEl.displayWidth.toDp() })
                    .wrapContentHeight(unbounded = true)
                    .onSizeChanged { size ->
                        val newHeight = size.height.toFloat()
                        if (kotlin.math.abs(newHeight - textEl.displayHeight) > 1f) {
                            val updated = textEl.copy(displayHeight = newHeight)
                            canvasElements = canvasElements.map { if (it.id == textEl.id) updated else it }
                            selectedElements = selectedElements.map { if (it.id == textEl.id) updated else it }
                        }
                    }
                    .pointerInput(textEl.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                editingTextElementId = textEl.id
                                // Move element into selectedElements so resize/drag handles appear
                                val current = canvasElements.firstOrNull { it.id == textEl.id }
                                if (current != null) {
                                    canvasElements = canvasElements.filterNot { it.id == current.id }
                                    selectedElements = listOf(current)
                                    lassoTotalDx = 0f
                                    lassoTotalDy = 0f
                                }
                            }
                        )
                    }
            ) {
                if (editingTextElementId == textEl.id) {
                    var textFieldValue by remember(textEl.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(textEl.text)) }
                    androidx.compose.foundation.text.BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            val processedValue = com.nvemuri.parallelnotes.utils.handleMarkdownTextEdit(textFieldValue, newValue)
                            textFieldValue = processedValue
                            val updated = textEl.copy(text = processedValue.text)
                            canvasElements = canvasElements.map { if (it.id == textEl.id) updated else it }
                            selectedElements = selectedElements.map { if (it.id == textEl.id) updated else it }
                            viewModel.updateCurrentElements(canvasElements)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(unbounded = true)
                            .background(Color.Transparent)
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.Tab && event.type == KeyEventType.KeyDown) {
                                    val newTextFieldValue = com.nvemuri.parallelnotes.utils.handleTabIndent(textFieldValue, event.isShiftPressed)
                                    if (newTextFieldValue != null) {
                                        textFieldValue = newTextFieldValue
                                        val updated = textEl.copy(text = newTextFieldValue.text)
                                        canvasElements = canvasElements.map { if (it.id == textEl.id) updated else it }
                                        selectedElements = selectedElements.map { if (it.id == textEl.id) updated else it }
                                        viewModel.updateCurrentElements(canvasElements)
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                false
                            },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.Black,
                            fontSize = 32.sp,
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        ),
                        visualTransformation = com.nvemuri.parallelnotes.utils.MarkdownVisualTransformation()
                    )
                } else {
                    dev.jeziellago.compose.markdowntext.MarkdownText(
                        markdown = textEl.text,
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).background(Color.Transparent).padding(4.dp),
                        color = Color.Black,
                        fontSize = 32.sp
                    )
                }
            }

            // Pill drag handle — centered above the text box when in edit mode
            if (textEl.id == editingTextElementId) {
                val pillWidthPx = 80f
                val pillHeightPx = 24f
                Box(
                    modifier = Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (textEl.minX + textEl.displayWidth / 2f - pillWidthPx / 2f).toInt(),
                                (textEl.minY - pillHeightPx - 10f).toInt()
                            )
                        }
                        .width(with(density) { pillWidthPx.toDp() })
                        .height(with(density) { pillHeightPx.toDp() })
                        .background(Color.DarkGray.copy(alpha = 0.7f), RoundedCornerShape(50))
                        .pointerInput(textEl.id + "_drag") {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                selectedElements = selectedElements.map { el ->
                                    if (el.id == textEl.id) el.translate(dragAmount.x, dragAmount.y) else el
                                }
                                lassoTotalDx += dragAmount.x
                                lassoTotalDy += dragAmount.y
                            }
                        }
                )
            }
        }
    }

    AnimatedVisibility(
        visible = feedbackMessage != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 64.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.7f),
            shadowElevation = 4.dp
        ) {
            Text(
                text = feedbackMessage ?: "",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    // Delete image dialog (triggered by long press)
    imageToDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { imageToDelete = null },
            title = { Text("Delete Image") },
            text = { Text("Remove this image from the canvas?") },
            confirmButton = {
                TextButton(onClick = {
                    val dirtyKeys = getOverlappingChunkKeys(image.boundingBox, CHUNK_SIZE)
                    canvasElements = canvasElements.filterNot { it.id == image.id }
                    undoStack = undoStack + CanvasAction.RemoveElements(listOf(image))
                    redoStack = emptyList()
                    rebuildTargetedChunks(canvasElements, dirtyKeys)
                    imageToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { imageToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
}

@Composable
fun ImportantPenMenu(viewModel: NoteViewModel) {
    val categories by viewModel.importantCategories.collectAsState()
    val selectedCategory by viewModel.selectedImportantCategory.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(3.dp, Color.Black),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.width(300.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Categories", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // Default "Important" category (pseudo-category)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectImportantCategory(null) }
                    .background(if (selectedCategory == null) Color.LightGray else Color.Transparent)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(24.dp).background(Color(0xFFFFD700), CircleShape))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Default")
            }

            categories.forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectImportantCategory(category) }
                        .background(if (selectedCategory?.name == category.name) Color.LightGray else Color.Transparent)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(24.dp).background(Color(category.colorArgb), CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(category.name, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteImportantCategory(category) }) {
                        Icon(painterResource(id = R.drawable.erase), contentDescription = "Delete", modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Text("Create New Category")
            }
        }
    }

    if (showCreateDialog) {
        CreateCategoryDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color ->
                viewModel.createImportantCategory(name, color.toArgb())
            }
        )
    }
}

@Composable
fun CreateCategoryDialog(onDismiss: () -> Unit, onConfirm: (String, Color) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Magenta, Color.Yellow, Color.Gray, Color.Black)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(100.dp)) {
                    items(colors) { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .padding(4.dp)
                                .background(color, CircleShape)
                                .border(if (selectedColor == color) 2.dp else 0.dp, Color.Black, CircleShape)
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, selectedColor); onDismiss() }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PenCustomizationPanel(
    style: PenStyle, onStyleChange: (PenStyle) -> Unit,
    thickness: Float, onThicknessChange: (Float) -> Unit,
    arcSmoothing: Boolean, onArcSmoothingChange: (Boolean) -> Unit,
    jitterAmount: Float, onJitterChange: (Float) -> Unit,
    onColorPickerClick: () -> Unit,
    smoothCurrentStroke: Boolean,
    onSmoothChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(3.dp, Color.Black),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.padding(start = 16.dp) // Space between toolbar and menu[p

    ) {
        Row(modifier = Modifier.padding(24.dp)) {

            // --- LEFT SIDE: Pen Styles ---
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(end = 32.dp)
            ) {
                // TODO: Swap these basic Text buttons with your SVG Icon buttons
                Button(
                    onClick = { onStyleChange(PenStyle.SOLID) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (style == PenStyle.SOLID) Color.LightGray else Color.Transparent,
                        contentColor = Color.Black
                    )
                ) { Text("Solid") }

                Button(
                    onClick = { onStyleChange(PenStyle.DASHED) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (style == PenStyle.DASHED) Color.LightGray else Color.Transparent,
                        contentColor = Color.Black
                    )
                ) { Text("Dash") }

                Button(
                    onClick = { onStyleChange(PenStyle.HIGHLIGHTER) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (style == PenStyle.HIGHLIGHTER) Color.LightGray else Color.Transparent,
                        contentColor = Color.Black
                    )
                ) { Text("Highlighter") }
            }

            // --- RIGHT SIDE: Settings ---
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Thickness Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Thickness", modifier = Modifier.width(130.dp), fontWeight = FontWeight.Bold)
                    Slider(
                        value = thickness,
                        onValueChange = onThicknessChange,
                        valueRange = 10f..80f, // Expanded range for zoomed out view
                        modifier = Modifier.width(120.dp)
                    )
                }

                // Color Picker Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Color", modifier = Modifier.width(130.dp), fontWeight = FontWeight.Bold)
                    // Draws a rainbow circle using a sweep gradient
                    Canvas(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onColorPickerClick() }
                    ) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                            )
                        )
                    }
                }

                // Arc Smoothing Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Arc Smoothing", modifier = Modifier.width(130.dp), fontWeight = FontWeight.Bold)
                    Switch(
                        checked = arcSmoothing,
                        onCheckedChange = onArcSmoothingChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = Color.Black)
                    )
                }

                //smoothing for current stroke
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Smooth Current Stroke", modifier = Modifier.width(130.dp), fontWeight = FontWeight.Bold)
                    Switch(
                        checked = smoothCurrentStroke && arcSmoothing,
                        onCheckedChange = onSmoothChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = Color.Black)
                    )
                }

                // Remove Jitter Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remove Jitter", modifier = Modifier.width(130.dp), fontWeight = FontWeight.Bold)
                    Slider(
                        value = jitterAmount,
                        onValueChange = onJitterChange,
                        valueRange = 0f..50f,
                        modifier = Modifier.width(120.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ColorToolbar(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color.Black, Color.Blue, Color.Red, Color.Green, Color.Yellow, Color.DarkGray, Color.LightGray, Color.White,
        Color(0xFFE91E63), // Pink
        Color(0xFFFF9800), // Orange
        Color(0xFF4CAF50), // Lighter Green
        Color.Cyan, Color.Magenta, Color(0xFF9C27B0) // Purple
    )

    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Auto-scroll to keep the active color visible when it changes (e.g. on pen style switch).
    LaunchedEffect(currentColor) {
        val index = colors.indexOf(currentColor)
        if (index >= 0) {
            with(density) {
                val itemPx = (28.dp + 6.dp).toPx()       // item height + spacing
                val paddingPx = 6.dp.toPx()               // Column top padding
                val itemCenterPx = paddingPx + index * itemPx + 14.dp.toPx()
                val viewportPx = 150.dp.toPx()
                val target = (itemCenterPx - viewportPx / 2f).coerceAtLeast(0f).toInt()
                scrollState.animateScrollTo(target)
            }
        }
    }

    Surface(
        modifier = modifier
            .width(72.dp)
            .height(150.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(2.dp, Color.Black)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (currentColor == color) 3.dp else 1.dp,
                            color = if (currentColor == color) Color.Black else Color.Gray,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(color) }
                )
            }
        }
    }
}

@Composable
fun PresetColorPickerDialog(
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    // Define your curated palette here
    val presetColors = listOf(
        Color.Black, Color.Blue, Color.Red, Color.Green, Color.Yellow, Color.DarkGray, Color.LightGray, Color.White,
         Color(0xFFE91E63), // Pink
        Color(0xFFFF9800), // Orange
          Color(0xFF4CAF50), // Lighter Green
         Color.Cyan, Color.Magenta, Color(0xFF9C27B0) // Purple
    )

    AlertDialog(
        onDismissRequest = onDismiss, // Closes if the user taps outside the dialog
        title = { Text("Select Pen Color") },
        text = {
            // Creates a grid with exactly 4 columns
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(presetColors) { color ->
                    // Each color is a simple circular Box
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape) // Makes the square Box a perfect circle
                            .background(color)
                            .clickable {
                                onColorSelected(color)
                                onDismiss() // Close the dialog after selection
                            }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameNoteDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Note Title") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(text)
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
