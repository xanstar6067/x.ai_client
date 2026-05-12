package com.adam.xai_client.ui.pricing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adam.xai_client.domain.model.AiModel
import com.adam.xai_client.ui.components.SafeSnackbarHost
import com.adam.xai_client.ui.components.TransientSnackbar
import com.adam.xai_client.ui.haptics.UiHapticSignal
import com.adam.xai_client.ui.haptics.rememberHapticClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingScreen(
    state: PricingUiState,
    onAutofill: () -> Unit,
    onPriceChange: (String, PriceField, String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val hapticBack = rememberHapticClick(onBack)
    val hapticAutofill = rememberHapticClick(onAutofill)
    val hapticSave = rememberHapticClick(UiHapticSignal.Confirm, onSave)
    TransientSnackbar(
        message = state.error ?: state.message,
        snackbarHostState = snackbarHostState,
        onShown = onMessageShown
    )

    Scaffold(
        snackbarHost = { SafeSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Расценки") },
                navigationIcon = {
                    IconButton(onClick = hapticBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = hapticAutofill,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                    Text("Подставить известные цены", modifier = Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = hapticSave,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text("Сохранить", modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    text = "Цены указываются в USD за 1 млн токенов. Пустое поле означает, что эта часть стоимости не считается.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.models.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Список моделей пуст", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.models,
                        key = { it.id }
                    ) { model ->
                        PricingModelCard(
                            model = model,
                            draft = state.drafts[model.id] ?: ModelPriceDraft(),
                            onPriceChange = { field, value -> onPriceChange(model.id, field, value) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PricingModelCard(
    model: AiModel,
    draft: ModelPriceDraft,
    onPriceChange: (PriceField, String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Filled.AttachMoney,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name.ifBlank { model.id },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = model.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            PriceTextField(
                label = "Ввод",
                value = draft.input,
                onValueChange = { onPriceChange(PriceField.INPUT, it) }
            )
            PriceTextField(
                label = "Кэшированный ввод",
                value = draft.cachedInput,
                onValueChange = { onPriceChange(PriceField.CACHED_INPUT, it) }
            )
            PriceTextField(
                label = "Вывод",
                value = draft.output,
                onValueChange = { onPriceChange(PriceField.OUTPUT, it) }
            )
        }
    }
}

@Composable
private fun PriceTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = { Text("$") },
        suffix = { Text("/ 1M") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}
