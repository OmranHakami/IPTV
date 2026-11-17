package com.oh.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class AppLoadingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun loadingContentIsVisibleAndProgressMoves() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { AppLoadingScreen() }

        composeRule.onNodeWithText("Opening your library").assertIsDisplayed()
        composeRule.onNodeWithText("Loading your profile…").assertIsDisplayed()
        val sweep = composeRule.onNodeWithTag("loading-progress-sweep")
        val before = sweep.fetchSemanticsNode().boundsInRoot.left
        composeRule.mainClock.advanceTimeBy(600)
        val after = sweep.fetchSemanticsNode().boundsInRoot.left
        assertNotEquals(before, after)
    }
}
