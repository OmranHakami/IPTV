package com.oh.tv.ui

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvTextFieldNavigationTest {
    @Test
    fun verticalKeysLeaveSingleLineField() {
        val value = TextFieldValue("server", TextRange(3))

        assertEquals(FocusDirection.Up, tvFocusDirection(Key.DirectionUp, value))
        assertEquals(FocusDirection.Down, tvFocusDirection(Key.DirectionDown, value))
    }

    @Test
    fun horizontalKeysLeaveOnlyAtTextBoundary() {
        val start = TextFieldValue("server", TextRange.Zero)
        val middle = TextFieldValue("server", TextRange(3))
        val end = TextFieldValue("server", TextRange(6))

        assertEquals(FocusDirection.Left, tvFocusDirection(Key.DirectionLeft, start))
        assertNull(tvFocusDirection(Key.DirectionLeft, middle))
        assertNull(tvFocusDirection(Key.DirectionRight, middle))
        assertEquals(FocusDirection.Right, tvFocusDirection(Key.DirectionRight, end))
    }

    @Test
    fun selectionAndNonDirectionalKeysStayInField() {
        val selection = TextFieldValue("server", TextRange(1, 4))

        assertNull(tvFocusDirection(Key.DirectionLeft, selection))
        assertNull(tvFocusDirection(Key.DirectionRight, selection))
        assertNull(tvFocusDirection(Key.Enter, selection))
    }
}
