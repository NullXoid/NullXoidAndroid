package com.nullxoid.android.ui.health

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nullxoid.android.ui.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Runtime health") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            val h = state.health
            if (h == null) {
                Text("Loading...", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Backend: ${h.backend ?: "unknown"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                h.version?.let { Text("Version: $it") }
                Text("Status: ${if (h.ok) "OK" else "DEGRADED"}")
                h.features?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("Features", style = MaterialTheme.typography.titleSmall)
                    Text(it.toString(), style = MaterialTheme.typography.bodySmall)
                }
                h.runtime?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("Runtime", style = MaterialTheme.typography.titleSmall)
                    Text(it.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRefresh) { Text("Refresh") }
        }
    }
}
