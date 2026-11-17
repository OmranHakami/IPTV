package com.oh.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.oh.tv.data.CatalogCounts
import org.junit.Rule
import org.junit.Test

class ExperienceChoiceScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun emptyExperienceIsUnavailable() {
        composeRule.setContent {
            ExperienceChoiceScreen(
                counts = CatalogCounts(liveChannels = 12, movies = 0, series = 4),
                onMovies = {},
                onShows = {},
                onLive = {},
                onReload = {},
                onSettings = {},
            )
        }

        composeRule.onNodeWithText("UNAVAILABLE").assertIsDisplayed()
        composeRule.onNodeWithText("Not available").assertIsDisplayed()
    }
}
