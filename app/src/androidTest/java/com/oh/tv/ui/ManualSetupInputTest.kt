package com.oh.tv.ui

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class ManualSetupInputTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun blankCredentialsAcceptTextAndHidePlaceholders() {
        composeRule.setContent {
            ManualSetupScreen(busy = false, message = null, onConnect = {}, onBack = {})
        }

        // Account name, server URL, username, password are the four editable fields.
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("https://example.invalid")
        composeRule.onAllNodes(hasSetTextAction())[1].assertTextContains("https://example.invalid")
        composeRule.onNodeWithText("https://line.provider.tv:8080").assertDoesNotExist()

        composeRule.onAllNodes(hasSetTextAction())[2].performTextInput("test-user")
        composeRule.onAllNodes(hasSetTextAction())[2].assertTextContains("test-user")
        composeRule.onNodeWithText("Provider username").assertDoesNotExist()

        composeRule.onAllNodes(hasSetTextAction())[3].performTextInput("test-password")
        composeRule.onNodeWithText("Provider password").assertDoesNotExist()
    }
}
