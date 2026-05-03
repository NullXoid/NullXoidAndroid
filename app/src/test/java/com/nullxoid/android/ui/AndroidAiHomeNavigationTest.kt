package com.nullxoid.android.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAiHomeNavigationTest {
    @Test
    fun bottomNavigationUsesAiWorkspaceLabels() {
        val source = File("src/main/java/com/nullxoid/android/ui/MainBottomNavigation.kt").readText()

        assertTrue(source.contains("Home"))
        assertTrue(source.contains("Create"))
        assertTrue(source.contains("Gallery"))
        assertTrue(source.contains("Settings"))
        assertFalse(source.contains("label = { Text(\"Chats\") }"))
        assertFalse(source.contains("label = { Text(\"Store\") }"))
    }

    @Test
    fun signedInDefaultRouteIsHome() {
        val source = File("src/main/java/com/nullxoid/android/ui/NullXoidNavHost.kt").readText()

        assertTrue(source.contains("const val Home = \"home\""))
        assertTrue(source.contains("if (state.onboardingCompleted) Routes.Home else Routes.Onboarding"))
        assertTrue(source.contains("if (state.auth.authenticated) Routes.Home else Routes.Login"))
    }

    @Test
    fun homeCardsRouteToCreateAskAndGallery() {
        val source = File("src/main/java/com/nullxoid/android/ui/home/HomeScreen.kt").readText()
        val navHost = File("src/main/java/com/nullxoid/android/ui/NullXoidNavHost.kt").readText()

        assertTrue(source.contains("Create image"))
        assertTrue(source.contains("Create video"))
        assertTrue(source.contains("Create 3D model"))
        assertTrue(source.contains("Ask EchoLabs"))
        assertTrue(source.contains("Open gallery"))
        assertTrue(navHost.contains("openCreate(\"local-image-studio\")"))
        assertTrue(navHost.contains("openCreate(\"local-video-studio\")"))
        assertTrue(navHost.contains("openCreate(\"local-3d-studio\")"))
        assertTrue(navHost.contains("onAskEchoLabs = { nav.navigate(Routes.ChatList) }"))
    }

    @Test
    fun settingsHasFamilyDemoFirstGlanceGroups() {
        val source = File("src/main/java/com/nullxoid/android/ui/settings/SettingsScreen.kt").readText()

        assertTrue(source.contains("Connection"))
        assertTrue(source.contains("Account"))
        assertTrue(source.contains("Security"))
        assertTrue(source.contains("App update"))
        assertTrue(source.contains("Advanced"))
        assertTrue(source.contains("Sign out"))
    }
}
