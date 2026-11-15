package com.oh.tv.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OhDatabaseTest {
    private lateinit var database: OhDatabase

    @Before fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OhDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun closeDatabase() = database.close()

    @Test fun tmdbMetadataDoesNotReplaceOriginalIptvFields() = runBlocking {
        val account = AccountEntity("a", "Test", "http://host", "alias", "Active", null, 1)
        database.accountDao().upsert(account)
        val movie = MovieEntity(
            accountId = "a", providerId = "10", name = "IPTV Original",
            containerExtension = "mp4", providerIconUrl = null, providerRating = 6.0,
            addedAt = 1, tmdbId = 100, overview = "IPTV synopsis", releaseDate = "2020-01-01",
            runtimeMinutes = null, tmdbRating = 8.0, tmdbVoteCount = null,
            genres = null, posterPath = null, backdropPath = null,
            metadataUpdatedAt = 2, tmdbTitle = "TMDB Title",
            tmdbOverview = "TMDB synopsis", tmdbReleaseDate = "2021-01-01",
            syncGeneration = 1,
        )
        database.catalogDao().upsertMovies(listOf(movie))
        val saved = database.catalogDao().moviesByIds("a", listOf("10")).single()
        assertEquals("IPTV Original", saved.name)
        assertEquals("IPTV synopsis", saved.overview)
        assertEquals("2020-01-01", saved.releaseDate)
        assertEquals("TMDB Title", saved.displayName)
        assertEquals("TMDB synopsis", saved.displayOverview)
        assertEquals("2021-01-01", saved.displayReleaseDate)
    }

    @Test fun favoritesAndContinueWatchingRemainAccountScoped() = runBlocking {
        val account = AccountEntity("a", "Test", "http://host", "alias", "Active", null, 1)
        val movie = MovieEntity(accountId = "a", providerId = "10", name = "Movie", containerExtension = "mp4", providerIconUrl = null, providerRating = null, addedAt = 1, tmdbId = null, overview = null, releaseDate = null, runtimeMinutes = null, tmdbRating = null, tmdbVoteCount = null, genres = null, posterPath = null, backdropPath = null, metadataUpdatedAt = null, syncGeneration = 1)
        database.accountDao().upsert(account)
        database.catalogDao().upsertMovies(listOf(movie))
        database.personalDao().upsertFavorite(FavoriteEntity("a", "movie", "10", 1))
        database.personalDao().upsertProgress(PlaybackProgressEntity("a", "movie", "10", 20_000, 100_000, false, 1))

        assertEquals(listOf(movie), database.catalogDao().favoriteMovies("a").first())
        assertEquals(listOf(movie), database.catalogDao().continueMovies("a").first())
        assertTrue(database.catalogDao().favoriteMovies("other").first().isEmpty())
    }
}
