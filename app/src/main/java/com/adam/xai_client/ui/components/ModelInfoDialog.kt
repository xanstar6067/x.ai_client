package com.adam.xai_client.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adam.xai_client.domain.model.ModelLimits
import com.adam.xai_client.domain.model.XaiModelLimits

@Composable
fun ModelInfoDialog(
    modelId: String?,
    limits: ModelLimits?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(modelId ?: "Модель") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (limits == null) {
                    Text("Для этой модели в приложении пока нет проверенного описания лимитов.")
                    Text(
                        text = "Источник для проверки: ${XaiModelLimits.sourceForRateLimitDetails()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    return@Column
                }
                LimitRow("Контекст", limits.contextWindowTokens?.let { "$it токенов" } ?: "Нет в ответе API")
                LimitRow("Лимит запросов", limits.publicRateLimit)
                LimitRow("Цена ввода", limits.inputPricePerMillion.withTokenUnit())
                limits.cachedInputPricePerMillion?.let {
                    LimitRow("Цена кэшированного ввода", "$it / 1 млн токенов")
                }
                LimitRow("Цена вывода", limits.outputPricePerMillion.withTokenUnit())
                limits.imagePrice?.let {
                    LimitRow("Цена изображения", "$it / изображение")
                }
                HorizontalDivider()
                limits.notes.forEach { note ->
                    Text("- $note", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "Источник: ${limits.sourceLabel} (${limits.sourceUrl})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ОК")
            }
        }
    )
}

@Composable
private fun LimitRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun String.withTokenUnit(): String {
    return if (contains("token", ignoreCase = true) || contains("/")) {
        this
    } else {
        "$this / 1 млн токенов"
    }
}
