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

    @Test fun favoritesAndContinueWatchingRemainAccountScoped() = runBlocking {
        val account = AccountEntity("a", "Test", "http://host", "alias", "Active", null, 1)
        val movie = MovieEntity("a", "10", "Movie", "mp4", null, null, 1, null, null, null, null, null, null, null, null, null, 1)
        database.accountDao().upsert(account)
        database.catalogDao().upsertMovies(listOf(movie))
        database.personalDao().upsertFavorite(FavoriteEntity("a", "movie", "10", 1))
        database.personalDao().upsertProgress(PlaybackProgressEntity("a", "movie", "10", 20_000, 100_000, false, 1))

        assertEquals(listOf(movie), database.catalogDao().favoriteMovies("a").first())
        assertEquals(listOf(movie), database.catalogDao().continueMovies("a").first())
        assertTrue(database.catalogDao().favoriteMovies("other").first().isEmpty())
    }
}
