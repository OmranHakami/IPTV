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

    @Test fun acceptsMetadataForCurrentUnenrichedMovie() {
        assertTrue(shouldApplyMovieMetadata(movie(), 42L))
    }

    @Test fun rejectsMetadataForMovieWhoseTmdbIdChangedDuringRefresh() {
        assertFalse(shouldApplyMovieMetadata(movie(tmdbId = 43L), 42L))
    }

    @Test fun rejectsLateMetadataWhenCurrentRowAlreadyEnriched() {
        assertFalse(shouldApplyMovieMetadata(movie(metadataUpdatedAt = 100L), 42L))
    }

    @Test fun rejectsMetadataWhenProviderRemovedTmdbId() {
        assertFalse(shouldApplyMovieMetadata(movie(tmdbId = null), 42L))
    }
}
