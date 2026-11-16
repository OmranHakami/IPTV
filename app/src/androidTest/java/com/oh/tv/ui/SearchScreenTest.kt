package com.oh.tv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun downFromSearchInputFocusesFirstResult() {
        composeRule.setContent {
            val resultFocus = remember { FocusRequester() }
            Column {
                SearchInput("movie", {}, resultFocus)
                Box(Modifier.size(80.dp).focusRequester(resultFocus).testTag("first-result").focusable())
            }
        }
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        composeRule.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("first-result").assertIsFocused()
    }

    @Test
    fun typingUsesSystemEditableTextFieldWithoutCustomKeyboard() {
        composeRule.setContent {
            val query = remember { mutableStateOf("") }
            SearchInput(query.value, { query.value = it }, null)
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("movie")
        composeRule.onNode(hasSetTextAction()).assertTextContains("movie")
        composeRule.onNodeWithText("ON-SCREEN KEYBOARD").assertDoesNotExist()
    }

    @Test
    fun searchFailureExposesClickableRetry() {
        var attempts = 0
        composeRule.setContent {
            SearchFailure("Search failed. Check your library and try again.") { attempts++ }
        }
        composeRule.onNodeWithText("Retry search").performClick()
        composeRule.runOnIdle { assertEquals(1, attempts) }
    }
}
