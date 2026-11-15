package com.oh.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPolicyTest {
    @Test fun marksNinetyPercentAsComplete() {
        assertFalse(PlaybackPolicy.isCompleted(89_999, 100_000))
        assertTrue(PlaybackPolicy.isCompleted(90_000, 100_000))
    }

    @Test fun resumesMeaningfulIncompleteProgressOnly() {
        assertEquals(0L, PlaybackPolicy.resumePosition(2_000, 100_000, false))
        assertEquals(40_000, PlaybackPolicy.resumePosition(40_000, 100_000, false))
        assertEquals(0L, PlaybackPolicy.resumePosition(95_000, 100_000, true))
    }
}
