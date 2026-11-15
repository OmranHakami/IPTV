package com.oh.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Graphite = Color(0xFF17191D)
val Surface = Color(0xFF23262C)
val SurfaceLifted = Color(0xFF2B2F36)
val WarmWhite = Color(0xFFF6F3EC)
val Muted = Color(0xFFA9AFBA)
val FocusBlue = Color(0xFF5E8FF7)
val ErrorRed = Color(0xFFFF6B6B)

private val OhColors = darkColorScheme(
    primary = FocusBlue,
    onPrimary = Color.White,
    background = Graphite,
    onBackground = WarmWhite,
    surface = Surface,
    onSurface = WarmWhite,
    error = ErrorRed,
)

@Composable
fun OhTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OhColors, content = content)
}

