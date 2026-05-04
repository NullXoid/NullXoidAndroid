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

        assertTrue(viewModel.contains("item.mimeType.startsWith(\"video/\") -> repo.storeArtifactBytes(artifactId)"))
        assertTrue(store.contains("MediaMetadataRetriever"))
        assertTrue(store.contains("ByteArrayVideoDataSource"))
        assertTrue(store.contains("decodeVideoFramePreview"))
        assertTrue(store.contains("Preview loading"))
        assertFalse(store.contains("setDataSource(videoFile.absolutePath)"))
    }
}
