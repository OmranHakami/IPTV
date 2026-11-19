package com.oh.tv.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CatalogMovieCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun focusExpandsTheMeasuredItemAndRevealsItAtTheRowEdge() {
        val outside = FocusRequester()
        val lookups = mutableListOf<Int>()
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalDensity provides Density(0.5f)) {
                Column {
                    Box(Modifier.size(20.dp).focusRequester(outside).focusable())
                    LazyRow(Modifier.width(680.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(3) { index ->
                            val item = CatalogItem(
                                id = "$index", title = "Movie $index", categoryId = null, artworkUrl = null,
                                genre = "Movie", year = "", runtime = null, rating = null, overview = null,
                                metadataKey = null, saved = false, onVisible = {}, onToggleSaved = {}, onOpen = {},
                                onHeld = { lookups += index; null },
                            )
                            CatalogMovieCard(item, focusRequester = null, dimmed = false, onPreviewChanged = {})
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                inputMode.requestInputMode(InputMode.Keyboard)
                outside.requestFocus()
            }
        }
        val first = compose.onNodeWithText("Movie 0")
        val second = compose.onNodeWithText("Movie 1")
        val compact = first.fetchSemanticsNode().boundsInRoot
        val nextLeft = second.fetchSemanticsNode().boundsInRoot.left
        assertEquals(112f, compact.width, 0f)

        compose.mainClock.autoAdvance = false
        first.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.mainClock.advanceTimeBy(96)
        val growing = first.fetchSemanticsNode().boundsInRoot
        assertTrue(growing.width > compact.width && growing.width < 210f)
        assertEquals(compact.left, growing.left, 0f)
        assertEquals(compact.height, growing.height, 0f)
        assertEquals(nextLeft + growing.width - compact.width, second.fetchSemanticsNode().boundsInRoot.left, 0f)

        compose.mainClock.advanceTimeBy(500)
        val expanded = first.fetchSemanticsNode().boundsInRoot
        assertEquals(compact.left, expanded.left, 0f)
        assertEquals(210f, expanded.width, 0f)
        assertEquals(170f, expanded.height, 0f)
        assertEquals(nextLeft + expanded.width - compact.width, second.fetchSemanticsNode().boundsInRoot.left, 0f)

        val last = compose.onNodeWithText("Movie 2")
        compose.runOnIdle { assertTrue(lookups.isEmpty()) }
        last.performSemanticsAction(SemanticsActions.RequestFocus)
        compose.mainClock.advanceTimeBy(4900)
        compose.runOnIdle { assertTrue(lookups.isEmpty()) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(listOf(2), lookups) }
        compose.mainClock.autoAdvance = true
        assertEquals(210f, last.fetchSemanticsNode().boundsInRoot.width, 0f)
    }
}
