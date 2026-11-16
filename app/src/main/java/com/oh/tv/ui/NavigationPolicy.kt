package com.oh.tv.ui

object NavigationPolicy {
    fun back(destination: Destination, settingsReturn: Destination, playerReturn: Destination, browseReturn: Destination = Destination.Movies): Destination = when (destination) {
        Destination.Settings -> settingsReturn
        Destination.Player -> playerReturn
        Destination.MovieDetails -> Destination.Movies
        Destination.SeriesDetails -> Destination.TvShows
        Destination.Search, Destination.MyList -> browseReturn
        Destination.Movies, Destination.TvShows, Destination.LiveTv, Destination.OnDemand -> Destination.Hub
        Destination.ManualSetup -> Destination.PhonePairing
        else -> Destination.Hub
    }
}
