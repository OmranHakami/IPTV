package com.oh.tv.ui

object NavigationPolicy {
    fun back(destination: Destination, settingsReturn: Destination, playerReturn: Destination): Destination = when (destination) {
        Destination.Settings -> settingsReturn
        Destination.Player -> playerReturn
        Destination.MovieDetails -> Destination.Movies
        Destination.SeriesDetails -> Destination.TvShows
        Destination.Search, Destination.MyList -> Destination.Movies
        Destination.Movies, Destination.TvShows, Destination.LiveTv, Destination.OnDemand -> Destination.Hub
        Destination.PhonePairing, Destination.ManualSetup -> Destination.Onboarding
        else -> Destination.Hub
    }
}
