package com.oh.tv.ui

import com.oh.tv.data.local.PlaybackProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistFormattingTest {
    @Test fun remainingTimeUsesWholeMinutesAndNeverGoesNegative() {
        assertEquals("38m left", watchlistRemainingLabel(progress(62_000, 2_342_000)))
        assertEquals("Resume", watchlistRemainingLabel(progress(2_400_000, 2_342_000)))
    }

    private fun progress(positionMs: Long, durationMs: Long) = PlaybackProgressEntity(
        accountId = "account",
        contentType = "movie",
        contentId = "movie",
        positionMs = positionMs,
        durationMs = durationMs,
        completed = false,
        updatedAt = 1,
    )
}
