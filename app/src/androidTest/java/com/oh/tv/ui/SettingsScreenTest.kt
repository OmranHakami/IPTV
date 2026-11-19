package com.oh.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.oh.tv.data.CatalogCounts
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun onlyWorkingSectionsRemainAndAccountRemovalRequiresConfirmation() {
        composeRule.setContent {
            SettingsScreen(OhUiState(), CatalogCounts(486, 1_248, 312), {}, {}, {})
        }

        composeRule.onNodeWithText("Playback").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-library").performClick()
        composeRule.onNodeWithText("Refresh library").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-subtitles").performClick()
        composeRule.onNodeWithText("OPENSUBTITLES API KEY").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-api-key").performClick()
        composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)
            .assertIsFocused()
            .performTextInput("test-key")
        composeRule.onNodeWithText("Enter a new API key").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-account").performClick()
        composeRule.onNodeWithTag("remove-account-button").performClick()
        composeRule.onNodeWithTag("remove-account-confirmation").assertIsDisplayed()
    }
}
