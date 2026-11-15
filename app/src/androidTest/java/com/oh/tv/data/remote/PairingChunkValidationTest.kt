package com.oh.tv.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingChunkValidationTest {
    private val validChunk = "a".repeat(300)

    @Test fun acceptsValidStringChunks() {
        assertEquals(listOf(validChunk, validChunk), validatedPairingChunks(JSONArray().put(validChunk).put(validChunk)))
    }

    @Test fun rejectsNonStringElementsWithoutThrowing() {
        val invalid = listOf<Any>(JSONObject.NULL, 12, true, JSONObject(), JSONArray())
        invalid.forEach { element ->
            assertNull("Unexpected acceptance of $element", validatedPairingChunks(JSONArray().put(validChunk).put(element)))
        }
    }

    @Test fun rejectsInvalidLengthsAndCounts() {
        assertNull(validatedPairingChunks(JSONArray()))
        assertNull(validatedPairingChunks(JSONArray().put("a".repeat(299))))
        assertNull(validatedPairingChunks(JSONArray().put("a".repeat(401))))
        assertNull(validatedPairingChunks(JSONArray().apply { repeat(17) { put(validChunk) } }))
    }
}
