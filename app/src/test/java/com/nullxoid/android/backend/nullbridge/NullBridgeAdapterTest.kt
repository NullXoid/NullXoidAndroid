package com.nullxoid.android.backend.nullbridge

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NullBridgeAdapterTest {
    @Test
    fun statusDoesNotExposeCredentials() {
        val adapter = NullBridgeAdapter()

        val status = adapter.status()
        val serialized = status.toString()

        assertEquals("android_backend", status["backend_id"]?.jsonPrimitive?.content)
        assertFalse(status["credentials_exposed"]?.jsonPrimitive?.boolean ?: true)
        assertFalse(serialized.contains("Bearer"))
        assertFalse(serialized.contains("service-token"))
    }

    @Test
    fun unsupportedRouteDeniesWithoutExecution() {
        val adapter = NullBridgeAdapter()

        val denial = adapter.denyUnsupported("suite.unsupported.route")

        assertFalse(denial["ok"]?.jsonPrimitive?.boolean ?: true)
        assertFalse(denial["accepted"]?.jsonPrimitive?.boolean ?: true)
        assertFalse(denial["executed"]?.jsonPrimitive?.boolean ?: true)
        assertEquals("suite.unsupported.route", denial["capability"]?.jsonPrimitive?.content)
        assertEquals("CAPABILITY_NOT_SUPPORTED", denial["errorCode"]?.jsonPrimitive?.content)
        assertFalse(denial.toString().contains("Bearer"))
    }
}
