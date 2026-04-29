package com.nullxoid.android.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

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
                HealthRow("Backend URL", state.backendUrl.ifBlank { "not set" })
                HealthRow("Backend", h.backend ?: runtimeValue(h.runtime, "backend_service") ?: "unknown")
                h.version?.let { HealthRow("Version", it) }
                HealthRow("Status", if (h.ok) "OK" else "DEGRADED")
                h.features?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("Features", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    FeatureRows(it)
                }
                h.runtime?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("Runtime", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    FeatureRows(it)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRefresh) { Text("Refresh") }
        }
    }
}

@Composable
private fun FeatureRows(values: JsonObject) {
    val preferred = listOf(
        "status",
        "runtime_status",
        "runtime_provider",
        "backend_service",
        "auth_mode",
        "auth_primary_method",
        "auth_passkey_login_ready",
        "auth_passkey_registration_enabled",
        "models",
        "settings_enabled",
        "artifacts_upload",
        "addons_enabled"
    )
    val ordered = values.entries.sortedWith(
        compareBy<Map.Entry<String, JsonElement>> {
            val index = preferred.indexOf(it.key)
            if (index == -1) preferred.size else index
        }.thenBy { it.key }
    )
    ordered.forEach { (key, value) ->
        HealthRow(key.replace('_', ' '), displayValue(value))
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun runtimeValue(runtime: JsonObject?, key: String): String? =
    runtime?.get(key)?.let(::displayValue)

internal fun displayValue(value: JsonElement): String =
    when (value) {
        is JsonPrimitive -> when {
            value.booleanOrNull != null -> if (value.booleanOrNull == true) "yes" else "no"
            value.isString -> value.contentOrNull.orEmpty()
            else -> value.toString()
        }
        else -> value.toString()
    }
