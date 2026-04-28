package com.nvemuri.parallelnotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.nvemuri.parallelnotes.data.NoteDao
import com.nvemuri.parallelnotes.data.NoteEntity
import com.nvemuri.parallelnotes.data.entities.CanvasElement
import com.nvemuri.parallelnotes.data.entities.PenStroke
import com.nvemuri.parallelnotes.data.entities.toSerializable
import com.nvemuri.parallelnotes.data.toCanvasElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import android.content.Context
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import com.nvemuri.parallelnotes.data.ImportantStrokeDao
import com.nvemuri.parallelnotes.data.ImportantCategoryDao
import com.nvemuri.parallelnotes.data.entities.ImportantStrokeEntity
import com.nvemuri.parallelnotes.data.entities.ImportantCategoryEntity
import com.nvemuri.parallelnotes.data.entities.ImageElement
import com.nvemuri.parallelnotes.data.entities.SerializableElement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

class NoteViewModel(
    private val context: Context,
    private val noteDao: NoteDao,
    private val importantStrokeDao: ImportantStrokeDao,
    private val importantCategoryDao: ImportantCategoryDao
) : ViewModel() {
    private val _currentNoteTitle = MutableStateFlow("Untitled Note")
    val currentNoteTitle: StateFlow<String> = _currentNoteTitle.asStateFlow()

    private val _currentFolder = MutableStateFlow("MyFolder")
    val currentFolder: StateFlow<String> = _currentFolder.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedImportantCategory = MutableStateFlow<ImportantCategoryEntity?>(null)
    val selectedImportantCategory: StateFlow<ImportantCategoryEntity?> = _selectedImportantCategory.asStateFlow()

    private val _lastProcessedStrokeBounds = MutableStateFlow<StrokeBoundsInfo?>(null)
    val lastProcessedStrokeBounds: StateFlow<StrokeBoundsInfo?> = _lastProcessedStrokeBounds.asStateFlow()

    private var autosaveJob: Job? = null

    data class StrokeBoundsInfo(
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float,
        val colorArgb: Int
    )

    init {
        syncDatabaseWithFiles()
    }

    private fun syncDatabaseWithFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = context.filesDir.listFiles { _, name -> name.endsWith(".json") } ?: return@launch
            val existingNotes = noteDao.getAllNotes().first()
            val existingIds = existingNotes.map { it.noteId }.toSet()

            files.forEach { file ->
                val noteId = file.nameWithoutExtension
                if (!existingIds.contains(noteId)) {
                    val recoveredNote = NoteEntity(
                        noteId = noteId,
                        title = "Recovery Note (${noteId.take(4)})",
                        lastModified = file.lastModified(),
                        folder = "MyFolder"
                    )
                    noteDao.insertOrUpdateNote(recoveredNote)
                }
            }
        }
    }

    fun resetCurrentNote() {
        autosaveJob?.cancel()
        currentNoteId = ""
        _currentElements.value = emptyList()
        _currentNoteTitle.value = "Untitled Note"
        // Do NOT reset _currentFolder here if want the home screen
        // to stay on the folder you were just viewing.
    }

    private fun scheduleAutosave() {
        if (currentNoteId.isEmpty()) return // Don't autosave if no note is active

        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(3000) // Wait for 3 seconds of inactivity before autosaving
            saveNote(_currentNoteTitle.value, _currentElements.value, isAutoSave = true)
        }
    }

    fun updateTitle(newTitle: String) {
        _currentNoteTitle.value = newTitle
        scheduleAutosave()
    }

    fun updateFolder(folderName: String) {
        _currentFolder.value = folderName
        scheduleAutosave()
    }

    val allNotes: StateFlow<List<NoteEntity>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFolders: StateFlow<List<String>> = noteDao.getAllFolders()
        .map { folders -> 
            if (folders.isEmpty()) listOf("MyFolder") 
            else if (!folders.contains("MyFolder")) listOf("MyFolder") + folders
            else folders
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("MyFolder"))

    @OptIn(ExperimentalCoroutinesApi::class)
    val importantStrokes: StateFlow<List<ImportantStrokeEntity>> = _currentFolder
        .flatMapLatest { folder -> importantStrokeDao.getImportantStrokesForFolder(folder) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val importantCategories: StateFlow<List<ImportantCategoryEntity>> = importantCategoryDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentElements = MutableStateFlow<List<CanvasElement>>(emptyList())
    fun updateCurrentElements(elements: List<CanvasElement>) {
        _currentElements.value = elements
        scheduleAutosave()
    }
    val currentElements: StateFlow<List<CanvasElement>> = _currentElements.asStateFlow()

    var currentNoteId: String = ""
        private set

    fun selectImportantCategory(category: ImportantCategoryEntity?) {
        _selectedImportantCategory.value = category
    }

    fun createImportantCategory(name: String, color: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            importantCategoryDao.insertOrUpdate(ImportantCategoryEntity(name, color))
        }
    }

    fun deleteImportantCategory(category: ImportantCategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            importantCategoryDao.delete(category)
        }
    }

    fun loadNote(noteId: String) {
        currentNoteId = noteId
        _currentElements.value = emptyList()
        _isLoading.value = true
        autosaveJob?.cancel()

        viewModelScope.launch {
            try {
                val note = withContext(Dispatchers.IO) { noteDao.getNoteById(noteId) }
                if (note != null) {
                    _currentNoteTitle.value = note.title
                    _currentFolder.value = note.folder

                    val file = File(context.filesDir, "${noteId}.json")
                    if (file.exists()) {
                        val rebuiltElements: List<CanvasElement> = withContext(Dispatchers.Default) {
                            val jsonString = file.readText()
                            val serializableData = Json.decodeFromString<List<SerializableElement>>(jsonString)
                            serializableData.map { it.toCanvasElement(context) }
                        }
                        _currentElements.value = rebuiltElements
                    }
                } else {
                    _currentNoteTitle.value = "Untitled Note"
                    _currentFolder.value = "MyFolder"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveNote(title: String, elements: List<CanvasElement>, isAutoSave: Boolean = false) {
        if (isAutoSave && currentNoteId.isEmpty()) {
            if (elements.isEmpty() && (title == "Untitled Note" || title.isBlank())) {
                return
            }
        }
        if (!isAutoSave) {
            autosaveJob?.cancel()
        }

        if (currentNoteId.isEmpty()) {
            if (isAutoSave && elements.isEmpty() && title == "Untitled Note") return
            currentNoteId = UUID.randomUUID().toString()
        }
        val idToSave = currentNoteId
        val folderToSave = _currentFolder.value

        if (!isAutoSave) _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val serializableData = elements.map { it.toSerializable() }
                    val jsonString = Json.encodeToString(serializableData)
                    val file = File(context.filesDir, "${idToSave}.json")
                    file.writeText(jsonString)
                }

                val existingNote = withContext(Dispatchers.IO) { noteDao.getNoteById(idToSave) }
                val colorToSave = existingNote?.colorArgb ?: -16777216

                val newNote = NoteEntity(
                    noteId = idToSave,
                    title = title,
                    lastModified = System.currentTimeMillis(),
                    folder = folderToSave,
                    colorArgb = colorToSave
                )
                withContext(Dispatchers.IO) {
                    noteDao.insertOrUpdateNote(newNote)
                }
            } finally {
                if (!isAutoSave) _isLoading.value = false
            }
        }
    }

    fun createNewNote(folder: String = "MyFolder") {
        currentNoteId = UUID.randomUUID().toString()
        _currentElements.value = emptyList()
        _currentNoteTitle.value = "Untitled Note"
        _currentFolder.value = folder
        autosaveJob?.cancel()
    }

    fun processImportantStroke(newStroke: PenStroke) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = _currentFolder.value
            val category = _selectedImportantCategory.value
            val categoryName = category?.name ?: "Important"
            val categoryColor = category?.colorArgb ?: 0xFFFFD700.toInt()
            
            val existingStrokes = importantStrokeDao.getImportantStrokesForFolderSync(folder)
            
            val threshold = 300f
            
            val nearbyStroke = existingStrokes.find { existing ->
                val dist = distanceBetweenRects(
                    existing.minX, existing.minY, existing.maxX, existing.maxY,
                    newStroke.minX, newStroke.minY, newStroke.maxX, newStroke.maxY
                )
                dist < threshold && existing.noteId == currentNoteId && existing.categoryName == categoryName
            }

            val combinedBounds = if (nearbyStroke != null) {
                // Calculate combined bounding box for merged group
                val mergedMinX = min(nearbyStroke.minX, newStroke.minX)
                val mergedMinY = min(nearbyStroke.minY, newStroke.minY)
                val mergedMaxX = max(nearbyStroke.maxX, newStroke.maxX)
                val mergedMaxY = max(nearbyStroke.maxY, newStroke.maxY)
                
                // Update database
                val updatedElements = nearbyStroke.serializedElements + newStroke.toSerializable()
                val updatedStroke = nearbyStroke.copy(
                    serializedElements = updatedElements,
                    minX = mergedMinX,
                    minY = mergedMinY,
                    maxX = mergedMaxX,
                    maxY = mergedMaxY,
                    timestamp = System.currentTimeMillis()
                )
                importantStrokeDao.insertOrUpdate(updatedStroke)
                
                StrokeBoundsInfo(mergedMinX, mergedMinY, mergedMaxX, mergedMaxY, categoryColor)
            } else {
                // Create new entry
                val newImportantStroke = ImportantStrokeEntity(
                    folderName = folder,
                    noteId = currentNoteId,
                    noteTitle = _currentNoteTitle.value,
                    serializedElements = listOf(newStroke.toSerializable()),
                    minX = newStroke.minX,
                    maxX = newStroke.maxX,
                    minY = newStroke.minY,
                    maxY = newStroke.maxY,
                    colorArgb = categoryColor,
                    categoryName = categoryName
                )
                importantStrokeDao.insertOrUpdate(newImportantStroke)
                
                StrokeBoundsInfo(newStroke.minX, newStroke.minY, newStroke.maxX, newStroke.maxY, categoryColor)
            }
            
            // Update state with combined bounds for UI to display
            _lastProcessedStrokeBounds.value = combinedBounds
        }
    }

    fun removeImportantStrokes(erasedElements: List<CanvasElement>) {
        val erasedIds = erasedElements.map { it.id }.toSet()
        viewModelScope.launch(Dispatchers.IO) {
            val folder = _currentFolder.value
            val noteId = currentNoteId
            
            if (noteId.isEmpty()) {
                return@launch
            }
            
            val existingStrokes = importantStrokeDao.getImportantStrokesForNoteSync(folder, noteId)
            
            existingStrokes.forEach { importantStroke ->
                val remainingElements = importantStroke.serializedElements.filterNot { it.id in erasedIds }
                
                if (remainingElements.size < importantStroke.serializedElements.size) {
                    if (remainingElements.isEmpty()) {
                        importantStrokeDao.delete(importantStroke)
                    } else {
                        var minX = Float.MAX_VALUE
                        var minY = Float.MAX_VALUE
                        var maxX = -Float.MAX_VALUE
                        var maxY = -Float.MAX_VALUE
                        
                        remainingElements.forEach { 
                            minX = min(minX, it.minX)
                            minY = min(minY, it.minY)
                            maxX = max(maxX, it.maxX)
                            maxY = max(maxY, it.maxY)
                        }
                        
                        val updatedStroke = importantStroke.copy(
                            serializedElements = remainingElements,
                            minX = minX,
                            minY = minY,
                            maxX = maxX,
                            maxY = maxY,
                            timestamp = System.currentTimeMillis()
                        )
                        importantStrokeDao.insertOrUpdate(updatedStroke)
                    }
                }
            }
        }
    }

    fun deleteImportantStroke(stroke: ImportantStrokeEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            importantStrokeDao.delete(stroke)
        }
    }

    private fun distanceBetweenRects(
        ax1: Float, ay1: Float, ax2: Float, ay2: Float,
        bx1: Float, by1: Float, bx2: Float, by2: Float
    ): Float {
        val dx = max(0f, max(ax1 - bx2, bx1 - ax2))
        val dy = max(0f, max(ay1 - by2, by1 - ay2))
        return sqrt(dx * dx + dy * dy)
    }

    fun moveNoteToFolder(noteId: String, newFolder: String) {
        viewModelScope.launch {
            val note = withContext(Dispatchers.IO) { noteDao.getNoteById(noteId) }
            if (note != null) {
                val updatedNote = note.copy(folder = newFolder)
                withContext(Dispatchers.IO) {
                    noteDao.insertOrUpdateNote(updatedNote)
                }
            }
        }
    }

    fun updateNoteColor(noteId: String, colorArgb: Int) {
        viewModelScope.launch {
            val note = withContext(Dispatchers.IO) { noteDao.getNoteById(noteId) }
            if (note != null) {
                val updatedNote = note.copy(colorArgb = colorArgb)
                withContext(Dispatchers.IO) {
                    noteDao.insertOrUpdateNote(updatedNote)
                }
            }
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            noteDao.deleteNote(note)
            val file = File(context.filesDir, "${note.noteId}.json")
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun exportToPdf(onComplete: (File?) -> Unit) {
        val elements = _currentElements.value
        if (elements.isEmpty()) {
            onComplete(null)
            return
        }

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE

                // 1. Efficient bounds calculation
                elements.forEach { element ->
                    if (element.minX < minX) minX = element.minX
                    if (element.minY < minY) minY = element.minY
                    if (element.maxX > maxX) maxX = element.maxX
                    if (element.maxY > maxY) maxY = element.maxY
                }

                // US Letter width in PDF points (72 DPI = 8.5" wide), custom height
                val pageWidth = 612f
                val pagePadding = 36f // 0.5-inch margins

                val contentWidth = (maxX - minX).coerceAtLeast(1f)
                val contentHeight = (maxY - minY).coerceAtLeast(1f)

                // Scale so content fills the letter width; height follows proportionally
                val scale = (pageWidth - pagePadding * 2) / contentWidth
                val pageHeight = (contentHeight * scale + pagePadding * 2).toInt().coerceAtLeast(100)

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // 2. Translate to margin, scale to page units, then shift content origin to (0,0)
                canvas.translate(pagePadding, pagePadding)
                canvas.scale(scale, scale)
                canvas.translate(-minX, -minY)

                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    style = android.graphics.Paint.Style.STROKE
                }

                // 3. Decimation threshold: skip points closer than 2px (imperceptible at PDF scale)
                val distThresholdSq = 2.0f * 2.0f
                // Pressure quantization: 8 discrete levels reduces path-flush frequency
                // while preserving visible variation (each level = 12.5% change)
                val pressureLevels = 8

                // Draw in z-order so images and strokes layer correctly
                elements.sortedBy { it.zIndex }.forEach { element ->
                    when (element) {
                        is PenStroke -> {
                            paint.color = element.color.toArgb()
                            val pts = element.points
                            if (pts.isEmpty()) return@forEach

                            if (pts.size == 1) {
                                val pt = pts[0]
                                val qp = (round(pt.pressure * pressureLevels).toFloat() / pressureLevels).coerceIn(0f, 1f)
                                paint.strokeWidth = (0.2f + qp * 0.8f) * element.thickness
                                canvas.drawPoint(pt.offset.x, pt.offset.y, paint)
                            } else {
                                // Batch consecutive segments with the same quantized stroke width
                                // into a single Path, cutting PDF path-operation count from
                                // O(points) down to O(strokes × pressureLevels).
                                var currentWidth = -1f
                                var path = android.graphics.Path()
                                var pathHasContent = false
                                var lastPt = pts[0]

                                fun flushPath() {
                                    if (pathHasContent) {
                                        canvas.drawPath(path, paint)
                                        path = android.graphics.Path()
                                        pathHasContent = false
                                    }
                                }

                                for (i in 1 until pts.size) {
                                    val currPt = pts[i]
                                    val dx = currPt.offset.x - lastPt.offset.x
                                    val dy = currPt.offset.y - lastPt.offset.y
                                    if (dx * dx + dy * dy < distThresholdSq && i != pts.size - 1) continue

                                    val qp = (round(currPt.pressure * pressureLevels).toFloat() / pressureLevels).coerceIn(0f, 1f)
                                    val newWidth = (0.2f + qp * 0.8f) * element.thickness

                                    if (newWidth != currentWidth) {
                                        flushPath()
                                        currentWidth = newWidth
                                        paint.strokeWidth = currentWidth
                                        // Start new path from the last drawn point so there's no gap
                                        path.moveTo(lastPt.offset.x, lastPt.offset.y)
                                        pathHasContent = false
                                    }

                                    path.lineTo(currPt.offset.x, currPt.offset.y)
                                    pathHasContent = true
                                    lastPt = currPt
                                }
                                flushPath()
                            }
                        }
                        is ImageElement -> {
                            val file = File(context.filesDir, element.imagePath)
                            if (file.exists()) {
                                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                if (bitmap != null) {
                                    val dst = android.graphics.RectF(element.minX, element.minY, element.maxX, element.maxY)
                                    canvas.drawBitmap(bitmap, null, dst, null)
                                    bitmap.recycle()
                                }
                            }
                        }
                        else -> { /* unknown element type */ }
                    }
                }

                pdfDocument.finishPage(page)

                // Save with a unique timestamp to prevent caching issues in share intents
                val fileName = "${_currentNoteTitle.value.filter { it.isLetterOrDigit() }}_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                
                try {
                    file.outputStream().use { out ->
                        pdfDocument.writeTo(out)
                    }
                    pdfDocument.close()
                    withContext(Dispatchers.Main) { onComplete(file) }
                } catch (e: Exception) {
                    pdfDocument.close()
                    withContext(Dispatchers.Main) { onComplete(null) }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }
}



class NoteViewModelFactory(
    private val context: Context,
    private val noteDao: NoteDao,
    private val importantStrokeDao: ImportantStrokeDao,
    private val importantCategoryDao: ImportantCategoryDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(context, noteDao, importantStrokeDao, importantCategoryDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
