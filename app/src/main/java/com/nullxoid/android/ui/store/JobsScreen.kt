package com.nullxoid.android.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nullxoid.android.data.model.StoreJobSummary
import com.nullxoid.android.ui.AppUiState
import com.nullxoid.android.ui.MainBottomNavigation
import com.nullxoid.android.ui.MainTab
import com.nullxoid.android.ui.mainTabSwipeNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    state: AppUiState,
    onRefresh: (Boolean) -> Unit,
    onCancelJob: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenAsk: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showHistory by remember { mutableStateOf(!state.storeJobsActiveOnly) }
    var pendingCancel by remember { mutableStateOf<StoreJobSummary?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jobs") },
                actions = {
                    IconButton(
                        modifier = Modifier.testTag("jobs-refresh"),
                        onClick = { onRefresh(showHistory.not()) }
                    ) { Icon(Icons.Default.Refresh, "Refresh jobs") }
                }
            )
        },
        bottomBar = {
            MainBottomNavigation(
                selected = MainTab.Create,
                onOpenCreate = onOpenCreate,
                onOpenGallery = onOpenGallery,
                onOpenAsk = onOpenAsk,
                onOpenSettings = onOpenSettings
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .navigationBarsPadding()
                .imePadding()
                .fillMaxSize()
                .mainTabSwipeNavigation(
                    selected = MainTab.Create,
                    onOpenCreate = onOpenCreate,
                    onOpenGallery = onOpenGallery,
                    onOpenAsk = onOpenAsk,
                    onOpenSettings = onOpenSettings
                )
                .testTag("jobs-screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            showHistory = false
                            onRefresh(true)
                        },
                        label = { Text("Active") }
                    )
                    AssistChip(
                        onClick = {
                            showHistory = true
                            onRefresh(false)
                        },
                        label = { Text("Recent") }
                    )
                }
            }
            if (state.storeJobs.isEmpty()) {
                item {
                    Text(
                        if (state.storeJobsLoading) "Loading jobs..." else "No jobs to show.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.storeJobs, key = { it.storeJobId ?: it.jobId ?: it.requestId ?: it.hashCode().toString() }) { job ->
                    StoreJobMonitorCard(job = job, onCancel = { pendingCancel = job })
                }
            }
        }
    }

    pendingCancel?.let { job ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text("Cancel this job?") },
            text = { Text("Cancelled jobs stop future claim, upload, and gallery result handling.") },
            dismissButton = {
                TextButton(onClick = { pendingCancel = null }) { Text("No, keep job") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = job.storeJobId ?: job.jobId.orEmpty()
                        pendingCancel = null
                        onCancelJob(id)
                    }
                ) { Text("Yes, cancel job") }
            }
        )
    }
}

@Composable
private fun StoreJobMonitorCard(job: StoreJobSummary, onCancel: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store-job-monitor-card")
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(
                        friendlyMedia(job.mediaKind),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        friendlyStatus(job.status, job.cancelRequested),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (job.queuePosition > 0) {
                    AssistChip(onClick = {}, label = { Text("Queue #${job.queuePosition + 1}") })
                }
            }
            Text(
                "Updated ${job.updatedAt.ifBlank { job.createdAt }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (job.approvalSource.isNotBlank()) {
                Text(
                    "Approval ${job.approvalSource}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            job.events.takeLast(3).forEach { event ->
                Text(
                    "${event.type.replace('_', ' ')} ${event.at}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "Details ${job.storeJobId ?: job.jobId.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (job.canCancel) {
                OutlinedButton(
                    modifier = Modifier.testTag("cancel-store-job"),
                    onClick = onCancel
                ) { Text("Cancel job") }
            } else if (job.status == "cancelled") {
                Text("Cancelled", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun friendlyMedia(mediaKind: String): String = when (mediaKind.lowercase()) {
    "video" -> "Video"
    "model3d", "3d" -> "3D"
    else -> "Image"
}

private fun friendlyStatus(status: String, cancelRequested: Boolean): String {
    if (cancelRequested && status !in setOf("cancelled", "failed", "completed")) return "Cancel requested"
    return when (status) {
        "pending_approval" -> "Waiting for approval"
        "approved", "queued_connector" -> "Queued"
        "running_provider" -> "Running"
        "uploading_artifact" -> "Uploading"
        "completed", "succeeded" -> "Completed"
        "cancelled" -> "Cancelled"
        "failed" -> "Failed"
        else -> status.replace('_', ' ').replaceFirstChar { it.titlecase() }
    }
}
