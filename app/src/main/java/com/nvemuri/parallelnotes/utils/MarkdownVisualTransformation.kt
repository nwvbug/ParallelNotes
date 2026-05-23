package com.nvemuri.parallelnotes.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp

private val BASE_FONT_SIZE = 32.sp

// Heading multipliers matching Markwon's defaults (the library used for the rendered view),
// so headers in edit mode size the same as when the box is rendered.
private val HEADING_MULTIPLIERS = floatArrayOf(2.0f, 1.5f, 1.17f, 1.0f, 0.83f, 0.67f)

// Markers (#, **, *, `, ~~) stay visible but are de-emphasized so they read as controls.
private val MARKER_COLOR = Color(0xFFB0B0B0)

// Bullet glyphs per nesting depth, mirroring the rendered list style.
private val BULLET_GLYPHS = charArrayOf('•', '◦', '▪') // • ◦ ▪

private const val INDENT_BASE_SP = 12f
private const val INDENT_PER_LEVEL_SP = 24f

class MarkdownVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text

        // Build the displayed string by swapping leading list markers for bullet glyphs.
        // Each swap is a single char for a single char, so length is preserved and the
        // identity offset mapping stays valid (no cursor remapping needed).
        val displayChars = raw.toCharArray()
        val bulletLineRegex = Regex("^(\\s*)([-*+])(\\s)", RegexOption.MULTILINE)
        bulletLineRegex.findAll(raw).forEach { match ->
            val leadingSpaces = match.groupValues[1].length
            val markerIndex = match.range.first + leadingSpaces
            val depth = leadingSpaces / 2
            displayChars[markerIndex] = BULLET_GLYPHS[depth.coerceIn(0, BULLET_GLYPHS.size - 1)]
        }

        val builder = AnnotatedString.Builder(String(displayChars))

        // Bold: **text**
        Regex("\\*\\*(.*?)\\*\\*").findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
            // Dim the surrounding ** markers.
            builder.addStyle(SpanStyle(color = MARKER_COLOR), match.range.first, match.range.first + 2)
            builder.addStyle(SpanStyle(color = MARKER_COLOR), match.range.last - 1, match.range.last + 1)
        }

        // Italic: *text* (avoid matching **)
        Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)").findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
            builder.addStyle(SpanStyle(color = MARKER_COLOR), match.range.first, match.range.first + 1)
            builder.addStyle(SpanStyle(color = MARKER_COLOR), match.range.last, match.range.last + 1)
        }

        // Strikethrough: ~~text~~
        Regex("~~(.*?)~~").findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), match.range.first, match.range.last + 1)
            builder.addStyle(SpanStyle(color = MARKER_COLOR), match.range.first, match.range.first + 2)
            builder.addStyle(SpanStyle(color = MARKER_COLOR), match.range.last - 1, match.range.last + 1)
        }

        // Headers: # Header
        Regex("^(#{1,6})\\s+(.*)", RegexOption.MULTILINE).findAll(raw).forEach { match ->
            val level = match.groupValues[1].length
            val size = BASE_FONT_SIZE * HEADING_MULTIPLIERS[level - 1]
            builder.addStyle(SpanStyle(fontSize = size, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
            // Dim and shrink the "# " prefix so it recedes behind the header text.
            val contentStart = match.groups[2]?.range?.first ?: (match.range.first + level)
            builder.addStyle(
                SpanStyle(color = MARKER_COLOR, fontSize = BASE_FONT_SIZE, fontWeight = FontWeight.Normal),
                match.range.first,
                contentStart
            )
        }

        // Inline code: `code`
        Regex("`(.*?)`").findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(background = Color.LightGray.copy(alpha = 0.5f), color = Color.DarkGray),
                match.range.first,
                match.range.last + 1
            )
            builder.addStyle(SpanStyle(color = MARKER_COLOR), match.range.first, match.range.first + 1)
            builder.addStyle(SpanStyle(color = MARKER_COLOR), match.range.last, match.range.last + 1)
        }

        // Nested-bullet indentation: a per-line ParagraphStyle that steps in with depth,
        // amplifying the otherwise tiny 2-space indent so it reads like the rendered list.
        // Ranges are aligned to line boundaries (and disjoint) to avoid overlapping
        // ParagraphStyle errors.
        bulletLineRegex.findAll(raw).forEach { match ->
            val depth = match.groupValues[1].length / 2
            val lineStart = match.range.first
            val newlineIdx = raw.indexOf('\n', startIndex = lineStart)
            val lineEnd = if (newlineIdx == -1) raw.length else newlineIdx + 1
            val indent = (INDENT_BASE_SP + depth * INDENT_PER_LEVEL_SP).sp
            builder.addStyle(
                ParagraphStyle(
                    textIndent = TextIndent(firstLine = indent, restLine = indent),
                    // Drop per-paragraph font padding; otherwise each bullet line adds
                    // top+bottom padding and the list spreads out vertically vs. the rendered view.
                    platformStyle = PlatformParagraphStyle(includeFontPadding = false)
                ),
                lineStart,
                lineEnd
            )
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
