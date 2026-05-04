package com.nullxoid.android.ui.store

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.nullxoid.android.data.model.StoreAddon
import com.nullxoid.android.data.model.StoreArtifactRef
import com.nullxoid.android.ui.AppUiState
import com.nullxoid.android.ui.MainBottomNavigation
import com.nullxoid.android.ui.MainTab
import com.nullxoid.android.ui.mainTabSwipeNavigation
import java.io.File
import java.io.RandomAccessFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    state: AppUiState,
    initialAddonId: String,
    onRefresh: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenAsk: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectAddon: (String) -> Unit,
    onRunStoreAddon: (String, String, String, String, String, String, Int, String, String, String, String) -> Unit,
    onSaveArtifact: (String, String) -> Unit,
    onShareArtifact: (String, String) -> Unit,
    onViewArtifact: (StoreArtifactRef) -> Unit,
    onLoadPreview: (StoreArtifactRef) -> Unit,
    onCloseViewer: () -> Unit,
    onResumeStoreJob: () -> Unit
) {
    val addons = state.storeCatalog.addons
        .filter { it.category == "creative-workflows" || it.id in creativeWorkflowAddonIds }
        .sortedBy {
            when (it.id) {
                "local-image-studio" -> 0
                "local-video-studio" -> 1
                "local-3d-studio" -> 2
                else -> 10
            }
        }
    var selectedAddonId by remember { mutableStateOf(initialAddonId.ifBlank { "local-image-studio" }) }
    LaunchedEffect(initialAddonId, addons.size) {
        if (initialAddonId.isNotBlank() && addons.any { it.id == initialAddonId }) {
            selectedAddonId = initialAddonId
        }
    }
    LaunchedEffect(state.activeStoreAddonId, addons.size) {
        val active = state.activeStoreAddonId.takeIf { id -> addons.any { it.id == id } }
        if (!active.isNullOrBlank()) selectedAddonId = active
    }
    val selectedAddon = addons.firstOrNull { it.id == selectedAddonId } ?: addons.firstOrNull()
    LaunchedEffect(selectedAddon?.id) {
        selectedAddon?.id?.let(onSelectAddon)
    }
    LaunchedEffect(state.activeStoreJobId) {
        if (state.activeStoreJobId.isNotBlank()) onResumeStoreJob()
    }

    var prompt by remember { mutableStateOf("") }
    val profiles = profileOptions(selectedAddon)
    var selectedProfileId by remember(selectedAddon?.id, profiles.size) {
        mutableStateOf(profiles.firstOrNull()?.id.orEmpty())
    }
    val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
    var sizeDraft by remember(selectedAddon?.id, profile?.id) {
        mutableStateOf(
            when (mediaKindForAddon(selectedAddon?.id.orEmpty())) {
                "video" -> profile?.videoSize ?: "1024x1024"
                else -> profile?.imageSize ?: "1024x1024"
            }
        )
    }
    val mediaKind = mediaKindForAddon(selectedAddon?.id.orEmpty())
    val context = LocalContext.current
    var audioMode by remember(selectedAddon?.id) {
        mutableStateOf(if (mediaKindForAddon(selectedAddon?.id.orEmpty()) == "video") "auto_generated" else "none")
    }
    var audioPrompt by remember(selectedAddon?.id) { mutableStateOf("") }
    var recordedAudioPath by remember(selectedAddon?.id) { mutableStateOf("") }
    var recorder by remember { mutableStateOf<WavVoiceRecorder?>(null) }
    var recording by remember { mutableStateOf(false) }
    var audioStatus by remember(selectedAddon?.id) { mutableStateOf("") }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording(
                context = context,
                onRecorder = { recorder = it },
                onRecordedPath = { recordedAudioPath = it },
                onRecording = { recording = it },
                onStatus = { audioStatus = it }
            )
        } else {
            audioStatus = "Microphone permission denied."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create") },
                actions = {
                    IconButton(
                        modifier = Modifier.testTag("store-refresh"),
                        onClick = onRefresh
                    ) { Icon(Icons.Default.Refresh, "Refresh Create") }
                }
            )
        },
        bottomBar = {
            MainBottomNavigation(
                selected = MainTab.Create,
                onOpenCreate = {},
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
                    onOpenCreate = {},
                    onOpenGallery = onOpenGallery,
                    onOpenAsk = onOpenAsk,
                    onOpenSettings = onOpenSettings
                )
                .testTag("store-screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "Create",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Private media through approval-gated workflows.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (addons.isEmpty()) {
                item { EmptyStoreCard(state.storeLoading) }
            } else {
                item {
                    StoreMediaSelector(
                        addons = addons,
                        selectedAddonId = selectedAddon?.id.orEmpty(),
                        onSelect = { option ->
                            selectedAddonId = option.addonId
                            selectedProfileId = ""
                            onSelectAddon(option.addonId)
                        }
                    )
                }
                item {
                    StoreAddonPanel(
                        addon = selectedAddon,
                        mediaKind = mediaKind,
                        profiles = profiles,
                        selectedProfileId = selectedProfileId.ifBlank { profiles.firstOrNull()?.id.orEmpty() },
                        prompt = prompt,
                        sizeDraft = sizeDraft,
                        loading = state.storeLoading,
                        onSelectProfile = { selectedProfileId = it },
                        onPromptChange = { prompt = it },
                        onSizeChange = { sizeDraft = it },
                        audioMode = audioMode,
                        audioPrompt = audioPrompt,
                        recordedAudioPath = recordedAudioPath,
                        recording = recording,
                        audioStatus = audioStatus,
                        onAudioModeChange = { audioMode = it },
                        onAudioPromptChange = { audioPrompt = it },
                        onStartRecording = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                startVoiceRecording(
                                    context = context,
                                    onRecorder = { recorder = it },
                                    onRecordedPath = { recordedAudioPath = it },
                                    onRecording = { recording = it },
                                    onStatus = { audioStatus = it }
                                )
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onStopRecording = {
                            stopVoiceRecording(
                                recorder = recorder,
                                onRecorder = { recorder = it },
                                onRecordedPath = { recordedAudioPath = it },
                                onRecording = { recording = it },
                                onStatus = { audioStatus = it }
                            )
                        },
                        onRun = {
                            val selectedProfile = profiles.firstOrNull {
                                it.id == selectedProfileId.ifBlank { profiles.firstOrNull()?.id.orEmpty() }
                            } ?: profiles.firstOrNull()
                            selectedAddon?.let {
                                onRunStoreAddon(
                                    it.id,
                                    it.approvalRoute?.action.orEmpty(),
                                    it.capabilities.firstOrNull().orEmpty(),
                                    prompt,
                                    sizeDraft,
                                    selectedProfile?.id.orEmpty(),
                                    selectedProfile?.durationMs ?: 0,
                                    selectedProfile?.format.orEmpty(),
                                    if (mediaKind == "video") audioMode else "none",
                                    recordedAudioPath,
                                    audioPrompt.ifBlank { "Generate synchronized audio that matches the video prompt." }
                                )
                            }
                        }
                    )
                }
                item {
                    StoreJobStatusCard(state)
                }
                item {
                    Text(
                        "Latest result",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    val latest = state.storeGallery.items.firstOrNull()
                    if (latest == null) {
                        Text(
                            "No recent result yet. Gallery will show your private media after generation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        StoreGalleryCard(
                            item = latest,
                            title = safeArtifactTitle(latest, 0),
                            previewBytes = state.storePreviewBytes[latest.artifactId],
                            onLoadPreview = onLoadPreview,
                            onView = { onViewArtifact(latest) },
                            onSave = { onSaveArtifact(latest.artifactId, latest.mimeType) },
                            onShare = { onShareArtifact(latest.artifactId, latest.mimeType) }
                        )
                    }
                }
                if (state.storeSaveStatus.isNotBlank()) {
                    item {
                        Text(state.storeSaveStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    state.storeViewerArtifact?.let { artifact ->
        StoreMediaViewer(
            artifact = artifact,
            bytes = state.storeViewerBytes,
            loading = state.storeViewerLoading,
            error = state.storeViewerError,
            onClose = onCloseViewer,
            onSave = { onSaveArtifact(artifact.artifactId, artifact.mimeType) },
            onShare = { onShareArtifact(artifact.artifactId, artifact.mimeType) }
        )
    }
}

private fun startVoiceRecording(
    context: Context,
    onRecorder: (WavVoiceRecorder?) -> Unit,
    onRecordedPath: (String) -> Unit,
    onRecording: (Boolean) -> Unit,
    onStatus: (String) -> Unit
) {
    val dir = File(context.cacheDir, "store_voice_inputs").apply { mkdirs() }
    val output = File(dir, "voice-${System.currentTimeMillis()}.wav")
    runCatching {
        val recorder = WavVoiceRecorder.create(output)
        recorder.start()
        onRecordedPath(output.absolutePath)
        onRecorder(recorder)
        onRecording(true)
        onStatus("Recording voice as WAV...")
    }.onFailure { error ->
        onRecorder(null)
        onRecording(false)
        onStatus(error.message ?: "Could not start recording.")
    }
}

private fun stopVoiceRecording(
    recorder: WavVoiceRecorder?,
    onRecorder: (WavVoiceRecorder?) -> Unit,
    onRecordedPath: (String) -> Unit,
    onRecording: (Boolean) -> Unit,
    onStatus: (String) -> Unit
) {
    val ready = runCatching { recorder?.stop() == true }.getOrDefault(false)
    onRecorder(null)
    onRecording(false)
    if (ready) {
        onStatus("WAV voice clip ready")
    } else if (recorder != null) {
        onRecordedPath("")
        onStatus("Recording was too short. Try again.")
    }
}

private class WavVoiceRecorder private constructor(
    private val audioRecord: AudioRecord,
    private val output: File,
    private val bufferSize: Int,
    private val sampleRate: Int,
    private val channelCount: Int
) {
    @Volatile
    private var running = false
    private var worker: Thread? = null

    fun start() {
        RandomAccessFile(output, "rw").use { file ->
            file.setLength(0)
            file.write(wavHeader(dataSize = 0, sampleRate = sampleRate, channelCount = channelCount))
        }
        audioRecord.startRecording()
        running = true
        worker = Thread {
            RandomAccessFile(output, "rw").use { file ->
                file.seek(WAV_HEADER_BYTES.toLong())
                val buffer = ByteArray(bufferSize)
                while (running) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        file.write(buffer, 0, read)
                    }
                }
                val dataSize = (file.length() - WAV_HEADER_BYTES).coerceAtLeast(0L)
                file.seek(0)
                file.write(wavHeader(dataSize, sampleRate, channelCount))
            }
        }.apply {
            name = "NullXoid-WAV-VoiceRecorder"
            start()
        }
    }

    fun stop(): Boolean {
        running = false
        runCatching { audioRecord.stop() }
        worker?.join(1_500)
        runCatching { audioRecord.release() }
        worker?.join(500)
        val ready = output.exists() && output.length() > WAV_HEADER_BYTES
        if (!ready) {
            runCatching { output.delete() }
        }
        return ready
    }

    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_COUNT = 1
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        @SuppressLint("MissingPermission")
        fun create(output: File): WavVoiceRecorder {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
            require(minBuffer > 0) { "WAV recording is unavailable on this device." }
            val bufferSize = maxOf(minBuffer, SAMPLE_RATE / 5)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                ENCODING,
                bufferSize
            )
            require(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                "Could not initialize WAV recording."
            }
            return WavVoiceRecorder(audioRecord, output, bufferSize, SAMPLE_RATE, CHANNEL_COUNT)
        }
    }
}

private const val WAV_HEADER_BYTES = 44

private fun wavHeader(dataSize: Long, sampleRate: Int, channelCount: Int): ByteArray {
    val bitsPerSample = 16
    val byteRate = sampleRate * channelCount * bitsPerSample / 8
    val blockAlign = channelCount * bitsPerSample / 8
    val header = ByteArray(WAV_HEADER_BYTES)
    fun ascii(offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).copyInto(header, offset)
    }
    ascii(0, "RIFF")
    writeLeInt(header, 4, 36L + dataSize)
    ascii(8, "WAVE")
    ascii(12, "fmt ")
    writeLeInt(header, 16, 16)
    writeLeShort(header, 20, 1)
    writeLeShort(header, 22, channelCount)
    writeLeInt(header, 24, sampleRate.toLong())
    writeLeInt(header, 28, byteRate.toLong())
    writeLeShort(header, 32, blockAlign)
    writeLeShort(header, 34, bitsPerSample)
    ascii(36, "data")
    writeLeInt(header, 40, dataSize)
    return header
}

private fun writeLeInt(target: ByteArray, offset: Int, value: Long) {
    target[offset] = (value and 0xff).toByte()
    target[offset + 1] = ((value shr 8) and 0xff).toByte()
    target[offset + 2] = ((value shr 16) and 0xff).toByte()
    target[offset + 3] = ((value shr 24) and 0xff).toByte()
}

private fun writeLeShort(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value and 0xff).toByte()
    target[offset + 1] = ((value shr 8) and 0xff).toByte()
}

@Composable
private fun EmptyStoreCard(loading: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Create options are unavailable.")
            if (loading) {
                Spacer(Modifier.height(8.dp))
                Text("Loading catalog...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StoreMediaSelector(
    addons: List<StoreAddon>,
    selectedAddonId: String,
    onSelect: (StoreMediaOption) -> Unit
) {
    val options = mediaOptions(addons, selectedAddonId)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store-media-${option.mediaKind}"),
                onClick = { onSelect(option) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AssistChip(
                        onClick = { onSelect(option) },
                        label = { Text(if (option.selected) "Selected" else "Choose") }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreAddonPanel(
    addon: StoreAddon?,
    mediaKind: String,
    profiles: List<StoreProfileOption>,
    selectedProfileId: String,
    prompt: String,
    sizeDraft: String,
    loading: Boolean,
    audioMode: String,
    audioPrompt: String,
    recordedAudioPath: String,
    recording: Boolean,
    audioStatus: String,
    onSelectProfile: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onSizeChange: (String) -> Unit,
    onAudioModeChange: (String) -> Unit,
    onAudioPromptChange: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRun: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store-${addon?.id ?: "addon"}-detail")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                when (mediaKind) {
                    "video" -> "Video"
                    "3d" -> "3D"
                    else -> "Image"
                },
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "Creative Workflows",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(addon?.name ?: "Creative Workflow", style = MaterialTheme.typography.titleSmall)
            Text(
                addon?.description.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Job type", style = MaterialTheme.typography.titleSmall)
            profiles.forEach { profile ->
                FilterChip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store-job-type-${profile.id}"),
                    selected = profile.id == selectedProfileId,
                    onClick = { onSelectProfile(profile.id) },
                    label = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(profile.label)
                            if (profile.description.isNotBlank()) {
                                Text(
                                    profile.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store-prompt"),
                value = prompt,
                onValueChange = onPromptChange,
                label = { Text("Prompt") },
                minLines = 3
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = sizeDraft,
                onValueChange = onSizeChange,
                label = { Text(if (mediaKind == "video") "Video size" else "Image size") },
                singleLine = true
            )
            val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mediaKind == "video") {
                    AssistChip(
                        onClick = {},
                        label = { Text("${((selectedProfile?.durationMs ?: 0) / 1000).coerceAtLeast(1)} seconds") }
                    )
                }
                if (mediaKind == "3d") {
                    AssistChip(
                        onClick = {},
                        label = { Text("Format: ${(selectedProfile?.format ?: "glb").uppercase()}") }
                    )
                }
                AssistChip(onClick = {}, label = { Text("Approval required") })
            }
            if (mediaKind == "video") {
                VideoAudioOptions(
                    audioMode = audioMode,
                    audioPrompt = audioPrompt,
                    recordedAudioPath = recordedAudioPath,
                    recording = recording,
                    status = audioStatus,
                    onAudioModeChange = onAudioModeChange,
                    onAudioPromptChange = onAudioPromptChange,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording
                )
            }
            val missingRecordedAudio = mediaKind == "video" &&
                audioMode == "recorded_voice" &&
                recordedAudioPath.isBlank()
            Button(
                modifier = Modifier
                    .height(52.dp)
                    .testTag("store-${addon?.id ?: "addon"}-generate"),
                enabled = !loading && addon != null && !recording && !missingRecordedAudio,
                onClick = onRun
            ) {
                Text(if (loading) "Checking job..." else "Generate with approval")
            }
        }
    }
}

@Composable
private fun VideoAudioOptions(
    audioMode: String,
    audioPrompt: String,
    recordedAudioPath: String,
    recording: Boolean,
    status: String,
    onAudioModeChange: (String) -> Unit,
    onAudioPromptChange: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Audio", style = MaterialTheme.typography.titleSmall)
        listOf(
            "auto_generated" to "Auto audio",
            "recorded_voice" to "Record voice",
            "none" to "No audio"
        ).forEach { (mode, label) ->
            FilterChip(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store-video-audio-$mode"),
                selected = audioMode == mode,
                onClick = { onAudioModeChange(mode) },
                label = {
                    Text(
                        when (mode) {
                            "auto_generated" -> "$label - match the prompt"
                            "recorded_voice" -> "$label - private voice input"
                            else -> label
                        }
                    )
                }
            )
        }
        if (audioMode == "auto_generated") {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = audioPrompt,
                onValueChange = onAudioPromptChange,
                label = { Text("Audio direction") },
                placeholder = { Text("Match the scene with synchronized sound") },
                minLines = 2
            )
        }
        if (audioMode == "recorded_voice") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !recording,
                    onClick = onStartRecording
                ) { Text("Start recording") }
                OutlinedButton(
                    enabled = recording,
                    onClick = onStopRecording
                ) { Text("Stop") }
            }
            val readyText = if (recordedAudioPath.isNotBlank()) "Voice clip ready" else "No voice clip yet"
            Text(
                status.ifBlank { readyText },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StoreJobStatusCard(state: AppUiState) {
    val action = state.storeAction
    val status = friendlyStoreStatus(action?.status, action?.errorCode)
    var showDetails by remember(action?.storeJobId, action?.status, action?.errorCode) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store-job-status-card")
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Job status", style = MaterialTheme.typography.labelMedium)
                    Text(status.label, style = MaterialTheme.typography.titleMedium)
                }
                AssistChip(onClick = {}, label = { Text("${(status.progress * 100).toInt()}%") })
            }
            LinearProgressIndicator(
                progress = status.progress,
                modifier = Modifier.fillMaxWidth()
            )
            Text(status.helper, style = MaterialTheme.typography.bodySmall)
            if (action?.status == "pending_approval") {
                Text(
                    "Return here after approving in NullBridge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Hide details" else "Details")
            }
            if (showDetails) {
                Text("Status: ${action?.status ?: "ready"}", style = MaterialTheme.typography.labelSmall)
                action?.errorCode?.let { Text("Code: $it", style = MaterialTheme.typography.labelSmall) }
                (action?.storeJobId ?: action?.jobId)?.takeIf { it.isNotBlank() }?.let {
                    Text("Job: $it", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                action?.requestId?.takeIf { it.isNotBlank() }?.let {
                    Text("Approval: $it", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun StoreGalleryCard(
    item: StoreArtifactRef,
    title: String,
    previewBytes: ByteArray?,
    onLoadPreview: (StoreArtifactRef) -> Unit,
    onView: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    LaunchedEffect(item.artifactId, item.thumbnailUrl, item.posterUrl, item.previewUrl) {
        onLoadPreview(item)
    }
    var showDetails by remember(item.artifactId) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store-gallery-card")
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StorePreviewBox(item = item, bytes = previewBytes)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(artifactTypeLabel(item)) })
                AssistChip(onClick = {}, label = { Text(item.status.ifBlank { "ready" }) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onView) { Text("View") }
                OutlinedButton(onClick = onSave) { Text("Save to device") }
                OutlinedButton(onClick = onShare) { Text("Share") }
            }
            OutlinedButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Hide details" else "Details")
            }
            if (showDetails) {
                Text("Artifact: ${item.artifactId}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("MIME: ${item.mimeType}", style = MaterialTheme.typography.labelSmall)
                if (item.createdAt.isNotBlank()) {
                    Text("Created: ${item.createdAt}", style = MaterialTheme.typography.labelSmall)
                }
                if (item.format.isNotBlank()) {
                    Text("Format: ${item.format}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StorePreviewBox(item: StoreArtifactRef, bytes: ByteArray?) {
    val imageBitmap = remember(bytes) {
        bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size).asImageBitmap() }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "${artifactTypeLabel(item)} preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(artifactTypeLabel(item), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun StoreMediaViewer(
    artifact: StoreArtifactRef,
    bytes: ByteArray,
    loading: Boolean,
    error: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(artifactTypeLabel(artifact), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close viewer", tint = Color.White)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loading -> Text("Opening...", color = Color.White)
                        error.isNotBlank() -> Text(error, color = Color.White)
                        artifact.mimeType.startsWith("image/") && bytes.isNotEmpty() -> ZoomableImage(bytes)
                        artifact.mimeType.startsWith("video/") && bytes.isNotEmpty() ->
                            InlineVideoPlayer(
                                context = context,
                                artifactId = artifact.artifactId,
                                bytes = bytes
                            )
                        artifact.mimeType.startsWith("video/") ->
                            Text("Video is ready. Save or Share to open it with a player.", color = Color.White)
                        else -> Text("Preview is not available yet.", color = Color.White)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onSave) { Text("Save to device") }
                    OutlinedButton(onClick = onShare) { Text("Share") }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun InlineVideoPlayer(
    context: Context,
    artifactId: String,
    bytes: ByteArray
) {
    val videoFile = remember(artifactId, bytes) {
        val dir = File(context.cacheDir, "shared_store_artifacts").apply { mkdirs() }
        File(dir, "$artifactId.mp4").also { it.writeBytes(bytes) }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black, RoundedCornerShape(12.dp))
                .testTag("store-video-player"),
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    val videoView = this
                    val controller = MediaController(viewContext).apply {
                        setAnchorView(videoView)
                    }
                    setMediaController(controller)
                    setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.isLooping = false
                        start()
                        controller.show(0)
                    }
                    setOnCompletionListener {
                        controller.show(0)
                    }
                    requestFocus()
                    tag = videoFile.absolutePath
                    setVideoPath(videoFile.absolutePath)
                }
            },
            update = { view ->
                if (view.tag != videoFile.absolutePath) {
                    view.tag = videoFile.absolutePath
                    view.setVideoPath(videoFile.absolutePath)
                }
            }
        )
        Text(
            "Tap the video for playback controls.",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ZoomableImage(bytes: ByteArray) {
    val bitmap = remember(bytes) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Image(
        bitmap = bitmap,
        contentDescription = "Generated image preview",
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    offset += pan
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ),
        contentScale = ContentScale.Fit
    )
}
