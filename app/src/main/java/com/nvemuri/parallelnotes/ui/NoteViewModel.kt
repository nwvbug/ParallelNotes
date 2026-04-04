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
import com.nvemuri.parallelnotes.data.entities.SerializableElement
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import kotlin.math.abs

class NoteViewModel(private val context: Context, private val noteDao: NoteDao) : ViewModel() {
    private val _currentNoteTitle = MutableStateFlow("Untitled Note")
    val currentNoteTitle: StateFlow<String> = _currentNoteTitle.asStateFlow()

    private val _currentFolder = MutableStateFlow("MyFolder")
    val currentFolder: StateFlow<String> = _currentFolder.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    fun updateTitle(newTitle: String) {
        _currentNoteTitle.value = newTitle
    }

    fun updateFolder(folderName: String) {
        _currentFolder.value = folderName
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

    private val _currentElements = MutableStateFlow<List<CanvasElement>>(emptyList())
    fun updateCurrentElements(elements: List<CanvasElement>) {
        _currentElements.value = elements
    }
    val currentElements: StateFlow<List<CanvasElement>> = _currentElements.asStateFlow()

    var currentNoteId: String = ""
        private set

    fun loadNote(noteId: String) {
        currentNoteId = noteId
        _currentElements.value = emptyList()
        _isLoading.value = true

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
                            serializableData.map { it.toCanvasElement() }
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

    fun saveNote(title: String, elements: List<CanvasElement>) {
        if (currentNoteId.isEmpty()) {
            currentNoteId = UUID.randomUUID().toString()
        }
        val idToSave = currentNoteId
        val folderToSave = _currentFolder.value

        _isLoading.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val serializableData = elements.map { it.toSerializable() }
                    val jsonString = Json.encodeToString(serializableData)
                    val file = File(context.filesDir, "${idToSave}.json")
                    file.writeText(jsonString)
                }

                val newNote = NoteEntity(
                    noteId = idToSave,
                    title = title,
                    lastModified = System.currentTimeMillis(),
                    folder = folderToSave
                )
                withContext(Dispatchers.IO) {
                    noteDao.insertOrUpdateNote(newNote)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createNewNote(folder: String = "MyFolder") {
        currentNoteId = UUID.randomUUID().toString()
        _currentElements.value = emptyList()
        _currentNoteTitle.value = "Untitled Note"
        _currentFolder.value = folder
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

                val padding = 50f
                val width = (maxX - minX + padding * 2).toInt().coerceAtLeast(100)
                val height = (maxY - minY + padding * 2).toInt().coerceAtLeast(100)

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // 2. Global translation instead of per-stroke translation
                canvas.translate(-minX + padding, -minY + padding)

                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }

                // 3. Decimation thresholds: only draw significant changes
                val distThresholdSq = 1.0f * 1.0f // 1pt distance (at 72dpi, this is enough)
                val pressureThreshold = 0.05f     // 5% pressure change

                elements.forEach { element ->
                    if (element is PenStroke) {
                        paint.color = element.color.toArgb()
                        val pts = element.points
                        if (pts.isEmpty()) return@forEach
                        
                        var lastDrawnPt = pts[0]
                        var lastDrawnPressure = lastDrawnPt.pressure
                        
                        if (pts.size == 1) {
                            paint.strokeWidth = (0.2f + (lastDrawnPressure * 0.8f)) * element.thickness
                            canvas.drawPoint(lastDrawnPt.offset.x, lastDrawnPt.offset.y, paint)
                        } else {
                            for (i in 1 until pts.size) {
                                val currPt = pts[i]
                                val dx = currPt.offset.x - lastDrawnPt.offset.x
                                val dy = currPt.offset.y - lastDrawnPt.offset.y
                                val distSq = dx * dx + dy * dy
                                val pressureDiff = abs(currPt.pressure - lastDrawnPressure)

                                // Only commit a vector line if the point has moved enough 
                                // or pressure has changed significantly
                                if (distSq >= distThresholdSq || pressureDiff >= pressureThreshold || i == pts.size - 1) {
                                    paint.strokeWidth = (0.2f + (currPt.pressure * 0.8f)) * element.thickness
                                    canvas.drawLine(
                                        lastDrawnPt.offset.x, lastDrawnPt.offset.y,
                                        currPt.offset.x, currPt.offset.y,
                                        paint
                                    )
                                    lastDrawnPt = currPt
                                    lastDrawnPressure = currPt.pressure
                                }
                            }
                        }
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
    private val noteDao: NoteDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(context, noteDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
