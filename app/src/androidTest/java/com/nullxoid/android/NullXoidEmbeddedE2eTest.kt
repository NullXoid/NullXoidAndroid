package com.nullxoid.android

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NullXoidEmbeddedE2eTest {

    @get:Rule
    val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun embeddedEchoBackendCanLoginAndStreamChat() {
        if (waitForTag("onboarding-finish-top", timeoutMillis = 5_000)) {
            compose.onNodeWithTag("onboarding-finish-top").performClick()
        }

        assertTagAppears("login-backend-button")

        compose.onNodeWithTag("login-backend-button").performClick()
        assertTagAppears("settings-embedded-switch")
        compose.onNodeWithTag("settings-embedded-switch").performScrollTo()
        compose.onNodeWithText("Echo").performScrollTo().performClick()
        val embeddedAlreadyRunning = compose.onAllNodes(
            hasTestTag("settings-embedded-status") and
                hasText("Running on 127.0.0.1:8090", substring = true)
        ).fetchSemanticsNodes().isNotEmpty()
        if (!embeddedAlreadyRunning) {
            compose.onNodeWithTag("settings-embedded-switch").performClick()
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodes(
                hasTestTag("settings-embedded-status") and
                    hasText("Running on 127.0.0.1:8090", substring = true)
            )
                .fetchSemanticsNodes().isNotEmpty()
        }
        pressBack()

        if (!openChatListIfAvailable()) {
            if (waitForTag("onboarding-finish-top", timeoutMillis = 2_000)) {
                compose.onNodeWithTag("onboarding-finish-top").performClick()
            }
        }

        if (!openChatListIfAvailable()) {
            assertTagAppears("login-username")
            compose.onNodeWithTag("login-username").performTextInput("e2e")
            compose.onNodeWithTag("login-password").performTextInput("e2e")
            compose.onNodeWithTag("login-submit").performClick()
            if (!openChatListIfAvailable(timeoutMillis = 10_000)) {
                assertTagAppears("chat-list-new-chat")
            }
        }

        compose.onNodeWithTag("chat-list-new-chat").performClick()
        compose.onNodeWithText("Start the conversation.").assertIsDisplayed()
        if (waitForTag("chat-refresh-models", timeoutMillis = 1_000)) {
            compose.onNodeWithTag("chat-refresh-models").performClick()
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText("echo:nullxoid-local", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("chat-message-input").performTextReplacement("hello from e2e")
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("chat-send") and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("chat-send").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(
                hasTestTag("chat-message-content-user") and
                    hasText("hello from e2e", substring = true),
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodes(
                hasTestTag("chat-message-content-assistant") and
                    hasText("You said: hello from e2e", substring = true),
                useUnmergedTree = true
            )
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long): Boolean =
        runCatching {
            compose.waitUntil(timeoutMillis = timeoutMillis) {
                compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess

    private fun assertTagAppears(tag: String, timeoutMillis: Long = 10_000) {
        compose.waitUntil(timeoutMillis = timeoutMillis) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openChatListIfAvailable(timeoutMillis: Long = 5_000): Boolean {
        if (waitForTag("chat-list-new-chat", timeoutMillis = timeoutMillis)) return true
        if (waitForTag("bottom-nav-ask", timeoutMillis = 1_000)) {
            compose.onNodeWithTag("bottom-nav-ask").performClick()
            return waitForTag("chat-list-new-chat", timeoutMillis = timeoutMillis)
        }
        return false
    }
}
