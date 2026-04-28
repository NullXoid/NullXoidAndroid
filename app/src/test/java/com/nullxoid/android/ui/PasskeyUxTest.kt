package com.nullxoid.android.ui

import androidx.credentials.exceptions.NoCredentialException
import com.nullxoid.android.data.api.ApiException
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
            "This account has 1 passkey(s). This phone can sign in only after Android saves one for NullXoid.",
            passkeyEnrollmentStatusText(
                authenticated = true,
                provider = provider,
                credentialCount = 1
            )
        )
    }

    @Test
    fun unregisteredCredentialErrorExplainsStaleDevicePasskey() {
        val message = mobilePasskeySignInError(
            ApiException(
                401,
                "{\"detail\":{\"code\":\"passkey_credential_not_registered\"}}"
            )
        )

        assertTrue(message.contains("not registered"))
        assertTrue(message.contains("remove the stale NullXoid passkey"))
    }
}
