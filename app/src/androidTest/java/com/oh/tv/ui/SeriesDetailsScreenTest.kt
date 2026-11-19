package com.oh.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.oh.tv.data.local.SeriesEntity
import com.oh.tv.data.local.SeriesEpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SeriesDetailsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val series = SeriesEntity(
        accountId = "account", providerId = "show", name = "Series", providerIconUrl = null,
        providerRating = null, lastModified = null, tmdbId = null, overview = null, releaseDate = null,
        tmdbRating = null, tmdbVoteCount = null, genres = null, posterPath = null, backdropPath = null,
        metadataUpdatedAt = null, detailsLoaded = false, syncGeneration = 1,
    )
    private val episode = SeriesEpisodeEntity(
        accountId = "account", seriesId = "show", providerId = "pilot", seasonNumber = 1, episodeNumber = 1,
        title = "Pilot", containerExtension = null, overview = null, duration = null, posterUrl = null, syncGeneration = 1,
    )

    @Test fun cachedEpisodeAndDetailRetryRemainAvailableAfterPlaybackFails() {
        var state by mutableStateOf(OhUiState(seriesDetailsError = "Refresh failed"))
        var plays = 0
        compose.setContent {
            SeriesDetailsScreen(series, listOf(episode), emptyList(), state, play = {
                plays++
                state = state.copy(message = "Playback failed")
            }, toggleFavorite = {}, retry = { state = state.copy(seriesDetailsLoading = true) }, back = {})
        }
        compose.onNodeWithText("Pilot").performClick()
        compose.onNodeWithText("Playback failed").assertIsDisplayed()
        compose.onNodeWithText("Refresh failed").assertIsDisplayed()
        compose.onNodeWithText("Pilot").performClick()
        compose.runOnIdle { assertEquals(2, plays) }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Pilot").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(seriesDetailsLoading = false, seriesDetailsError = null) }
        compose.onNodeWithText("Refresh failed").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText("Playback failed").assertIsDisplayed()
    }

    @Test fun cachedEpisodeCanBeSelectedAgainAfterAPlaybackError() {
        var state by mutableStateOf(OhUiState())
        var attempts = 0
        compose.setContent {
            SeriesDetailsScreen(series, listOf(episode), emptyList(), state, play = {
                assertEquals(episode, it)
                attempts++
                state = state.copy(message = "Playback failed")
            }, toggleFavorite = {}, retry = {}, back = {})
        }
        compose.onNodeWithText("Pilot").performClick()
        compose.onNodeWithText("Playback failed").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText("Pilot").performClick()
        compose.runOnIdle { assertEquals(2, attempts) }
    }

    @Test fun initialFailureOffersRetryAndShowsLoading() {
        var state by mutableStateOf(OhUiState(seriesDetailsError = "Refresh failed"))
        compose.setContent {
            SeriesDetailsScreen(series, emptyList(), emptyList(), state, play = {}, toggleFavorite = {},
                retry = { state = state.copy(seriesDetailsLoading = true) }, back = {})
        }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Loading episodes").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }
}
