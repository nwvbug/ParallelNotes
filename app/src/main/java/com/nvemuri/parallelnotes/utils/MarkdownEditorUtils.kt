package com.nvemuri.parallelnotes.utils

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

fun handleMarkdownTextEdit(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
    // Check if exactly one character was added and it's a newline
    if (newValue.text.length - oldValue.text.length == 1) {
        val insertionPos = newValue.selection.start - 1
        if (insertionPos >= 0 && insertionPos < newValue.text.length && newValue.text[insertionPos] == '\n') {
            
            val textBeforeNewline = newValue.text.substring(0, insertionPos)
            val lastLineStart = textBeforeNewline.lastIndexOf('\n').let { if (it == -1) 0 else it + 1 }
            val lastLine = textBeforeNewline.substring(lastLineStart)
            
            // Regex to match a bullet or numbered list item, capturing the prefix
            val emptyBulletRegex = Regex("^(\\s*([-*+]|\\d+\\.)\\s+)$")
            if (emptyBulletRegex.matches(lastLine)) {
                // Pressed enter on an empty bullet -> remove the bullet from the previous line
                val newText = newValue.text.substring(0, lastLineStart) + newValue.text.substring(insertionPos)
                val newCursor = insertionPos + 1 - lastLine.length
                return TextFieldValue(text = newText, selection = TextRange(maxOf(0, newCursor)))
            }
            
            val bulletRegex = Regex("^(\\s*([-*+]|\\d+\\.)\\s+).*")
            val match = bulletRegex.matchEntire(lastLine)
            if (match != null) {
                val bulletPrefix = match.groupValues[1]
                // Auto-insert the same prefix after the newline
                val newText = newValue.text.substring(0, insertionPos + 1) + bulletPrefix + newValue.text.substring(insertionPos + 1)
                val newCursor = insertionPos + 1 + bulletPrefix.length
                return TextFieldValue(text = newText, selection = TextRange(newCursor))
            }
        }
    }
    return newValue
}

fun handleTabIndent(currentValue: TextFieldValue, isShiftPressed: Boolean): TextFieldValue? {
    val cursor = currentValue.selection.start
    val text = currentValue.text
    val lineStart = text.lastIndexOf('\n', cursor - 1).let { if (it == -1) 0 else it + 1 }
    
    if (isShiftPressed) {
        // Outdent: remove up to 2 spaces at the beginning of the line
        var spacesToRemove = 0
        if (lineStart < text.length && text[lineStart] == ' ') spacesToRemove++
        if (lineStart + 1 < text.length && text[lineStart + 1] == ' ') spacesToRemove++
        
        if (spacesToRemove > 0) {
            val newText = text.substring(0, lineStart) + text.substring(lineStart + spacesToRemove)
            val newCursor = maxOf(lineStart, cursor - spacesToRemove)
            return TextFieldValue(newText, TextRange(newCursor))
        }
    } else {
        // Indent: insert 2 spaces
        val newText = text.substring(0, lineStart) + "  " + text.substring(lineStart)
        val newCursor = cursor + 2
        return TextFieldValue(newText, TextRange(newCursor))
    }
    return null
}
