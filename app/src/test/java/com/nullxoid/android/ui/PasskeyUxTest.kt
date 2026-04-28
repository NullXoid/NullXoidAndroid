package com.nullxoid.android.ui

import androidx.credentials.exceptions.NoCredentialException
import com.nullxoid.android.data.model.PasskeyProviderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyUxTest {
    @Test
    fun noCredentialPasskeyErrorExplainsFirstTimeMobileSetup() {
        val message = mobilePasskeySignInError(NoCredentialException())

        assertTrue(message.contains("Sign in with password once"))
        assertTrue(message.contains("Add passkey"))
    }

    @Test
    fun enrollmentStatusExplainsUnauthenticatedSetup() {
        assertEquals(
            "Sign in with password once to add a passkey on this phone.",
            passkeyEnrollmentStatusText(
                authenticated = false,
                provider = null,
                credentialCount = 0
            )
        )
    }

    @Test
    fun enrollmentStatusExplainsExistingPhonePasskey() {
        val provider = PasskeyProviderStatus(
            configured = true,
            registrationEnabled = true,
            rpId = "echolabs.diy",
            rpName = "NullXoid"
        )

        assertEquals(
            "This phone has 1 passkey(s). You can sign in with passkey next time.",
            passkeyEnrollmentStatusText(
                authenticated = true,
                provider = provider,
                credentialCount = 1
            )
        )
    }
}
