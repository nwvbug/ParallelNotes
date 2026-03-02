package com.nvemuri.parallelnotes.ui// Change this to match your package name!

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.layout.ContentScale
import com.nvemuri.parallelnotes.R
import com.nvemuri.parallelnotes.utils.PenStroke
import com.nvemuri.parallelnotes.utils.Point
import com.nvemuri.parallelnotes.utils.bezierSmoothStroke
import com.nvemuri.parallelnotes.utils.drawStroke
import com.nvemuri.parallelnotes.utils.generatePathFromPoints
import com.nvemuri.parallelnotes.utils.isPointInPolygon
import android.graphics.Picture
import android.graphics.Paint as NativePaint
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint.Cap
import android.graphics.Paint.Join
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContent is the entry point, similar to rendering a root component in React
        setContent {
            PenNoteApp()
        }
    }
}


@Composable
fun NoteTakingScreen(onNavigateHome: () -> Unit){
    var currentTool by remember { mutableStateOf(ActiveTool.DRAW)}

    //pen settings
    var isPenMenuOpen by remember { mutableStateOf(false)}
    var currentPenStyle by remember { mutableStateOf(PenStyle.SOLID) }
    var penThickness by remember { mutableFloatStateOf(15f) }
    var penColor by remember { mutableStateOf(Color.Black) }
    var isColorSelectorOpen by remember { mutableStateOf(false)}
    var arcSmoothingEnabled by remember { mutableStateOf(true) }
    var removeJitterAmount by remember { mutableFloatStateOf(15f) }
    var smoothCurrentStroke by remember { mutableStateOf(false) }


    Box(modifier = Modifier.fillMaxSize()){
        DrawingCanvas(currentTool, penThickness, penColor, arcSmoothingEnabled, smoothCurrentStroke, removeJitterAmount)


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

        if (isColorSelectorOpen) {
            PresetColorPickerDialog(
                onDismiss = { isColorSelectorOpen = false },
                onColorSelected = { selectedColor ->
                    penColor = selectedColor
                }
            )
        }
    }
}


enum class ActiveTool {
    DRAW,
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
    removeJitterAmount: Float
) {
    //Drawing States
    var completedStrokes by remember { mutableStateOf(emptyList<PenStroke>()) }
    var currentRawStroke by remember { mutableStateOf(emptyList<Point>())}

    //Cursor State
    var cursorPosition by remember { mutableStateOf<Offset?>(null) }

    //Lasso States
    var lassoPath  by remember {mutableStateOf(emptyList<Offset>())}
    var selectedStrokes by remember { mutableStateOf(emptyList<PenStroke>()) }
    var isDraggingSelection by remember { mutableStateOf(false) }
    var dragLastPosition by remember { mutableStateOf(Offset.Zero) }
    var cacheBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var cacheCanvas by remember { mutableStateOf<android.graphics.Canvas?>(null) }
    // state integer to force Compose to redraw when we mutate the bitmap
    var cacheVersion by remember { mutableIntStateOf(0) }

    // Helper function for full rebuilds (Eraser and Lasso pickup)
    val rebuildCache: (List<PenStroke>) -> Unit = { strokes ->
        cacheCanvas?.let { canvas ->
            // Clear the bitmap completely
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            // Redraw all remaining strokes
            strokes.forEach { stroke ->
                canvas.save()
                canvas.translate(stroke.minX, stroke.minY)
                canvas.drawPicture(stroke.picture)
                canvas.restore()
            }
            cacheVersion++ // Trigger UI update
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    // Create a mutable Android Bitmap
                    val androidBmp = android.graphics.Bitmap.createBitmap(
                        size.width, size.height, android.graphics.Bitmap.Config.ARGB_8888
                    )
                    cacheBitmap = androidBmp.asImageBitmap()
                    cacheCanvas = android.graphics.Canvas(androidBmp)

                    // If there are already strokes (e.g., on device rotation), draw them
                    rebuildCache(completedStrokes)
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
            .pointerInput(currentTool, removeJitterAmount, smoothCurrentStroke, arcSmoothing, pencolor, thickness){
                awaitEachGesture {
                    // POINTER DOWN
                    val down = awaitFirstDown()
                    down.consume()

                    // check if its a stylus and get pressure
                    val isStylus = down.type == PointerType.Stylus
                    if (!isStylus && (currentTool == ActiveTool.DRAW || currentTool == ActiveTool.ERASESTROKE)) return@awaitEachGesture //only take pen for drawing
                    val startPressure = down.pressure

                    // Streamline stuff (position)
                    var virtualBrush = down.position
                    val stringLength = removeJitterAmount

                    //streamline stuff (velocity)
                    var lastTime = down.uptimeMillis
                    var lastHardwarePos = down.position

                    val maxVelo = 5.0f // may need to be tweaked

                    //make a single dot if just tapped
                    if (currentTool == ActiveTool.DRAW) {
                        currentRawStroke = listOf(Point(down.position, startPressure))

                    }

                    //determining what theyre doing if its lasso
                    if (currentTool == ActiveTool.LASSO) {
                        // Check if they tapped inside an active selection
                        if (selectedStrokes.isNotEmpty() && isPointInPolygon(
                                virtualBrush,
                                lassoPath
                            )
                        ) {
                            isDraggingSelection = true
                            dragLastPosition = virtualBrush
                        }
                    }
                    // clear lasso if they tapped outside regardless of what tool is selected
                    if (selectedStrokes.isNotEmpty() && !isPointInPolygon(virtualBrush, lassoPath)) {
                        // tapped outside, commit ink back to bitmap
                        if (selectedStrokes.isNotEmpty()) {
                            cacheCanvas?.let { canvas ->
                                selectedStrokes.forEach { stroke ->
                                    canvas.save()
                                    canvas.translate(stroke.minX, stroke.minY)
                                    canvas.drawPicture(stroke.picture)
                                    canvas.restore()
                                }
                                cacheVersion++
                            }
                            completedStrokes = completedStrokes + selectedStrokes
                            selectedStrokes = emptyList()
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

                            if (currentTool == ActiveTool.DRAW) {
                                val movePressure = if (change.type == PointerType.Stylus) change.pressure else 1.0f
                                val stylusPos = change.position //position of actual pointer
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
                                // Check with vectors if user is touching a stroke, if so, rebuild bitmap
                                val eraserRadius = 50f
                                val oldSize = completedStrokes.size
                                completedStrokes = completedStrokes.filterNot { stroke -> //see if user is touching any strokes
                                    stroke.points.any { point ->
                                        (point.offset - change.position).getDistance() < eraserRadius
                                    }
                                }
                                // If we actually deleted something, rebuild the cache
                                if (completedStrokes.size != oldSize) {
                                    rebuildCache(completedStrokes)
                                }
                            } else if (currentTool == ActiveTool.LASSO) {
                                if (isDraggingSelection) {
                                    // Calculate the distance moved since the last frame
                                    val dx = change.position.x - dragLastPosition.x
                                    val dy = change.position.y - dragLastPosition.y

                                    // Translate all selected strokes to new location
                                    selectedStrokes = selectedStrokes.map { stroke ->
                                        PenStroke(
                                            points = stroke.points.map { point ->
                                                Point(Offset(point.offset.x + dx, point.offset.y + dy), point.pressure)
                                            },
                                            thickness = stroke.thickness,
                                            color = stroke.color,
                                            picture = stroke.picture,
                                            minX = stroke.minX + dx,
                                            maxX = stroke.maxX + dx,
                                            minY = stroke.minY + dy,
                                            maxY = stroke.maxY + dy
                                        )
                                    }

                                    // Translate the lasso path so the lasso moves with the ink
                                    lassoPath = lassoPath.map { offset ->
                                        Offset(offset.x + dx, offset.y + dy)
                                    }

                                    dragLastPosition = change.position
                                } else {
                                    // Just drawing the lasso loop
                                    lassoPath = lassoPath + change.position
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // FOR LASSO, CHECK IF THE USER CAPTURED ANYTHING
                    if (currentTool == ActiveTool.LASSO && lassoPath.isNotEmpty()) {
                        // 2. Separate the strokes based on the Ray-Casting algorithm
                        val newlySelected = mutableListOf<PenStroke>()
                        val unselected = mutableListOf<PenStroke>()

                        for (stroke in completedStrokes) {
                            // If at least one point in the stroke is inside the lasso, select the whole stroke
                            val isSelected = stroke.points.any { point ->
                                isPointInPolygon(point.offset, lassoPath)
                            }

                            if (isSelected) {
                                newlySelected.add(stroke)
                            } else {
                                unselected.add(stroke)
                            }
                        }

                        // 3. Update the state
                        selectedStrokes = selectedStrokes + newlySelected
                        completedStrokes = unselected

                        // If we successfully picked something up off the canvas, rebuild the cache without it
                        if (newlySelected.isNotEmpty()) {
                            rebuildCache(completedStrokes)
                        }

                        if (selectedStrokes.isEmpty()){
                            lassoPath = emptyList()
                        }
                        //lassoPath = emptyList() // Clear the lasso line from the screen
                    }

                    // IF DRAW, ADD LAST STROKE TO COMPLETED
                    if (currentTool == ActiveTool.DRAW && currentRawStroke.isNotEmpty()) {
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
                        val nativePaint = NativePaint().apply {
                            color = pencolor.toArgb() // Convert Compose Color to Native Color
                            isAntiAlias = true
                            strokeCap = Cap.ROUND
                            strokeJoin = Join.ROUND
                        }
                        val pointsToSave = if (arcSmoothing) {
                            bezierSmoothStroke(currentRawStroke)
                        } else {
                            currentRawStroke
                        }
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

                        val newStroke = PenStroke(
                            points = pointsToSave,
                            picture = picture,
                            thickness = thickness,
                            color = pencolor,
                            minX = minX, minY = minY, maxX = maxX, maxY = maxY
                        )
                        cacheCanvas?.let { canvas ->
                            canvas.save()
                            canvas.translate(newStroke.minX, newStroke.minY)
                            canvas.drawPicture(newStroke.picture)
                            canvas.restore()
                            cacheVersion++
                        }

                        completedStrokes = completedStrokes + newStroke
                        currentRawStroke = emptyList()
                    }
                }
            }
    ) {
        // Draw the strokes
        // efficient displaying with bitmap for completed strokes

        cacheBitmap?.let { bmp ->
            // We read cacheVersion so Compose knows to redraw when it changes
            val trigger = cacheVersion
            drawImage(
                image = bmp,
                topLeft = Offset.Zero
            )
        }
        // less efficient drawing with points for current stroke
        if (currentRawStroke.isNotEmpty()) {
            val strokeToDraw = if (smoothCurrentStroke && arcSmoothing) {
                bezierSmoothStroke(currentRawStroke)
            } else {
                currentRawStroke
            }
            val currentStroke = PenStroke(strokeToDraw, thickness, pencolor, Picture(), 0f, 0f, 0f, 0f) //create temp stroke with empty pic to pass to drawStroke
            drawStroke(currentStroke, thickness)
        }
        //selected strokes use more optimized picture movement, but not as optimized as bitmap
        drawIntoCanvas { canvas ->
            selectedStrokes.forEach { stroke ->
                canvas.save()

                canvas.translate(stroke.minX, stroke.minY)

                canvas.nativeCanvas.drawPicture(stroke.picture)

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

        // move the cursor
        cursorPosition?.let { pos ->
            if (currentTool == ActiveTool.DRAW) {
                // A small, solid black dot
                drawCircle(
                    color = Color.Black,
                    radius = 5f,
                    center = pos,
                    style = Stroke(width = 2f)
                )
            } else if (currentTool == ActiveTool.ERASESTROKE) {
                // A large, semi-transparent circle matching your eraser radius
                drawCircle(
                    color = Color.Gray,
                    radius = 50f, // This matches the 50f radius in your eraser logic
                    center = pos,
                    alpha = 0.5f  // Equivalent to setting opacity in CSS
                )
            }
        }
    }
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
                        valueRange = 1f..50f,
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