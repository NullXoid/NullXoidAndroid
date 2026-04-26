package com.nullxoid.android

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        compose.onNodeWithTag("login-backend-button").assertIsDisplayed()

        compose.onNodeWithTag("login-backend-button").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithTag("settings-embedded-switch").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText("http://127.0.0.1:8090", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("settings-back").performClick()

        compose.onNodeWithTag("login-username").performTextInput("e2e")
        compose.onNodeWithTag("login-password").performTextInput("e2e")
        compose.onNodeWithTag("login-submit").performClick()

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasText("No chats yet", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("chat-list-new-chat").performClick()
        compose.onNodeWithText("Start the conversation.").assertIsDisplayed()

        compose.onNodeWithTag("chat-message-input").performTextInput("hello from e2e")
        compose.onNodeWithTag("chat-send").performClick()

        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(hasText("You said: hello from e2e", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
