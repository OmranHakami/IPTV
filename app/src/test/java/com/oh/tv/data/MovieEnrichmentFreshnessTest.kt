package com.oh.tv.data

import com.oh.tv.data.local.MovieEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieEnrichmentFreshnessTest {
    private fun movie(tmdbId: Long? = 42L, metadataUpdatedAt: Long? = null) = MovieEntity(
        accountId = "account", providerId = "movie", name = "Provider title",
        containerExtension = "mp4", providerIconUrl = null, providerRating = null,
        addedAt = null, tmdbId = tmdbId, overview = null, releaseDate = null,
        runtimeMinutes = null, tmdbRating = null, tmdbVoteCount = null,
        genres = null, posterPath = null, backdropPath = null,
        metadataUpdatedAt = metadataUpdatedAt, syncGeneration = 200L,
    )

    @Test fun cacheFieldsDoNotChangeProviderIdentity() {
        assertTrue(movie(metadataUpdatedAt = 100L).hasSameMatchInputs(movie()))
    }

    @Test fun rejectsMetadataForMovieWhoseTmdbIdChangedDuringRefresh() {
        assertFalse(movie(tmdbId = 43L).hasSameMatchInputs(movie()))
    }

    @Test fun rejectsLateMetadataWhenTitleOrYearChanges() {
        assertFalse(movie().copy(name = "Other movie").hasSameMatchInputs(movie()))
        assertFalse(movie().copy(releaseDate = "2024").hasSameMatchInputs(movie()))
    }

    @Test fun rejectsMetadataWhenProviderRemovedTmdbId() {
        assertFalse(movie(tmdbId = null).hasSameMatchInputs(movie()))
    }
}
