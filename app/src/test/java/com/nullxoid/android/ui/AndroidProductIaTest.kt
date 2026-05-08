package com.nullxoid.android.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidProductIaTest {
    @Test
    fun bottomNavigationUsesCreationAppLabels() {
        val source = File("src/main/java/com/nullxoid/android/ui/MainBottomNavigation.kt").readText()

        assertTrue(source.contains("Text(\"Create\")"))
        assertTrue(source.contains("Text(\"Gallery\")"))
        assertTrue(source.contains("Text(\"Ask\")"))
        assertTrue(source.contains("Text(\"Settings\")"))
        assertFalse(source.contains("Text(\"Home\")"))
        assertFalse(source.contains("Text(\"Chats\")"))
        assertFalse(source.contains("Text(\"Store\")"))
        assertFalse(source.contains("bottom-nav-home"))
        assertFalse(source.contains("MainTab.Home"))
    }

    @Test
    fun signedInDefaultRouteIsHome() {
        val source = File("src/main/java/com/nullxoid/android/ui/NullXoidNavHost.kt").readText()
        val home = File("src/main/java/com/nullxoid/android/ui/home/HomeScreen.kt").readText()
        val homeModels = File("src/main/java/com/nullxoid/android/ui/home/HomeUiModels.kt").readText()

        assertTrue(source.contains("const val Home = \"home\""))
        assertTrue(source.contains("if (state.onboardingCompleted) Routes.Home else Routes.Onboarding"))
        assertTrue(source.contains("if (state.auth.authenticated) Routes.Home else Routes.Login"))
        assertTrue(home.contains("What do you want to create?"))
        assertTrue(home.contains("Ask NullXoid"))
        assertTrue(homeModels.contains("Mesh and wrapping are experimental"))
    }

    @Test
    fun detailScreensUseHomeNavigationInsteadOfBottomTabs() {
        val create = File("src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt").readText()
        val gallery = File("src/main/java/com/nullxoid/android/ui/store/GalleryScreen.kt").readText()
        val ask = File("src/main/java/com/nullxoid/android/ui/chat/ChatListScreen.kt").readText()
        val settings = File("src/main/java/com/nullxoid/android/ui/settings/SettingsScreen.kt").readText()
        val home = File("src/main/java/com/nullxoid/android/ui/home/HomeScreen.kt").readText()

        assertTrue(create.contains("Icon(Icons.Default.Home, \"Home\")"))
        assertTrue(gallery.contains("Icon(Icons.Default.Home, \"Home\")"))
        assertTrue(ask.contains("Icon(Icons.Default.Home, \"Home\")"))
        assertTrue(settings.contains("Icon(Icons.Default.Home, \"Home\")"))
        assertTrue(home.contains("home-tile-\${tile.id}"))
        assertFalse(create.contains("MainBottomNavigation("))
        assertFalse(gallery.contains("MainBottomNavigation("))
        assertFalse(ask.contains("MainBottomNavigation("))
        assertFalse(settings.contains("MainBottomNavigation("))
        assertFalse(settings.contains("settings-back\""))
        assertFalse(settings.contains("Icons.AutoMirrored.Filled.ArrowBack"))
    }

    @Test
    fun createIsTaskFirstAndKeepsStoreJobWiring() {
        val source = File("src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt").readText()
        val nav = File("src/main/java/com/nullxoid/android/ui/NullXoidNavHost.kt").readText()

        assertTrue(source.contains("Image Studio"))
        assertTrue(source.contains("Video Studio"))
        assertTrue(source.contains("3D Beta Studio"))
        assertTrue(source.contains("store-media-\${option.mediaKind}"))
        assertTrue(source.contains("Latest result"))
        assertTrue(source.contains("store-job-status-card"))
        assertTrue(source.contains("store-prompt"))
        assertTrue(source.contains("Create image"))
        assertTrue(source.contains("Create video"))
        assertTrue(source.contains("Create 3D model"))
        assertTrue(source.contains("Advanced mesh settings"))
        assertTrue(source.contains("Auto audio"))
        assertTrue(source.contains("Record voice"))
        assertTrue(source.contains("No audio"))
        assertFalse(source.contains("1. Choose"))
        assertFalse(source.contains("2. Configure"))
        assertFalse(source.contains("Text(\"Gallery\""))
        assertTrue(nav.contains("onRunStoreAddon = vm::runStoreAddon"))
        assertTrue(nav.contains("LaunchedEffect(state.auth.authenticated)"))
        assertTrue(nav.contains("if (state.auth.authenticated) vm.refreshStore()"))
        assertTrue(nav.contains("nav.navigate(Routes.Jobs)"))
    }

    @Test
    fun jobsScreenSupportsMonitoringAndCancellation() {
        val source = File("src/main/java/com/nullxoid/android/ui/store/JobsScreen.kt").readText()
        val viewModel = File("src/main/java/com/nullxoid/android/ui/NullXoidViewModel.kt").readText()
        val nav = File("src/main/java/com/nullxoid/android/ui/NullXoidNavHost.kt").readText()

        assertTrue(nav.contains("const val Jobs = \"jobs\""))
        assertTrue(source.contains("Text(\"Jobs\")"))
        assertTrue(source.contains("store-job-monitor-card"))
        assertTrue(source.contains("Cancel this job?"))
        assertTrue(source.contains("No, keep job"))
        assertTrue(source.contains("Yes, cancel job"))
        assertTrue(source.contains("Cancel job"))
        assertTrue(source.contains("Queue #"))
        assertTrue(viewModel.contains("storeJobs: List<StoreJobSummary>"))
        assertTrue(viewModel.contains("repo.storeJobs(activeOnly = activeOnly, limit = 50)"))
        assertTrue(viewModel.contains("repo.cancelStoreJob(storeJobId)"))
        assertFalse(source.contains("raw prompt"))
        assertFalse(source.contains("provider URL"))
        assertFalse(source.contains("workflow path"))
    }

    @Test
    fun askAndGalleryOwnTheirProductSurfaces() {
        val ask = File("src/main/java/com/nullxoid/android/ui/chat/ChatListScreen.kt").readText()
        val gallery = File("src/main/java/com/nullxoid/android/ui/store/GalleryScreen.kt").readText()
        val home = File("src/main/java/com/nullxoid/android/ui/home/HomeScreen.kt").readText()

        assertTrue(ask.contains("Ask EchoLabs"))
        assertTrue(ask.contains("Recent conversations"))
        assertTrue(ask.contains("chat-list-new-chat"))
        assertTrue(home.contains("NullXoid Assistant"))
        assertTrue(home.contains("Open full chat"))
        assertTrue(gallery.contains("Private media"))
        assertTrue(gallery.contains("Images"))
        assertTrue(gallery.contains("Videos"))
        assertTrue(gallery.contains("3D"))
        assertTrue(gallery.contains("StoreGalleryCard"))
    }

    @Test
    fun settingsKeepsProductFirstGroupsBeforeAdvanced() {
        val source = File("src/main/java/com/nullxoid/android/ui/settings/SettingsScreen.kt").readText()

        assertTrue(source.contains("Connection"))
        assertTrue(source.contains("Account"))
        assertTrue(source.contains("Security"))
        assertTrue(source.contains("App update"))
        assertTrue(source.contains("Advanced"))
        assertTrue(source.contains("Hosted API"))
        assertTrue(source.contains("Sign out"))
        assertTrue(source.indexOf("App update") < source.indexOf("Advanced"))
    }

    @Test
    fun topLevelScreensUseBottomAndImePadding() {
        listOf(
            "src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt",
            "src/main/java/com/nullxoid/android/ui/store/GalleryScreen.kt",
            "src/main/java/com/nullxoid/android/ui/chat/ChatListScreen.kt",
            "src/main/java/com/nullxoid/android/ui/settings/SettingsScreen.kt"
        ).forEach { path ->
            val source = File(path).readText()
            assertTrue("$path should apply navigationBarsPadding", source.contains("navigationBarsPadding()"))
            assertTrue("$path should apply imePadding", source.contains("imePadding()"))
        }
    }

    @Test
    fun homeSupportsVisibleAssistantAndSwipeUpEntry() {
        val home = File("src/main/java/com/nullxoid/android/ui/home/HomeScreen.kt").readText()

        assertTrue(home.contains("home-ask-nullxoid"))
        assertTrue(home.contains("detectVerticalDragGestures"))
        assertTrue(home.contains("ModalBottomSheet"))
        assertTrue(home.contains("BackHandler(enabled = assistantVisible)"))
        assertTrue(home.contains("Close assistant"))
        assertTrue(home.contains("Open full chat"))
        assertTrue(home.contains("ASSISTANT_SWIPE_THRESHOLD"))
    }

    @Test
    fun androidUiDoesNotExposeProviderOrPathMarkers() {
        val source = listOf(
            "src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt",
            "src/main/java/com/nullxoid/android/ui/store/GalleryScreen.kt",
            "src/main/java/com/nullxoid/android/ui/chat/ChatListScreen.kt",
            "src/main/java/com/nullxoid/android/ui/settings/SettingsScreen.kt",
            "src/main/java/com/nullxoid/android/ui/home/HomeScreen.kt"
        ).joinToString("\n") { File(it).readText() }

        listOf(
            "CREATIVE_PROVIDER",
            "CREATIVE_OUTPUT_DIR",
            "CREATIVE_WORKER_TOKEN",
            "NULLBRIDGE_SERVICE_TOKEN",
            "workflow.json",
            "provider output path",
            "private artifact path"
        ).forEach { marker ->
            assertFalse("Unexpected leak marker: $marker", source.contains(marker))
        }
    }

    @Test
    fun voiceRecordingUsesAndroidPermissionAndSafeArtifactUpload() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val api = File("src/main/java/com/nullxoid/android/data/api/NullXoidApi.kt").readText()
        val store = File("src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt").readText()
        val viewModel = File("src/main/java/com/nullxoid/android/ui/NullXoidViewModel.kt").readText()

        assertTrue(manifest.contains("android.permission.RECORD_AUDIO"))
        assertTrue(api.contains("/artifacts/upload"))
        assertTrue(store.contains("store-video-audio-\$mode"))
        assertTrue(store.contains("AudioRecord"))
        assertTrue(store.contains(".wav"))
        assertTrue(store.contains("WAV voice clip ready"))
        assertTrue(viewModel.contains("voice-note.wav"))
        assertTrue(viewModel.contains("audio/wav"))
        assertFalse(viewModel.contains("voice-note.m4a"))
        assertFalse(viewModel.contains("audio/mp4"))
        assertFalse(store.contains("workflow path"))
        assertFalse(store.contains("provider config"))
    }

    @Test
    fun videoViewerPlaysInsideAppWithNativeControls() {
        val store = File("src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt").readText()

        assertTrue(store.contains("MediaController"))
        assertTrue(store.contains("VideoView"))
        assertTrue(store.contains("store-video-player"))
        assertTrue(store.contains("Tap the video for playback controls."))
        assertTrue(store.contains("setMediaController"))
        assertTrue(store.contains("FileProvider.getUriForFile"))
        assertTrue(store.contains("setVideoURI"))
        assertTrue(store.contains("start()"))
        assertFalse(store.contains("setVideoPath(videoFile.absolutePath)"))
    }

    @Test
    fun videoGalleryCardsUseSafeFramePreviews() {
        val store = File("src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt").readText()
        val viewModel = File("src/main/java/com/nullxoid/android/ui/NullXoidViewModel.kt").readText()

        assertTrue(viewModel.contains("repo.storeBytesAtPath(previewPath)"))
        assertTrue(viewModel.contains("repo.storeArtifactBytes(artifactId)"))
        assertTrue(viewModel.contains("item.mimeType.startsWith(\"image/\") || item.mimeType.startsWith(\"video/\")"))
        assertTrue(store.contains("MediaMetadataRetriever"))
        assertTrue(store.contains("ByteArrayVideoDataSource"))
        assertTrue(store.contains("decodeVideoFramePreview"))
        assertTrue(store.contains("Preview loading"))
        assertFalse(store.contains("setDataSource(videoFile.absolutePath)"))
    }

    @Test
    fun model3dGalleryCardsUseRenderedPreviewAndDownloadSavePath() {
        val store = File("src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt").readText()
        val modelViewer = File("src/main/java/com/nullxoid/android/ui/store/Model3DViewer.kt").readText()
        val viewModel = File("src/main/java/com/nullxoid/android/ui/NullXoidViewModel.kt").readText()
        val uiModels = File("src/main/java/com/nullxoid/android/ui/store/StoreUiModels.kt").readText()
        val gradle = File("build.gradle.kts").readText()

        assertTrue(store.contains("3D preview not yet available. Save the GLB to open it in a model viewer."))
        assertTrue(store.contains("Rendered GLB preview. Save the GLB to open the model file."))
        assertTrue(store.contains("InteractiveGlbViewer"))
        assertTrue(modelViewer.contains("ModelViewer"))
        assertTrue(modelViewer.contains("loadModelGlb"))
        assertTrue(modelViewer.contains("transformToUnitCube"))
        assertTrue(modelViewer.contains("onTouchEvent"))
        assertTrue(modelViewer.contains("modelViewer.engine.lightManager"))
        assertTrue(modelViewer.contains("setDirection"))
        assertTrue(modelViewer.contains("store-model3d-viewer"))
        assertTrue(gradle.contains("com.google.android.filament:filament-android"))
        assertTrue(gradle.contains("com.google.android.filament:gltfio-android"))
        assertTrue(gradle.contains("com.google.android.filament:filament-utils-android"))
        assertTrue(store.contains("previewBytes = state.storePreviewBytes[artifact.artifactId]"))
        assertTrue(store.contains("Start with one clear image. Add side or back views when you have them. Mesh and wrapping are still experimental."))
        assertTrue(store.contains("Choose the front / main image first"))
        assertTrue(store.contains("File(context.filesDir, \"store_3d_source_images\")"))
        assertTrue(store.contains("source-image-${'$'}safeRole."))
        assertTrue(viewModel.contains("sourceImageArtifactId"))
        assertTrue(viewModel.contains("Choose a source image before generating a 3D model."))
        assertTrue(uiModels.contains("3D model"))
        assertTrue(uiModels.contains("3D ${'$'}{item.format.ifBlank { \"GLB\" }.uppercase()}"))
        assertTrue(viewModel.contains("mimeType.startsWith(\"model/\")"))
        assertTrue(viewModel.contains("MediaStore.Downloads.EXTERNAL_CONTENT_URI"))
        assertTrue(viewModel.contains("Environment.DIRECTORY_DOWNLOADS"))
        assertTrue(viewModel.contains("model/gltf-binary"))
        assertFalse(viewModel.contains("model/gltf-binary\" else \"image/png\""))
    }
}
