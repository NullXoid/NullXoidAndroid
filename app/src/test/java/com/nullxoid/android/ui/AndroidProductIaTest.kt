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
        assertFalse(source.contains("1. Choose"))
        assertFalse(source.contains("2. Configure"))
        assertFalse(source.contains("Text(\"Gallery\""))
        assertTrue(nav.contains("onRunStoreAddon = vm::runStoreAddon"))
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
}
