package com.adam.xai_client.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun TransientSnackbar(
    message: String?,
    snackbarHostState: SnackbarHostState,
    onShown: () -> Unit
) {
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        onShown()
    }
}
