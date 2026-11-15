package com.oh.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {
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

    @Test fun browseScreensReturnThroughTheNewMediaHome() {
        assertEquals(Destination.Hub, NavigationPolicy.back(Destination.Movies, Destination.Hub, Destination.Hub))
        assertEquals(Destination.Movies, NavigationPolicy.back(Destination.Search, Destination.Hub, Destination.Hub))
    }
}
