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
    fun signedInDefaultRouteIsCreate() {
        val source = File("src/main/java/com/nullxoid/android/ui/NullXoidNavHost.kt").readText()

        assertFalse(source.contains("const val Home"))
        assertFalse(source.contains("Routes.Home"))
        assertTrue(source.contains("if (state.onboardingCompleted) Routes.Store else Routes.Onboarding"))
        assertTrue(source.contains("if (state.auth.authenticated) Routes.Store else Routes.Login"))
    }

    @Test
    fun topLevelScreensDoNotExposeBackArrows() {
        val create = File("src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt").readText()
        val gallery = File("src/main/java/com/nullxoid/android/ui/store/GalleryScreen.kt").readText()
        val ask = File("src/main/java/com/nullxoid/android/ui/chat/ChatListScreen.kt").readText()
        val settings = File("src/main/java/com/nullxoid/android/ui/settings/SettingsScreen.kt").readText()

        assertFalse(create.contains("navigationIcon"))
        assertFalse(gallery.contains("navigationIcon"))
        assertFalse(ask.contains("navigationIcon"))
        assertFalse(settings.contains("settings-back\""))
        assertFalse(settings.contains("Icons.AutoMirrored.Filled.ArrowBack"))
    }

    @Test
    fun createIsTaskFirstAndKeepsStoreJobWiring() {
        val source = File("src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt").readText()
        val nav = File("src/main/java/com/nullxoid/android/ui/NullXoidNavHost.kt").readText()

        assertTrue(source.contains("Private media through approval-gated workflows."))
        assertTrue(source.contains("Latest result"))
        assertTrue(source.contains("store-job-status-card"))
        assertTrue(source.contains("store-view-jobs"))
        assertTrue(source.contains("store-prompt"))
        assertTrue(source.contains("Generate with approval"))
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

        assertTrue(ask.contains("Ask EchoLabs"))
        assertTrue(ask.contains("Recent conversations"))
        assertTrue(ask.contains("selected = MainTab.Ask"))
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
    fun topLevelScreensSupportLeftRightSwipeNavigation() {
        val bottomNav = File("src/main/java/com/nullxoid/android/ui/MainBottomNavigation.kt").readText()

        assertTrue(bottomNav.contains("mainTabSwipeNavigation"))
        assertTrue(bottomNav.contains("detectHorizontalDragGestures"))
        assertTrue(bottomNav.contains("MainTab.entries"))
        assertTrue(bottomNav.contains("MainTabSwipeState"))
        assertTrue(bottomNav.contains("lastNavigationAtMs"))

        listOf(
            "src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt",
            "src/main/java/com/nullxoid/android/ui/store/GalleryScreen.kt",
            "src/main/java/com/nullxoid/android/ui/chat/ChatListScreen.kt",
            "src/main/java/com/nullxoid/android/ui/settings/SettingsScreen.kt"
        ).forEach { path ->
            val source = File(path).readText()
            assertTrue("$path should apply swipe navigation", source.contains(".mainTabSwipeNavigation("))
        }
    }

    @Test
    fun androidUiDoesNotExposeProviderOrPathMarkers() {
        val source = listOf(
            "src/main/java/com/nullxoid/android/ui/store/StoreScreen.kt",
            "src/main/java/com/nullxoid/android/ui/store/GalleryScreen.kt",
            "src/main/java/com/nullxoid/android/ui/chat/ChatListScreen.kt",
            "src/main/java/com/nullxoid/android/ui/settings/SettingsScreen.kt",
            "src/main/java/com/nullxoid/android/ui/MainBottomNavigation.kt"
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
        val viewModel = File("src/main/java/com/nullxoid/android/ui/NullXoidViewModel.kt").readText()
        val uiModels = File("src/main/java/com/nullxoid/android/ui/store/StoreUiModels.kt").readText()

        assertTrue(store.contains("3D preview not yet available. Save the GLB to open it in a model viewer."))
        assertTrue(store.contains("Rendered GLB preview. Save the GLB to open the model file."))
        assertTrue(store.contains("previewBytes = state.storePreviewBytes[artifact.artifactId]"))
        assertTrue(store.contains("3D model generation uses an image first."))
        assertTrue(store.contains("Choose image first"))
        assertTrue(store.contains("File(context.filesDir, \"store_3d_source_images\")"))
        assertTrue(store.contains("source-image-${'$'}{System.currentTimeMillis()}"))
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
