package com.oh.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNumberingTest {
    @Test fun firstBatchUsesOriginalRowPositions() {
        assertEquals(1, fallbackChannelNumber(0, 0))
        assertEquals(3, fallbackChannelNumber(0, 2))
    }

    @Test fun nextBatchStartsAfterAllPreviousRawRowsIncludingSkippedOnes() {
        // First raw batch has 3 rows, but only 1 had a valid stream_id.
        // The next batch must start at 4, not at the mapped-row count + 1 (2).
        assertEquals(4, fallbackChannelNumber(3, 0))
        assertEquals(6, fallbackChannelNumber(3, 2))
    }
}
