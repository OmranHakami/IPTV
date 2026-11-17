package com.oh.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.oh.tv.data.SyncProgress
import com.oh.tv.data.SyncScopes
import com.oh.tv.data.SyncState
import org.junit.Rule
import org.junit.Test

class LibraryPreparationScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun runningMoviesDescribeArtworkIndexing() {
        composeRule.setContent {
            LibraryPreparationScreen(
                state = OhUiState(
                    busy = true,
                    syncProgress = listOf(SyncProgress(SyncScopes.MOVIES, 19_500, SyncState.Running)),
                ),
                onOpenReady = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("19,500 items · indexing artwork").assertIsDisplayed()
        composeRule.onNodeWithText("Organizing your library…").assertIsDisplayed()
    }
}
