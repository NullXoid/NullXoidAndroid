package com.nullxoid.android.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    @Test
    fun challengeMatchesRfc7636Example() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        )
    }

    @Test
    fun generatedPairUsesUrlSafeUnpaddedValues() {
        val pair = Pkce.generatePair()

        assertEquals(Pkce.CHALLENGE_METHOD, pair.method)
        assertTrue(pair.verifier.length >= 43)
        assertTrue(pair.challenge.length >= 43)
        assertFalse(pair.verifier.contains("="))
        assertFalse(pair.challenge.contains("="))
    }
}
