package com.adam.xai_client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.adam.xai_client.navigation.XaiChatNavHost
import com.adam.xai_client.ui.theme.Xai_clientTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as XaiChatApplication).container
        lifecycleScope.launch {
            appContainer.roleRepository.ensureBuiltInRole()
            appContainer.modelRepository.ensureKnownModels()
        }
        enableEdgeToEdge()
        setContent {
            Xai_clientTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    XaiChatNavHost(container = appContainer)
                }
            }
        }
    }
}
