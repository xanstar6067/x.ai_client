package com.adam.xai_client.ui.components

import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin

@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typography = MaterialTheme.typography.bodyLarge
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
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
                setTextIsSelectable(true)
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
