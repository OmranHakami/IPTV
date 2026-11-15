package com.oh.tv.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingConnectionLimiterTest {
    @Test fun rejectsClientsWhenAllSlotsAreOccupied() {
        val limiter = PairingConnectionLimiter(2)
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
    }

    @Test fun releasedSlotAllowsAnotherClient() {
        val limiter = PairingConnectionLimiter(1)
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
        limiter.release()
        assertTrue(limiter.tryAcquire())
    }
}
