package com.oh.tv.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TolerantParsingTest {
    @Test fun acceptsStringAndNumberIdentifiers() {
        assertEquals("42", 42.asString())
        assertEquals(42L, "42".asLong())
        assertNull("not-a-number".asLong())
    }

    @Test fun acceptsLegacyBooleanShapes() {
        assertTrue("1".asBoolean() == true)
        assertFalse(0.asBoolean() == true)
        assertNull("unknown".asBoolean())
    }
}

