package com.oh.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.oh.tv.R

val Graphite = Color(0xFF090B10)
val Surface = Color(0xFF23262C)
val SurfaceLifted = Color(0xFF2B2F36)
val WarmWhite = Color(0xFFF6F3EC)
val Muted = Color(0xFFA9AFBA)
val FocusBlue = Color(0xFF5E8FF7)
val ErrorRed = Color(0xFFFF6B6B)

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

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
    MaterialTheme(colorScheme = OhColors) {
        ProvideTextStyle(TextStyle(fontFamily = Manrope), content)
    }
}

