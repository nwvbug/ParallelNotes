package com.nvemuri.parallelnotes.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale

import com.nvemuri.parallelnotes.R
import com.nvemuri.parallelnotes.data.entities.PenStroke
import com.nvemuri.parallelnotes.data.entities.Point
import com.nvemuri.parallelnotes.utils.bezierSmoothStroke
import com.nvemuri.parallelnotes.utils.drawStroke
import com.nvemuri.parallelnotes.utils.isPointInPolygon
import com.nvemuri.parallelnotes.data.entities.CanvasElement
import com.nvemuri.parallelnotes.utils.getOverlappingChunkKeys
import com.nvemuri.parallelnotes.data.CanvasChunk
import com.nvemuri.parallelnotes.utils.detectMultiFingerTap

import android.graphics.Picture
import android.graphics.Paint as NativePaint
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint.Cap
import android.graphics.Paint.Join
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectTransformGestures
import com.nvemuri.parallelnotes.data.AppDatabase
import java.util.UUID

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
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
    var currentTool by remember { mutableStateOf(ActiveTool.DRAW)}

    //pen settings
    var isPenMenuOpen by remember { mutableStateOf(false)}
    var currentPenStyle by remember { mutableStateOf(PenStyle.SOLID) }
    // Increased default thickness since we're starting zoomed out
    var penThickness by remember { mutableFloatStateOf(35f) }
    var penColor by remember { mutableStateOf(Color.Black) }
    var isColorSelectorOpen by remember { mutableStateOf(false)}
    var arcSmoothingEnabled by remember { mutableStateOf(true) }
    var removeJitterAmount by remember { mutableFloatStateOf(25f) }
    var smoothCurrentStroke by remember { mutableStateOf(true) }
    //name states
    val noteTitle by viewModel.currentNoteTitle.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }

    // More Options Menu
    var isMoreMenuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Important Pen Menu
    var isImportantPenMenuOpen by remember { mutableStateOf(false) }
    val selectedImportantCategory by viewModel.selectedImportantCategory.collectAsState()

    Box(modifier = Modifier.fillMaxSize()){
        DrawingCanvas(currentTool, penThickness, penColor, arcSmoothingEnabled, smoothCurrentStroke, removeJitterAmount, viewModel)


        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            border = BorderStroke(3.dp, Color.Black)

        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    .padding(start = 105.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(3.dp, Color.Black)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TextButton(onClick = {
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
                    }) {
                        Text("Export to PDF", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isColorSelectorOpen) {
            PresetColorPickerDialog(
                onDismiss = { isColorSelectorOpen = false },
                onColorSelected = { selectedColor ->
                    penColor = selectedColor
                }
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

enum class PenStyle {
    SOLID,
    DASHED,
    HIGHLIGHTER
}



@Composable
fun DrawingCanvas(
    currentTool: ActiveTool,
    thickness: Float,
    pencolor: Color,
    arcSmoothing: Boolean,
    smoothCurrentStroke: Boolean,
    removeJitterAmount: Float,
    viewModel: NoteViewModel
) {
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

    //Bitmap States
    // state integer to force Compose to redraw when we mutate the bitmap
    var cacheVersion by remember { mutableIntStateOf(0) }
    var activeChunks by remember { mutableStateOf(mutableMapOf<String, CanvasChunk>()) }
    val CHUNK_SIZE = 512

    //Viewport states
    var viewportPan by remember { mutableStateOf(Offset.Zero) }
    // Start zoomed out (0.5x) to give a larger overview by default
    var viewportScale by remember { mutableFloatStateOf(0.5f) }

    val selectedImportantCategory by viewModel.selectedImportantCategory.collectAsState()

    // screen coord to actual canvas coord
    val screenToWorld: (Offset) -> Offset = { screenPos ->
        (screenPos - viewportPan) / viewportScale
    }
    LaunchedEffect(loadedElements) {
        if (loadedElements.isNotEmpty() && canvasElements.isEmpty()) {
            canvasElements = loadedElements
        }
    }

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
                    chunk.canvas.translate(-chunk.bounds.left, -chunk.bounds.top)

                    // Draw based on type
                    when (element) {
                        is PenStroke -> {
                            chunk.canvas.translate(element.minX, element.minY)
                            chunk.canvas.drawPicture(element.picture)
                        }
                        // is ImageElement -> ... (for later)
                    }
                    chunk.canvas.restore()
                }
            }
        }
        cacheVersion++ // Trigger Compose to re-render the chunks
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

    Canvas(
        modifier = Modifier
            .fillMaxSize()
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

                    },
                    onThreeFingerTap = {

                    }
                )
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
            .pointerInput(currentTool, removeJitterAmount, smoothCurrentStroke, arcSmoothing, pencolor, thickness, selectedImportantCategory){
                awaitEachGesture {
                    // POINTER DOWN
                    val down = awaitFirstDown()
                    down.consume()

                    // check if its a stylus and get pressure
                    val isStylus = down.type == PointerType.Stylus
                    if (!isStylus && (currentTool == ActiveTool.DRAW || currentTool == ActiveTool.IMPORTANT_PEN || currentTool == ActiveTool.ERASESTROKE)) return@awaitEachGesture //only take pen for drawing
                    val startPressure = down.pressure

                    // Streamline stuff (position)
                    var virtualBrush = screenToWorld(down.position)
                    val stringLength = removeJitterAmount

                    //streamline stuff (velocity)
                    var lastTime = down.uptimeMillis
                    var lastHardwarePos = screenToWorld(down.position)

                    val maxVelo = 5.0f // may need to be tweaked

                    //make a single dot if just tapped
                    if (currentTool == ActiveTool.DRAW || currentTool == ActiveTool.IMPORTANT_PEN) {
                        currentRawStroke = listOf(Point(screenToWorld(down.position), startPressure))

                    }

                    //determining what theyre doing if its lasso
                    if (currentTool == ActiveTool.LASSO) {
                        // Check if they tapped inside an active selection
                        if (selectedElements.isNotEmpty() && isPointInPolygon(
                                virtualBrush,
                                lassoPath
                            )
                        ) {
                            isDraggingSelection = true
                            dragLastPosition = virtualBrush
                        }
                    }
                    // clear lasso if they tapped outside regardless of what tool is selected
                    if (selectedElements.isNotEmpty() && !isPointInPolygon(virtualBrush, lassoPath)) {

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
                                    chunk.canvas.translate(-chunk.bounds.left, -chunk.bounds.top)

                                    when (element) {
                                        is PenStroke -> {
                                            chunk.canvas.translate(element.minX, element.minY)
                                            chunk.canvas.drawPicture(element.picture)
                                        }
                                        // is ImageElement -> ...
                                    }
                                    chunk.canvas.restore()
                                }
                            }
                            cacheVersion++ // Trigger UI update

                            canvasElements = canvasElements + selectedElements
                            selectedElements = emptyList()
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
                            if (currentTool == ActiveTool.DRAW || currentTool == ActiveTool.IMPORTANT_PEN) {
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
                            else if (currentTool == ActiveTool.ERASESTROKE) {

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
                                        // dont erase other types
                                    }
                                }

                                if (toErase.isNotEmpty()) {
                                    // Remove them from the main list immediately
                                    canvasElements = canvasElements.filterNot { it in toErase }
                                    
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

                            } else if (currentTool == ActiveTool.LASSO) {
                                if (isDraggingSelection) {
                                    // Calculate the distance moved since the last frame
                                    val dx = stylusPos.x - dragLastPosition.x
                                    val dy = stylusPos.y - dragLastPosition.y

                                    // Translate all selected strokes to new location
                                    selectedElements = selectedElements.map { element ->
                                        element.translate(dx, dy)
                                    }

                                    // Translate the lasso path so the lasso moves with the ink
                                    lassoPath = lassoPath.map { offset ->
                                        Offset(offset.x + dx, offset.y + dy)
                                    }

                                    dragLastPosition = stylusPos
                                } else {
                                    // Just drawing the lasso loop
                                    lassoPath = lassoPath + stylusPos
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // FOR LASSO, CHECK IF THE USER CAPTURED ANYTHING
                    if (currentTool == ActiveTool.LASSO && lassoPath.isNotEmpty()) {
                        val newlySelected = mutableListOf<CanvasElement>()
                        val unselected = mutableListOf<CanvasElement>()

                        for (element in canvasElements) {
                            val isSelected = when (element) {
                                is PenStroke -> element.points.any { isPointInPolygon(it.offset, lassoPath) }
                                else -> false
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

                        // Pad the bounding box by the radius of the thickest possible point
                        // user specified thickness * mapped pressure max /2f
                        val padding = thickness * (0.2f + (1 * 0.8f)) / 2f

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
                            strokeCap = Cap.ROUND
                            strokeJoin = Join.ROUND
                        }
                        val pointsToSave = if (arcSmoothing) {
                            bezierSmoothStroke(currentRawStroke)
                        } else {
                            currentRawStroke
                        }
                        // handle it depending on if its a dot or line
                        if (pointsToSave.size == 1) { //if it is just a single dot
                            val singlePoint = pointsToSave.first()
                            nativePaint.strokeWidth = (0.2f + (singlePoint.pressure * 0.8f)) * thickness
                            nativeCanvas.drawPoint(
                                singlePoint.offset.x - minX,
                                singlePoint.offset.y - minY,
                                nativePaint
                            )
                        } else { //if it is an actual stroke
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
                        
                        if (currentTool == ActiveTool.IMPORTANT_PEN) {
                            viewModel.processImportantStroke(newStroke)
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
                    picture = Picture(),
                    minX = 0f,
                    minY = 0f,
                    maxX = 0f,
                    maxY = 0f
                ) //create temp stroke with empty pic to pass to drawStroke
                drawStroke(currentStroke, thickness)
            }
            //selected strokes use more optimized picture movement
            drawIntoCanvas { canvas ->
                selectedElements.forEach { stroke ->
                    canvas.save()

                    canvas.translate(stroke.minX, stroke.minY)

                    if (stroke is PenStroke) {
                        canvas.nativeCanvas.drawPicture(stroke.picture)
                    }

                    canvas.restore()
                }
            }

            //draw the lasso path if it exists
            if (lassoPath.size > 1) {
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
        }
        // non transformed things
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
        modifier = Modifier.padding(start = 16.dp) // Space between toolbar and menu
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
fun PresetColorPickerDialog(
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    // Define your curated palette here
    val presetColors = listOf(
        Color.Black, Color.DarkGray, Color.LightGray, Color.White,
        Color.Red, Color(0xFFE91E63), // Pink
        Color(0xFFFF9800), // Orange
        Color.Yellow, Color.Green, Color(0xFF4CAF50), // Lighter Green
        Color.Blue, Color.Cyan, Color.Magenta, Color(0xFF9C27B0) // Purple
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
