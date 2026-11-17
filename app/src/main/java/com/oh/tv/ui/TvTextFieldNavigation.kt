package com.oh.tv.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue

internal fun Modifier.tvTextFieldNavigation(
    focusManager: FocusManager,
    value: TextFieldValue,
): Modifier = onPreviewKeyEvent { event ->
    event.type == KeyEventType.KeyDown &&
        tvFocusDirection(event.key, value)?.let(focusManager::moveFocus) == true
}

internal fun tvFocusDirection(key: Key, value: TextFieldValue): FocusDirection? = when {
    key == Key.DirectionUp -> FocusDirection.Up
    key == Key.DirectionDown -> FocusDirection.Down
    !value.selection.collapsed -> null
    key == Key.DirectionLeft && value.selection.start == 0 -> FocusDirection.Left
    key == Key.DirectionRight && value.selection.end == value.text.length -> FocusDirection.Right
    else -> null
}
