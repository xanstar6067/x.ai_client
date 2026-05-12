package com.adam.xai_client.ui.components

import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin

@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { splitMarkdownBlocks(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownRenderBlock.Text -> {
                    if (block.markdown.isNotBlank()) {
                        MarkwonText(
                            markdown = block.markdown,
                            color = color
                        )
                    }
                }

                is MarkdownRenderBlock.Table -> MarkdownTable(
                    table = block,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun MarkwonText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typography = MaterialTheme.typography.bodyLarge
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .build()
    }
    val textSize = typography.fontSize.takeIf { it.isSp } ?: 16.sp
    val lineHeight = typography.lineHeight.takeIf { it.isSp }
    val typeface = remember(
        typography.fontFamily,
        typography.fontWeight,
        typography.fontStyle
    ) {
        val style = when {
            typography.fontWeight == FontWeight.Bold && typography.fontStyle == FontStyle.Italic -> Typeface.BOLD_ITALIC
            typography.fontWeight == FontWeight.Bold -> Typeface.BOLD
            typography.fontStyle == FontStyle.Italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        Typeface.create(Typeface.DEFAULT, style)
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                includeFontPadding = true
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(false)
            }
        },
        update = { textView ->
            textView.setTextColor(color.toArgb())
            textView.textSize = textSize.value
            textView.typeface = typeface
            if (lineHeight != null) {
                textView.setLineSpacing(0f, lineHeight.value / textSize.value)
            } else {
                textView.setLineSpacing(0f, 1.0f)
            }
            markwon.setMarkdown(textView, markdown)
        }
    )
}

@Composable
private fun MarkdownTable(
    table: MarkdownRenderBlock.Table,
    color: Color
) {
    val columnCount = table.columnCount
    val header = table.header.normalizedTo(columnCount)
    val rows = table.rows.map { it.normalizedTo(columnCount) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            MarkdownTableRow(
                cells = header,
                color = color,
                header = true
            )
            HorizontalDivider(color = color.copy(alpha = 0.18f))
            rows.forEachIndexed { index, row ->
                MarkdownTableRow(
                    cells = row,
                    color = color,
                    header = false,
                    shaded = index % 2 == 1
                )
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    color: Color,
    header: Boolean,
    shaded: Boolean = false
) {
    Row(
        modifier = Modifier.background(
            if (shaded) color.copy(alpha = 0.04f) else Color.Transparent
        )
    ) {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .width(164.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                MarkwonText(
                    markdown = if (header) "**${cell.trim()}**" else cell.trim(),
                    color = color
                )
            }
        }
    }
}

private sealed interface MarkdownRenderBlock {
    data class Text(val markdown: String) : MarkdownRenderBlock
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>
    ) : MarkdownRenderBlock {
        val columnCount: Int = maxOf(
            header.size,
            rows.maxOfOrNull { it.size } ?: 0
        )
    }
}

private fun splitMarkdownBlocks(markdown: String): List<MarkdownRenderBlock> {
    val lines = markdown.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MarkdownRenderBlock>()
    val textLines = mutableListOf<String>()
    var index = 0

    fun flushText() {
        if (textLines.any { it.isNotBlank() }) {
            blocks += MarkdownRenderBlock.Text(textLines.joinToString("\n").trim())
        }
        textLines.clear()
    }

    while (index < lines.size) {
        if (isTableStart(lines, index)) {
            flushText()
            val header = splitTableRow(lines[index])
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && looksLikeTableRow(lines[index])) {
                rows += splitTableRow(lines[index])
                index++
            }
            blocks += MarkdownRenderBlock.Table(header = header, rows = rows)
        } else {
            textLines += lines[index]
            index++
        }
    }

    flushText()
    return blocks
}

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    return looksLikeTableRow(lines[index]) && tableSeparatorRegex.matches(lines[index + 1].trim())
}

private fun looksLikeTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.contains("|") && trimmed.count { it == '|' } >= 2
}

private fun splitTableRow(line: String): List<String> {
    return splitUnescapedPipes(line.trim().trim('|'))
        .map { it.replace("\\|", "|").trim() }
}

private fun splitUnescapedPipes(line: String): List<String> {
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var escaped = false
    line.forEach { char ->
        when {
            escaped -> {
                cell.append(char)
                escaped = false
            }

            char == '\\' -> {
                cell.append(char)
                escaped = true
            }

            char == '|' -> {
                cells += cell.toString()
                cell.clear()
            }

            else -> cell.append(char)
        }
    }
    cells += cell.toString()
    return cells
}

private fun List<String>.normalizedTo(size: Int): List<String> {
    if (this.size == size) return this
    if (this.size > size) return take(size)
    return this + List(size - this.size) { "" }
}

private val tableSeparatorRegex = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$")
