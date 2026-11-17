package com.oh.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class PhonePairingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun waitingStateOffersStatusRefresh() {
        composeRule.setContent {
            PhonePairingScreen(
                state = PairingUiState(
                    phase = PairingPhase.Waiting,
                    code = "M7K-4P2",
                    linkUrl = "http://chroma.tv/link",
                    expiresAtEpochMs = System.currentTimeMillis() + 600_000,
                ),
                onRetry = {},
                onManual = {},
            )
        }

        composeRule.onNodeWithText("Refresh status").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for your phone").assertIsDisplayed()
    }
}
