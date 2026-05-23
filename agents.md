# ParallelNotes - Development Context

This document serves as persistent context for working on this codebase across sessions.

## Project Overview

**ParallelNotes** is an Android note-taking app with canvas-based handwriting support and a "Important Pen" feature that allows users to mark specific strokes for quick access on the home screen.

**Key Technologies:**
- Jetpack Compose for UI
- Room Database for persistence
- Kotlin Coroutines for async operations
- Android Canvas API for drawing

## Architecture

### Main Screens
- **MainActivity** - Entry point, hosts navigation
- **HomeScreen** - Displays notes, folders, and Important Stroke summaries
- **NoteTakingScreen** - Contains the DrawingCanvas for handwriting

### Key Components

#### DrawingCanvas (NoteTakingScreen.kt)
- **Viewport system**: Pan/zoom with `viewportPan` (Offset) and `viewportScale` (Float)
- **Bitmap chunking**: Canvas divided into 512x512 pixel chunks for efficient rendering
- **Stroke pipeline**: Stylus input → jitter removal → bezier smoothing → Picture recording → chunk storage

#### NoteViewModel
- **currentNoteId**: Current note being edited (empty string if no note loaded)
- **currentFolder**: Currently selected folder
- **currentElements**: Canvas strokes/shapes (StateFlow)
- **importantStrokes**: Important Pen strokes grouped by category (Flow from DAO)

#### Important Pen Feature
- Strokes within 300px proximity threshold are merged into groups
- Groups are stored in `ImportantStrokeEntity` with bounding boxes
- Strokes can be filtered by folder AND noteId for proper deletion

### Database Schema

**Table: notes**
| Column | Type | Description |
|--------|------|-------------|
| noteId | TEXT (PK) | UUID |
| title | TEXT | Note title |
| folder | TEXT | Folder name |
| lastModified | INTEGER | Timestamp |
| colorArgb | INTEGER | Border color |

**Table: important_strokes**
| Column | Type | Description |
|--------|------|-------------|
| id | TEXT (PK) | UUID |
| folderName | TEXT | Folder for filtering |
| noteId | TEXT | Links to specific note |
| noteTitle | TEXT | Snapshot of note title |
| serializedElements | TEXT | JSON list of strokes |
| minX, maxX, minY, maxY | REAL | Bounding box |
| colorArgb | INTEGER | Category color |
| categoryName | TEXT | Category (default: "Important") |
| timestamp | INTEGER | For sorting |

**Table: important_categories**
| Column | Type | Description |
|--------|------|-------------|
| name | TEXT (PK) | Category name |
| colorArgb | INTEGER | Category color |

## Important Patterns

### ViewModel ↔ UI Communication
- Use `StateFlow` for observable state
- Use `LaunchedEffect` with flow collection for side effects
- Avoid `GlobalScope` - use `rememberCoroutineScope()` in Composables

### Drawing Pipeline
1. **Pen down**: Initialize stroke with pressure
2. **Pen move**: Add points with jitter removal
3. **Pen up**: Convert to Picture, store in chunks, update canvasElements

### Important Stroke Processing
```kotlin
// When stroke completes with Important Pen:
// 1. Check for nearby strokes in same note/category
// 2. If found: merge bounds and elements
// 3. If not: create new ImportantStrokeEntity
// 4. Update database
```

### Viewport Navigation
When navigating from Important Stroke card to note:
1. Store target bounds in ViewModel (`navigateToImportantStroke`)
2. Load note (`loadNote`)
3. On canvas render with size: calculate pan/scale to center on stroke

## Known Quirks & Limitations

### Eraser Behavior
- Stroke eraser (erases entire stroke on touch)
- When erasing Important Pen strokes: must filter by BOTH folder AND noteId
- Bug history: Previously only filtered by folder, causing orphaned strokes

### Bounding Box Confirmation
- Shows 300px proximity threshold area (outer dashed box)
- Inner solid box shows actual stroke bounds with 8px padding
- Debounce: 300ms before showing, auto-hides after 1000ms
- Cancels immediately when user starts new stroke

### Stroke Merging
- Proximity threshold: 300px between bounding boxes
- Merged strokes share same category and noteId
- Deletion requires matching both criteria

## Important Files

| File | Purpose |
|------|---------|
| `NoteTakingScreen.kt` | Main canvas, drawing logic, viewport |
| `NoteViewModel.kt` | Business logic, database operations |
| `HomeScreen.kt` | Note/stroke listing, navigation |
| `ImportantStrokeDao.kt` | Database queries for important strokes |
| `CanvasElement.kt` | Stroke data structures |
| `DrawingUtils.kt` | Stroke smoothing, drawing helpers |
| `CanvasChunk.kt` | Bitmap chunking for rendering |

## Build & Test

```bash
# Build debug APK
./gradlew assembleDebug

# Java required - ensure JAVA_HOME is set
```

## Future Improvements to Consider

1. **Precise eraser option**: Current eraser is stroke-based, could add point-based
2. **Export improvements**: PDF export works but could add PNG/SVG
3. **Undo/redo**: Not currently implemented
4. **Multi-user sync**: Room database is local only
5. **Stroke search**: Could implement stroke recognition for search

## Session Checkpoints

### 2026-05-22 — Text box bounds fix + markdown/resize WIP

**Shipped:**
- Fixed text-box bounds desync: the overlay box used `Modifier.width()`, which Compose
  clamps to the parent's screen-width constraint. Once `displayWidth` (a world-space value)
  exceeded the screen width, the rendered box capped at the screen while the canvas-drawn
  selection outline/handles (via `toScreen()`) did not — so the dashed box looked wider than
  the actual text, and the clamped-narrower box over-wrapped, inflating `displayHeight` and
  pushing the L/R handles off the top of the text. Switched to `requiredWidth()`, which
  ignores the incoming max constraint. (`NoteTakingScreen.kt`)

**Also committed (in-progress, same area):**
- `CanvasAction` sealed class (Add/Remove/Move/Resize) backing the undo/redo stacks.
- Markdown editing helpers: `handleMarkdownTextEdit` (bullet/number list continuation on
  Enter, empty-bullet removal) and `handleTabIndent` (2-space indent/outdent on Tab/Shift+Tab).
- `MarkdownVisualTransformation` for inline WYSIWYG-ish styling in the edit field.
- Live resize preview: selection handles and text reflow now track `resizePreviewRect`
  during an active drag instead of a separate preview outline. `image_insert.xml` drawable.

**Note:** The "Undo/redo: Not currently implemented" line above is stale — it exists now
(see `CanvasAction` + undo/redo stacks in `NoteTakingScreen.kt`).
