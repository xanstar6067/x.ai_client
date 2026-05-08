package com.adam.xai_client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = block.text.toInlineMarkdown(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = color
                    )
                }

                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = block.text.toInlineMarkdown(),
                        style = style,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                is MarkdownBlock.ListItems -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (block.ordered) "${index + 1}." else "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = color
                                )
                                Text(
                                    text = item.toInlineMarkdown(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = color
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Code -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        contentColor = color,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                is MarkdownBlock.Quote -> {
                    Surface(
                        color = color.copy(alpha = 0.08f),
                        contentColor = color,
                        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = block.text.toInlineMarkdown(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                is MarkdownBlock.Table -> MarkdownTable(block, color)
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    table: MarkdownBlock.Table,
    color: Color
) {
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
            TableRow(table.header, color, header = true)
            HorizontalDivider(color = color.copy(alpha = 0.18f))
            table.rows.forEach { row ->
                TableRow(row, color, header = false)
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    color: Color,
    header: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Text(
                text = cell.toInlineMarkdown(),
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = if (header) FontWeight.SemiBold else null,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class ListItems(val ordered: Boolean, val items: List<String>) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index++
            continue
        }

        if (line.trimStart().startsWith("```")) {
            val codeLines = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                codeLines += lines[index]
                index++
            }
            if (index < lines.size) index++
            blocks += MarkdownBlock.Code(codeLines.joinToString("\n"))
            continue
        }

        val headingMatch = headingRegex.matchEntire(line.trim())
        if (headingMatch != null) {
            blocks += MarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length.coerceAtMost(3),
                text = headingMatch.groupValues[2].trim()
            )
            index++
            continue
        }

        if (isTableStart(lines, index)) {
            val header = splitTableRow(lines[index])
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && looksLikeTableRow(lines[index])) {
                rows += splitTableRow(lines[index])
                index++
            }
            blocks += MarkdownBlock.Table(header = header, rows = rows)
            continue
        }

        val listMatch = listRegex.matchEntire(line.trim())
        if (listMatch != null) {
            val ordered = listMatch.groupValues[1].isNotEmpty()
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val match = listRegex.matchEntire(lines[index].trim()) ?: break
                val itemIsOrdered = match.groupValues[1].isNotEmpty()
                if (itemIsOrdered != ordered) break
                items += match.groupValues[2].trim()
                index++
            }
            blocks += MarkdownBlock.ListItems(ordered = ordered, items = items)
            continue
        }

        if (line.trimStart().startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                quoteLines += lines[index].trimStart().removePrefix(">").trimStart()
                index++
            }
            blocks += MarkdownBlock.Quote(quoteLines.joinToString("\n"))
            continue
        }

        val paragraphLines = mutableListOf<String>()
        while (index < lines.size && lines[index].isNotBlank()) {
            if (
                lines[index].trimStart().startsWith("```") ||
                headingRegex.matches(lines[index].trim()) ||
                listRegex.matches(lines[index].trim()) ||
                isTableStart(lines, index) ||
                lines[index].trimStart().startsWith(">")
            ) {
                break
            }
            paragraphLines += lines[index].trim()
            index++
        }
        blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString("\n"))
    }

    return blocks
}

private fun String.toInlineMarkdown(): AnnotatedString {
    val source = this
    return AnnotatedString.Builder().apply {
        appendInlineMarkdown(source, 0, source.length)
    }.toAnnotatedString()
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    source: String,
    start: Int,
    end: Int
) {
    var index = start
    while (index < end) {
        val match = inlineMarkers
            .mapNotNull { marker ->
                val found = source.indexOf(marker.token, index)
                if (found >= 0 && found < end) marker to found else null
            }
            .minByOrNull { it.second }

        if (match == null) {
            append(source.substring(index, end))
            return
        }

        val (marker, markerStart) = match
        append(source.substring(index, markerStart))
        val contentStart = markerStart + marker.token.length
        val contentEnd = source.indexOf(marker.token, contentStart)
        if (contentEnd < 0 || contentEnd >= end) {
            append(source.substring(markerStart, end))
            return
        }

        pushStyle(marker.style)
        appendInlineMarkdown(source, contentStart, contentEnd)
        pop()
        index = contentEnd + marker.token.length
    }
}

private data class InlineMarker(
    val token: String,
    val style: SpanStyle
)

private val inlineMarkers = listOf(
    InlineMarker("***", SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)),
    InlineMarker("**", SpanStyle(fontWeight = FontWeight.Bold)),
    InlineMarker("__", SpanStyle(fontWeight = FontWeight.Bold)),
    InlineMarker("*", SpanStyle(fontStyle = FontStyle.Italic)),
    InlineMarker("_", SpanStyle(fontStyle = FontStyle.Italic)),
    InlineMarker("`", SpanStyle(fontFamily = FontFamily.Monospace))
)

private val headingRegex = Regex("^(#{1,6})\\s+(.+)$")
private val listRegex = Regex("^(?:(\\d+)\\.|[-*+])\\s+(.+)$")

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    return looksLikeTableRow(lines[index]) && tableSeparatorRegex.matches(lines[index + 1].trim())
}

private fun looksLikeTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.contains("|") && trimmed.count { it == '|' } >= 2
}

private fun splitTableRow(line: String): List<String> {
    return line.trim()
        .trim('|')
        .split("|")
        .map { it.trim() }
}

private val tableSeparatorRegex = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$")
