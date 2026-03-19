package com.nvemuri.parallelnotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.nvemuri.parallelnotes.data.NoteDao
import com.nvemuri.parallelnotes.data.NoteEntity
import com.nvemuri.parallelnotes.data.NoteSummary
import com.nvemuri.parallelnotes.data.entities.CanvasElement
import com.nvemuri.parallelnotes.data.entities.toSerializable
import com.nvemuri.parallelnotes.data.toCanvasElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import android.content.Context
import com.nvemuri.parallelnotes.data.entities.SerializableElement
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NoteViewModel(private val context: Context, private val noteDao: NoteDao) : ViewModel() {
    // Add this near your other MutableStateFlows
    private val _currentNoteTitle = MutableStateFlow("Untitled Note")
    val currentNoteTitle: StateFlow<String> = _currentNoteTitle.asStateFlow()

    // Add a quick function to let the UI change the title
    fun updateTitle(newTitle: String) {
        _currentNoteTitle.value = newTitle
    }
    // Change the type from List<NoteEntity> to List<NoteSummary>
    val allNotes: StateFlow<List<NoteEntity>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // Holds the currently loaded note's elements
    private val _currentElements = MutableStateFlow<List<CanvasElement>>(emptyList())
    fun updateCurrentElements(elements: List<CanvasElement>) {
        _currentElements.value = elements
    }
    val currentElements: StateFlow<List<CanvasElement>> = _currentElements.asStateFlow()

    // Tracks which note is currently open
    var currentNoteId: String = ""
        private set

    fun loadNote(noteId: String) {
        currentNoteId = noteId
        _currentElements.value = emptyList()

        viewModelScope.launch {
            // 1. Fetch the title from Room
            val note = withContext(Dispatchers.IO) { noteDao.getNoteById(noteId) }
            if (note != null) {
                _currentNoteTitle.value = note.title

                // 2. Read the ink from the local JSON file
                val file = File(context.filesDir, "${noteId}.json")
                if (file.exists()) {
                    val rebuiltElements: List<CanvasElement> = withContext(Dispatchers.Default) {
                        val jsonString = file.readText()
                        val serializableData = Json.decodeFromString<List<SerializableElement>>(jsonString)

                        // This last line is what gets returned to 'rebuiltElements'
                        serializableData.map { it.toCanvasElement() }
                    }
                    _currentElements.value = rebuiltElements
                }
            } else {
                _currentNoteTitle.value = "Untitled Note"
            }
        }
    }

    fun saveNote(title: String, elements: List<CanvasElement>) {
        if (currentNoteId.isEmpty()) {
            currentNoteId = java.util.UUID.randomUUID().toString()
        }
        val idToSave = currentNoteId

        viewModelScope.launch {
            // 1. Write the massive ink math to a file on the hard drive
            withContext(Dispatchers.Default) {
                val serializableData = elements.map { it.toSerializable() }
                val jsonString = Json.encodeToString(serializableData)
                val file = File(context.filesDir, "${idToSave}.json")
                file.writeText(jsonString)
            }

            // 2. Save the lightweight ID and Title to Room
            val newNote = NoteEntity(
                noteId = idToSave,
                title = title,
                lastModified = System.currentTimeMillis()
            )
            withContext(Dispatchers.IO) {
                noteDao.insertOrUpdateNote(newNote)
            }
        }
    }

    // Prepare for a New Note
    fun createNewNote() {
        currentNoteId = UUID.randomUUID().toString()
        _currentElements.value = emptyList()
        _currentNoteTitle.value = "Untitled Note"
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

