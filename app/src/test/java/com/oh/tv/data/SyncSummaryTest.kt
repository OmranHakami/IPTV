package com.oh.tv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSummaryTest {
    @Test fun failedScopesDoNotMakeLibraryUsable() {
        assertFalse(
            SyncSummary(listOf(SyncProgress("Live TV", state = SyncState.Failed, failure = "network"))).usable,
        )
    }

    @Test fun aCompletedContentScopeMakesLibraryUsable() {
        assertTrue(SyncSummary(listOf(SyncProgress("Movies", 42, SyncState.Ready))).usable)
    }

    @Test fun retainedCacheKeepsLibraryUsableAfterRefreshFailure() {
        assertTrue(
            SyncSummary(listOf(SyncProgress("TV Shows", 250, SyncState.Stale, failure = "timeout"))).usable,
        )
    }

    @Test fun exposesExactlyThreeUserFacingScopes() {
        assertTrue(SyncScopes.userVisible == listOf("Movies", "TV Shows", "Live TV"))
    }
}
