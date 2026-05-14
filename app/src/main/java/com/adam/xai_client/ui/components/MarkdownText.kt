package com.adam.xai_client.ui.components

import android.graphics.Typeface
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.adam.xai_client.ui.haptics.rememberHapticClick
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin

@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
    onCopyCode: ((String) -> Unit)? = null
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

                is MarkdownRenderBlock.Code -> MarkdownCodeBlock(
                    code = block.code,
                    language = block.language,
                    color = color,
                    onCopy = onCopyCode
                )

                is MarkdownRenderBlock.Table -> MarkdownTable(
                    table = block,
                    color = color
                )
            }
        }
    }
}

@Composable
fun MarkdownInlineText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE
) {
    MarkwonText(
        markdown = markdown,
        color = color,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        selectable = false
    )
}

@Composable
private fun MarkwonText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    selectable: Boolean = true
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .build()
    }
    val textSize = style.fontSize.takeIf { it.isSp } ?: 16.sp
    val lineHeight = style.lineHeight.takeIf { it.isSp }
    val typeface = remember(
        style.fontFamily,
        style.fontWeight,
        style.fontStyle
    ) {
        val typefaceStyle = when {
            style.fontWeight == FontWeight.Bold && style.fontStyle == FontStyle.Italic -> Typeface.BOLD_ITALIC
            style.fontWeight == FontWeight.Bold -> Typeface.BOLD
            style.fontStyle == FontStyle.Italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        Typeface.create(Typeface.DEFAULT, typefaceStyle)
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                includeFontPadding = true
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(color.toArgb())
            textView.textSize = textSize.value
            textView.typeface = typeface
            textView.maxLines = maxLines
            textView.ellipsize = if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END
            textView.setTextIsSelectable(selectable)
            textView.linksClickable = true
            textView.movementMethod = LinkMovementMethod.getInstance()
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
private fun MarkdownCodeBlock(
    code: String,
    language: String?,
    color: Color,
    onCopy: ((String) -> Unit)?
) {
    val copyCode = onCopy?.let { copy ->
        rememberHapticClick { copy(code) }
    }
    val canRunHtml = remember(language) { language.isHtmlLanguage() }
    var isPreviewVisible by rememberSaveable(code, language) { mutableStateOf(false) }
    val togglePreview = rememberHapticClick {
        isPreviewVisible = !isPreviewVisible
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (!language.isNullOrBlank() || copyCode != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = language?.takeIf { it.isNotBlank() } ?: "code",
                        style = MaterialTheme.typography.labelSmall,
                        color = color.copy(alpha = 0.72f),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = true)
                    )
                    if (copyCode != null) {
                        IconButton(onClick = copyCode) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Копировать код",
                                tint = color.copy(alpha = 0.78f)
                            )
                        }
                    }
                    if (canRunHtml) {
                        IconButton(onClick = togglePreview) {
                            Icon(
                                if (isPreviewVisible) Icons.Filled.Close else Icons.Filled.PlayArrow,
                                contentDescription = if (isPreviewVisible) {
                                    "Закрыть HTML-превью"
                                } else {
                                    "Запустить HTML"
                                },
                                tint = color.copy(alpha = 0.78f)
                            )
                        }
                    }
                }
            }
            if (isPreviewVisible && canRunHtml) {
                HtmlPreview(
                    html = code,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 420.dp)
                )
            }
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun HtmlPreview(
    html: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)),
        shape = MaterialTheme.shapes.small
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(
                    "https://local-html-preview.invalid/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        )
    }
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
    data class Code(val language: String?, val code: String) : MarkdownRenderBlock
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
        val fence = codeFenceFor(lines[index])
        if (fence != null) {
            flushText()
            val codeLines = mutableListOf<String>()
            index++
            while (index < lines.size && !isClosingCodeFence(lines[index], fence.marker)) {
                codeLines += lines[index]
                index++
            }
            if (index < lines.size) index++
            blocks += MarkdownRenderBlock.Code(
                language = fence.language,
                code = codeLines.joinToString("\n").trimEnd()
            )
        } else if (isTableStart(lines, index)) {
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

private data class CodeFence(val marker: String, val language: String?)

private fun codeFenceFor(line: String): CodeFence? {
    val trimmed = line.trimStart()
    val marker = when {
        trimmed.startsWith("```") -> "```"
        trimmed.startsWith("~~~") -> "~~~"
        else -> return null
    }
    return CodeFence(
        marker = marker,
        language = trimmed.removePrefix(marker).trim().takeIf { it.isNotBlank() }
    )
}

private fun isClosingCodeFence(line: String, marker: String): Boolean {
    return line.trimStart().startsWith(marker)
}

private fun String?.isHtmlLanguage(): Boolean {
    return this?.trim()?.lowercase() in setOf("html", "htm")
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
