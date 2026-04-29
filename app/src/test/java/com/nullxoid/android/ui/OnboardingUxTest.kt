package com.nullxoid.android.ui

import com.nullxoid.android.data.prefs.SettingsStore
import com.nullxoid.android.ui.onboarding.onboardingAccountStatus
import com.nullxoid.android.ui.onboarding.onboardingUpdateStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUxTest {
    @Test
    fun firstRunAccountStepExplainsPasswordBeforePasskey() {
        val message = onboardingAccountStatus(authenticated = false, passkeyCount = 0)

        assertTrue(message.contains("Password sign-in comes first"))
        assertTrue(message.contains("save a NullXoid passkey"))
    }

    @Test
    fun authenticatedAccountStepExplainsPasskeyRegistration() {
        val message = onboardingAccountStatus(authenticated = true, passkeyCount = 0)

        assertTrue(message.contains("Signed in"))
        assertTrue(message.contains("Add a passkey"))
    }

    @Test
    fun updateStepExplainsAutoMirrorOrder() {
        val message = onboardingUpdateStatus(SettingsStore.UPDATE_SOURCE_AUTO)

        assertTrue(message.contains("Forgejo first"))
        assertTrue(message.contains("GitHub"))
    }
}
