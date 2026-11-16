package com.oh.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {
    @Test fun setupFlowHasNoLegacyOnboardingDestination() {
        val destinations = Destination.entries.map { it.name }
        org.junit.Assert.assertFalse("Legacy setup choice must not be reachable", destinations.contains("Onboarding"))
        org.junit.Assert.assertTrue(destinations.contains("PhonePairing"))
        org.junit.Assert.assertTrue(destinations.contains("ManualSetup"))
    }

    @Test fun manualSetupBackReturnsDirectlyToPhonePairing() {
        val destination = NavigationPolicy.back(Destination.ManualSetup, Destination.Hub, Destination.Hub)
        assertEquals(Destination.PhonePairing, destination)
    }

    @Test fun playerReturnsToExplicitOrigin() {
        assertEquals(Destination.LiveTv, NavigationPolicy.back(Destination.Player, Destination.Hub, Destination.LiveTv))
        assertEquals(Destination.MovieDetails, NavigationPolicy.back(Destination.Player, Destination.Hub, Destination.MovieDetails))
        assertEquals(Destination.SeriesDetails, NavigationPolicy.back(Destination.Player, Destination.Hub, Destination.SeriesDetails))
    }

    @Test fun settingsReturnsToOpeningArea() {
        assertEquals(Destination.OnDemand, NavigationPolicy.back(Destination.Settings, Destination.OnDemand, Destination.Hub))
    }

    @Test fun manualSetupReturnsToPhonePairing() {
        assertEquals(Destination.PhonePairing, NavigationPolicy.back(Destination.ManualSetup, Destination.Hub, Destination.Hub))
    }

    @Test fun searchAndMyListReturnToTheirCaller() {
        for (origin in listOf(Destination.LiveTv, Destination.TvShows, Destination.Movies, Destination.OnDemand)) {
            assertEquals(origin, NavigationPolicy.back(Destination.Search, Destination.Hub, Destination.Hub, origin))
            assertEquals(origin, NavigationPolicy.back(Destination.MyList, Destination.Hub, Destination.Hub, origin))
        }
    }

    @Test fun browseScreensReturnThroughTheNewMediaHome() {
        assertEquals(Destination.Hub, NavigationPolicy.back(Destination.Movies, Destination.Hub, Destination.Hub))
        assertEquals(Destination.Movies, NavigationPolicy.back(Destination.Search, Destination.Hub, Destination.Hub))
    }
}
