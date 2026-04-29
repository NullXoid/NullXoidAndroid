package com.nullxoid.android.ui.health

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthUxTest {
    @Test
    fun displayValueConvertsBooleansToReadableLabels() {
        assertEquals("yes", displayValue(JsonPrimitive(true)))
        assertEquals("no", displayValue(JsonPrimitive(false)))
    }

    @Test
    fun displayValueKeepsStringsWithoutJsonQuotes() {
        assertEquals("local_runtime", displayValue(JsonPrimitive("local_runtime")))
    }
}
