package com.adam.xai_client.ui.images

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adam.xai_client.domain.model.GeneratedImage
import com.adam.xai_client.ui.components.DropdownSelector
import com.adam.xai_client.ui.components.TransientSnackbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenerationScreen(
    state: ImageGenerationUiState,
    onPromptChange: (String) -> Unit,
    onSourceImageUrlChange: (String) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onResolutionChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onSave: () -> Unit,
    onStoragePermissionDenied: () -> Unit,
    onBack: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingSave by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingSave) {
            onSave()
        } else if (!granted) {
            onStoragePermissionDenied()
        }
        pendingSave = false
    }

    TransientSnackbar(
        message = state.error ?: state.message,
        snackbarHostState = snackbarHostState,
        onShown = onMessageShown
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Генерация изображений") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                label = { Text("Описание") },
                minLines = 3,
                maxLines = 7,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.sourceImageUrl,
                onValueChange = onSourceImageUrlChange,
                label = { Text("URL исходного изображения") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DropdownSelector(
                    label = "Aspect ratio",
                    options = aspectRatios,
                    selectedOption = state.aspectRatio,
                    optionLabel = { it },
                    onOptionSelected = onAspectRatioChange,
                    modifier = Modifier.weight(1f)
                )
                DropdownSelector(
                    label = "Resolution",
                    options = resolutions,
                    selectedOption = state.resolution,
                    optionLabel = { it },
                    onOptionSelected = onResolutionChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onGenerate,
                    enabled = !state.isGenerating && state.prompt.isNotBlank()
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text("Сгенерировать")
                }
                TextButton(
                    onClick = {
                        if (requiresWritePermission(context)) {
                            pendingSave = true
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            onSave()
                        }
                    },
                    enabled = state.generatedImage != null && !state.isSaving
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text("Сохранить")
                }
            }

            ImagePreview(
                image = state.generatedImage,
                isGenerating = state.isGenerating
            )
        }
    }
}

@Composable
private fun ImagePreview(
    image: GeneratedImage?,
    isGenerating: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        when {
            isGenerating -> CircularProgressIndicator()
            image == null -> Text(
                text = "Здесь появится изображение",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                val bitmap = remember(image) {
                    BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

private fun requiresWritePermission(context: android.content.Context): Boolean {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED
}

private val aspectRatios = listOf(
    "auto",
    "1:1",
    "16:9",
    "9:16",
    "4:3",
    "3:4",
    "3:2",
    "2:3",
    "2:1",
    "1:2"
)

private val resolutions = listOf("1k", "2k")
