package com.nullxoid.android.ui.store

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.material3.TextButton
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
import androidx.core.content.FileProvider
import com.nullxoid.android.data.model.StoreAddon
import com.nullxoid.android.data.model.StoreArtifactRef
import com.nullxoid.android.ui.AppUiState
import java.io.File
import java.io.RandomAccessFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    state: AppUiState,
    initialAddonId: String,
    onRefresh: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenAsk: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectAddon: (String) -> Unit,
    onRunStoreAddon: (String, String, String, String, String, String, Int, String, String, String, String, String, Map<String, String>, Boolean) -> Unit,
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
    LaunchedEffect(state.activeStoreAddonId, addons.size, initialAddonId) {
        if (initialAddonId.isBlank()) {
            val active = state.activeStoreAddonId.takeIf { id -> addons.any { it.id == id } }
            if (!active.isNullOrBlank()) selectedAddonId = active
        }
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
    var sourceImagePaths by remember(selectedAddon?.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var sourceImageStatus by remember(selectedAddon?.id) { mutableStateOf("") }
    var mirrorSideView by remember(selectedAddon?.id) { mutableStateOf(false) }
    var pendingSourceImageRole by remember(selectedAddon?.id) { mutableStateOf("front") }
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
    val sourceImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            sourceImageStatus = "No source image selected."
        } else {
            runCatching {
                val role = pendingSourceImageRole.takeIf { candidate ->
                    model3dSourceImageSlots.any { it.role == candidate }
                } ?: "front"
                sourceImagePaths = sourceImagePaths + (role to copySourceImageFor3d(context, uri, role))
                val label = model3dSourceImageSlots.firstOrNull { it.role == role }?.label ?: "Image"
                sourceImageStatus = "$label ready"
            }.onFailure {
                sourceImagePaths = sourceImagePaths - pendingSourceImageRole
                sourceImageStatus = "Source image could not be loaded."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create") },
                navigationIcon = {
                    IconButton(onClick = onOpenHome) { Icon(Icons.Default.Home, "Home") }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.testTag("store-refresh"),
                        onClick = onRefresh
                    ) { Icon(Icons.Default.Refresh, "Refresh Create") }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .navigationBarsPadding()
                .imePadding()
                .fillMaxSize()
                .testTag("store-screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "store-header-${selectedAddon?.id.orEmpty()}") {
                CreateStudioHeader(
                    mediaKind = mediaKind,
                    addons = addons,
                    selectedAddonId = selectedAddon?.id.orEmpty(),
                    latestResultReady = latestRenderableArtifact(state.storeGallery.items) != null,
                    onSelect = { option ->
                        selectedAddonId = option.addonId
                        selectedProfileId = ""
                        onSelectAddon(option.addonId)
                    },
                    onOpenGallery = onOpenGallery
                )
            }

            if (addons.isEmpty()) {
                item(key = "store-empty") { EmptyStoreCard(state.storeLoading) }
            } else {
                item(key = "store-addon-panel-${selectedAddon?.id.orEmpty()}") {
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
                        sourceImagePaths = sourceImagePaths,
                        sourceImageStatus = sourceImageStatus,
                        mirrorSideView = mirrorSideView,
                        onAudioModeChange = { audioMode = it },
                        onAudioPromptChange = { audioPrompt = it },
                        onPickSourceImage = { role ->
                            pendingSourceImageRole = role
                            sourceImageLauncher.launch("image/*")
                        },
                        onClearSourceImage = { role ->
                            sourceImagePaths = sourceImagePaths - role
                            sourceImageStatus = ""
                        },
                        onMirrorSideViewChange = { mirrorSideView = it },
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
                                    audioPrompt.ifBlank { "Generate synchronized audio that matches the video prompt." },
                                    if (mediaKind == "3d") sourceImagePaths["front"].orEmpty() else "",
                                    if (mediaKind == "3d") sourceImagePaths else emptyMap(),
                                    if (mediaKind == "3d") mirrorSideView && model3dHasOneSideSource(sourceImagePaths) else false
                                )
                            }
                        }
                    )
                }
                item(key = "store-job-status-${state.storeAction?.storeJobId ?: state.activeStoreJobId}") {
                    StoreJobStatusCard(state)
                }
                item(key = "store-latest-title") {
                    Text(
                        "Latest result",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val latest = latestRenderableArtifact(state.storeGallery.items)
                item(key = latest?.let { stableArtifactListKey(it, 0, "store-latest") } ?: "store-latest-empty") {
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
                    item(key = "store-save-status") {
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
            previewBytes = state.storePreviewBytes[artifact.artifactId] ?: ByteArray(0),
            loading = state.storeViewerLoading,
            error = state.storeViewerError,
            onClose = onCloseViewer,
            onSave = { onSaveArtifact(artifact.artifactId, artifact.mimeType) },
            onShare = { onShareArtifact(artifact.artifactId, artifact.mimeType) }
        )
    }
}

private fun selectedWorkflowLabel(mediaKind: String): String = when (mediaKind) {
    "video" -> "Video selected"
    "3d" -> "3D Beta selected"
    else -> "Image selected"
}

private fun studioTitle(mediaKind: String): String = when (mediaKind) {
    "video" -> "Video Studio"
    "3d" -> "3D Beta Studio"
    else -> "Image Studio"
}

private fun studioDescription(mediaKind: String): String = when (mediaKind) {
    "video" -> "Create a private motion clip. Use a prompt or start from an image when available."
    "3d" -> "Generate private GLB/glTF model artifacts from an image."
    else -> "Generate private images through an approval-gated workflow."
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
private fun CreateStudioHeader(
    mediaKind: String,
    addons: List<StoreAddon>,
    selectedAddonId: String,
    latestResultReady: Boolean,
    onSelect: (StoreMediaOption) -> Unit,
    onOpenGallery: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            studioTitle(mediaKind),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            studioDescription(mediaKind),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StoreMediaSelector(
            addons = addons,
            selectedAddonId = selectedAddonId,
            onSelect = onSelect
        )
        if (latestResultReady) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Latest result ready",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = onOpenGallery) { Text("Open Gallery") }
                }
            }
        }
        if (mediaKind == "3d") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFF3D6),
                border = BorderStroke(1.dp, Color(0xFFE1B95C))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "3D is experimental",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8A5B00)
                    )
                    Text(
                        "Image-to-3D works. Mesh and wrapping quality may vary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B4C11)
                    )
                }
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                modifier = Modifier.testTag("store-media-${option.mediaKind}"),
                selected = option.selected,
                onClick = { onSelect(option) },
                label = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(option.label)
                        if (option.mediaKind == "3d") {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                            ) {
                                Text(
                                    "Beta",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            )
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
    sourceImagePaths: Map<String, String>,
    sourceImageStatus: String,
    mirrorSideView: Boolean,
    onSelectProfile: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onSizeChange: (String) -> Unit,
    onAudioModeChange: (String) -> Unit,
    onAudioPromptChange: (String) -> Unit,
    onPickSourceImage: (String) -> Unit,
    onClearSourceImage: (String) -> Unit,
    onMirrorSideViewChange: (Boolean) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRun: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("store-${addon?.id ?: "addon"}-detail"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (mediaKind) {
            "video" -> VideoStudioContent(
                profiles = profiles,
                selectedProfileId = selectedProfileId,
                prompt = prompt,
                audioMode = audioMode,
                audioPrompt = audioPrompt,
                recordedAudioPath = recordedAudioPath,
                recording = recording,
                audioStatus = audioStatus,
                onSelectProfile = onSelectProfile,
                onPromptChange = onPromptChange,
                onAudioModeChange = onAudioModeChange,
                onAudioPromptChange = onAudioPromptChange,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording
            )
            "3d" -> Model3DStudioContent(
                profiles = profiles,
                selectedProfileId = selectedProfileId,
                prompt = prompt,
                sourceImagePaths = sourceImagePaths,
                sourceImageStatus = sourceImageStatus,
                mirrorSideView = mirrorSideView,
                onSelectProfile = onSelectProfile,
                onPromptChange = onPromptChange,
                onPickSourceImage = onPickSourceImage,
                onClearSourceImage = onClearSourceImage,
                onMirrorSideViewChange = onMirrorSideViewChange
            )
            else -> ImageStudioContent(
                profiles = profiles,
                selectedProfileId = selectedProfileId,
                prompt = prompt,
                sizeDraft = sizeDraft,
                onSelectProfile = onSelectProfile,
                onPromptChange = onPromptChange,
                onSizeChange = onSizeChange
            )
        }
        val missingRecordedAudio = mediaKind == "video" &&
            audioMode == "recorded_voice" &&
            recordedAudioPath.isBlank()
        val missingSourceImage = mediaKind == "3d" && !model3dPrimarySourceReady(sourceImagePaths)
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("store-${addon?.id ?: "addon"}-generate"),
            enabled = !loading && addon != null && !recording && !missingRecordedAudio && !missingSourceImage,
            onClick = onRun
        ) {
            Text(
                when {
                    loading -> "Checking job..."
                    missingRecordedAudio -> "Record voice first"
                    missingSourceImage -> "Choose source image first"
                    mediaKind == "video" -> "Create video"
                    mediaKind == "3d" -> "Create 3D model"
                    else -> "Create image"
                }
            )
        }
    }
}

@Composable
private fun ImageStudioContent(
    profiles: List<StoreProfileOption>,
    selectedProfileId: String,
    prompt: String,
    sizeDraft: String,
    onSelectProfile: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onSizeChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StudioSectionTitle("Prompt")
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("store-prompt"),
            value = prompt,
            onValueChange = onPromptChange,
            placeholder = { Text("Describe the picture you want to create...") },
            minLines = 4
        )
        Text(
            "Tip: include subject, setting, style, lighting, and mood.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StudioSectionTitle("Job type")
        CompactProfileChips(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            onSelectProfile = onSelectProfile
        )
        StudioSectionTitle("Output")
        ImageOutputChips(sizeDraft = sizeDraft, onSizeChange = onSizeChange)
        AdvancedSettingsRow("Advanced settings")
    }
}

@Composable
private fun VideoStudioContent(
    profiles: List<StoreProfileOption>,
    selectedProfileId: String,
    prompt: String,
    audioMode: String,
    audioPrompt: String,
    recordedAudioPath: String,
    recording: Boolean,
    audioStatus: String,
    onSelectProfile: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onAudioModeChange: (String) -> Unit,
    onAudioPromptChange: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StudioSectionTitle("Prompt")
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("store-prompt"),
            value = prompt,
            onValueChange = onPromptChange,
            placeholder = { Text("Describe the motion, camera movement, subject, and mood...") },
            minLines = 4
        )
        Text(
            "Tip: keep video prompts short and specific.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StudioSectionTitle("Job type")
        CompactProfileChips(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            onSelectProfile = onSelectProfile
        )
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
        StudioInfoRow(
            title = "Approval required",
            helper = "Video jobs stay queued until the workflow is approved."
        )
    }
}

@Composable
private fun Model3DStudioContent(
    profiles: List<StoreProfileOption>,
    selectedProfileId: String,
    prompt: String,
    sourceImagePaths: Map<String, String>,
    sourceImageStatus: String,
    mirrorSideView: Boolean,
    onSelectProfile: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onPickSourceImage: (String) -> Unit,
    onClearSourceImage: (String) -> Unit,
    onMirrorSideViewChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Model3DSourceImageOptions(
            sourceImagePaths = sourceImagePaths,
            status = sourceImageStatus,
            mirrorSideView = mirrorSideView,
            onPickSourceImage = onPickSourceImage,
            onClearSourceImage = onClearSourceImage,
            onMirrorSideViewChange = onMirrorSideViewChange
        )
        StudioSectionTitle("Prompt")
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("store-prompt"),
            value = prompt,
            onValueChange = onPromptChange,
            placeholder = { Text("Optional: describe the object, material, or shape...") },
            minLines = 3
        )
        StudioSectionTitle("Job type")
        CompactProfileChips(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            onSelectProfile = onSelectProfile
        )
        AdvancedSettingsRow("Advanced mesh settings")
    }
}

@Composable
private fun StudioSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CompactProfileChips(
    profiles: List<StoreProfileOption>,
    selectedProfileId: String,
    onSelectProfile: (String) -> Unit
) {
    if (profiles.isEmpty()) {
        Text(
            "No job types available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        profiles.chunked(2).forEach { rowProfiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowProfiles.forEach { profile ->
                    FilterChip(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("store-job-type-${profile.id}"),
                        selected = profile.id == selectedProfileId,
                        onClick = { onSelectProfile(profile.id) },
                        label = {
                            Text(
                                profile.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
                if (rowProfiles.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ImageOutputChips(sizeDraft: String, onSizeChange: (String) -> Unit) {
    val outputs = listOf(
        "1024x1024" to "Square",
        "768x1024" to "Portrait",
        "1344x768" to "Landscape"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        outputs.forEach { (size, label) ->
            FilterChip(
                selected = sizeDraft == size,
                onClick = { onSizeChange(size) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun StudioInfoRow(title: String, helper: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdvancedSettingsRow(label: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}


@Composable
private fun Model3DSourceImageOptions(
    sourceImagePaths: Map<String, String>,
    status: String,
    mirrorSideView: Boolean,
    onPickSourceImage: (String) -> Unit,
    onClearSourceImage: (String) -> Unit,
    onMirrorSideViewChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("3D source images", style = MaterialTheme.typography.titleSmall)
        Text(
            "Start with one clear image. Add side or back views when you have them. Mesh and wrapping are still experimental.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${model3dSelectedSourceCount(sourceImagePaths)} of ${model3dSourceImageSlots.size} images selected",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        model3dSourceImageSlots.forEach { slot ->
            val ready = !sourceImagePaths[slot.role].isNullOrBlank()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        slot.label + if (slot.required) " *" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (slot.required) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Text(
                        if (ready) "Ready" else slot.helper,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    modifier = Modifier.testTag("store-3d-source-${slot.role}"),
                    onClick = { onPickSourceImage(slot.role) }
                ) {
                    Text(if (ready) "Change" else "Choose")
                }
                if (ready) {
                    IconButton(
                        modifier = Modifier.testTag("store-3d-source-${slot.role}-clear"),
                        onClick = { onClearSourceImage(slot.role) }
                    ) {
                        Icon(Icons.Default.Close, "Clear ${slot.label}")
                    }
                }
            }
        }
        val hasOneSide = model3dHasOneSideSource(sourceImagePaths)
        FilterChip(
            selected = mirrorSideView,
            enabled = hasOneSide,
            onClick = { onMirrorSideViewChange(!mirrorSideView) },
            label = {
                Text(
                    if (hasOneSide) {
                        "Mirror one side for the opposite side"
                    } else {
                        "Add one side image to enable side mirroring"
                    }
                )
            }
        )
        val readyText = status.ifBlank {
            if (model3dPrimarySourceReady(sourceImagePaths)) {
                "Ready for approval-gated GLB generation"
            } else {
                "Choose the front / main image first"
            }
        }
        Text(
            readyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


private fun copySourceImageFor3d(context: Context, uri: Uri, role: String): String {
    val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
    val extension = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        else -> "png"
    }
    val safeRole = role.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifBlank { "source" }
    val targetDir = File(context.filesDir, "store_3d_source_images").apply { mkdirs() }
    targetDir.listFiles()
        ?.filter { it.name.startsWith("source-image-$safeRole.") }
        ?.forEach { old -> runCatching { old.delete() } }
    val target = File(targetDir, "source-image-$safeRole.${System.currentTimeMillis()}.$extension")
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Source image could not be opened." }
        target.outputStream().use { output -> input.copyTo(output) }
    }
    require(target.length() > 0L) { "Source image is empty." }
    return target.absolutePath
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
    val actionsEnabled = isActionableArtifact(item)
    LaunchedEffect(item.artifactId, item.thumbnailUrl, item.posterUrl, item.previewUrl, item.modelPreviewUrl) {
        if (previewLoadAllowed(item)) {
            onLoadPreview(item)
        }
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
                if (isExperimentalModel3d(item)) {
                    AssistChip(onClick = {}, label = { Text("Beta") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = actionsEnabled, onClick = onView) { Text("View") }
                OutlinedButton(enabled = actionsEnabled, onClick = onSave) { Text("Save to device") }
                OutlinedButton(enabled = actionsEnabled, onClick = onShare) { Text("Share") }
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
                if (isExperimentalModel3d(item)) {
                    val quality = betaQualityLabel(item)
                    val classification = betaClassificationLabel(item)
                    val assetType = betaAssetTypeLabel(item)
                    val runtimeFamily = betaRuntimeFamilyLabel(item)
                    val geometryConfidence = betaGeometryConfidenceLabel(item)
                    val recommendedFallback = betaRecommendedFallbackLabel(item)
                    Text("3D provider: Experimental beta", style = MaterialTheme.typography.labelSmall)
                    if (runtimeFamily.isNotBlank()) {
                        Text("Runtime: $runtimeFamily", style = MaterialTheme.typography.labelSmall)
                    }
                    if (quality.isNotBlank()) {
                        Text("Quality: $quality", style = MaterialTheme.typography.labelSmall)
                    }
                    if (classification.isNotBlank()) {
                        Text("Type: $classification", style = MaterialTheme.typography.labelSmall)
                    }
                    if (assetType.isNotBlank()) {
                        Text("Asset: $assetType", style = MaterialTheme.typography.labelSmall)
                    }
                    if (geometryConfidence.isNotBlank()) {
                        Text("Geometry confidence: $geometryConfidence", style = MaterialTheme.typography.labelSmall)
                    }
                    if (recommendedFallback.isNotBlank()) {
                        Text("Fallback: $recommendedFallback", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Maps: ${betaMapAvailabilityLabel(item)}", style = MaterialTheme.typography.labelSmall)
                    item.sourceWarnings.take(3).forEach { warning ->
                        Text("Source warning: $warning", style = MaterialTheme.typography.labelSmall)
                    }
                    item.knownFlaws.take(3).forEach { flaw ->
                        Text("Known limit: $flaw", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StorePreviewBox(item: StoreArtifactRef, bytes: ByteArray?) {
    val previewBitmap = remember(item.artifactId, item.mimeType, bytes) {
        bytes?.let {
            runCatching {
                when {
                    item.mimeType.startsWith("video/") -> decodeVideoFramePreview(it)?.asImageBitmap()
                    else -> BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = "${artifactTypeLabel(item)} preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(artifactTypeLabel(item), style = MaterialTheme.typography.titleMedium)
                if (item.mimeType.startsWith("video/")) {
                    Text("Preview loading", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun decodeVideoFramePreview(bytes: ByteArray) =
    MediaMetadataRetriever().run {
        try {
            setDataSource(ByteArrayVideoDataSource(bytes))
            getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            release()
        }
    }

private class ByteArrayVideoDataSource(
    private val bytes: ByteArray
) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0 || position >= bytes.size) return -1
        val length = minOf(size, bytes.size - position.toInt())
        bytes.copyInto(destination = buffer, destinationOffset = offset, startIndex = position.toInt(), endIndex = position.toInt() + length)
        return length
    }

    override fun getSize(): Long = bytes.size.toLong()

    override fun close() = Unit
}

@Composable
fun StoreMediaViewer(
    artifact: StoreArtifactRef,
    bytes: ByteArray,
    previewBytes: ByteArray,
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
                        isModelArtifact(artifact) && bytes.isNotEmpty() ->
                            InteractiveGlbViewer(
                                artifactId = artifact.artifactId,
                                bytes = bytes,
                                modifier = Modifier.fillMaxSize()
                            )
                        (artifact.mimeType.startsWith("model/") || artifact.format in setOf("glb", "gltf")) &&
                            previewBytes.isNotEmpty() ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ZoomableImage(previewBytes)
                                Text("Rendered GLB preview. Save the GLB to open the model file.", color = Color.White)
                            }
                        artifact.mimeType.startsWith("model/") || artifact.format in setOf("glb", "gltf") ->
                            Text("3D preview not yet available. Save the GLB to open it in a model viewer.", color = Color.White)
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
    val videoUri = remember(artifactId, videoFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
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
                    tag = artifactId
                    setVideoURI(videoUri)
                }
            },
            update = { view ->
                if (view.tag != artifactId) {
                    view.tag = artifactId
                    view.setVideoURI(videoUri)
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
