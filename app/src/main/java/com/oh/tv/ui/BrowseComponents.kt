package com.oh.tv.ui


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Text
import com.oh.tv.ui.components.FocusSurface
import java.text.NumberFormat
import java.util.Locale

enum class MediaSection { Home, Movies, Shows, MyList }

private val BrowseInk = Color(0xFF070A10)
private val BrowsePanel = Color(0xFF151C27)
private val BrowseStroke = Color(0xFF303A4B)
private val BrowseBright = Color(0xFFF7F8FC)
private val BrowseMuted = Color(0xFF909CAF)
private val BrowsePeach = Color(0xFFFFB86B)

@Composable
fun BrowseScreen(content: @Composable () -> Unit) {
    val horizontalPadding = 42.dp
    val verticalPadding = 20.dp
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(BrowseInk, Color(0xFF0B101A), Color(0xFF11101A))),
        ).padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) { content() }
}

@Composable
fun MediaNavigationBar(
    active: MediaSection,
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onShows: () -> Unit,
    onMyList: () -> Unit,
    onSearch: () -> Unit,
    initialFocus: FocusRequester? = null,
) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(BrowsePeach, Color(0xFFFF7B72)))), contentAlignment = Alignment.Center) {
                Text("▶", color = Color(0xFF17100C), fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Text("OH", color = BrowseBright, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
        }
        Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NavItem("Home", active == MediaSection.Home, onHome, if (active == MediaSection.Home) initialFocus else null)
            NavItem("Movies", active == MediaSection.Movies, onMovies, if (active == MediaSection.Movies) initialFocus else null)
            NavItem("TV Shows", active == MediaSection.Shows, onShows, if (active == MediaSection.Shows) initialFocus else null)
            NavItem("Watchlist", active == MediaSection.MyList, onMyList, if (active == MediaSection.MyList) initialFocus else null)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FocusSurface(onSearch, background = BrowsePanel, focusedBackground = Color(0xFF263247), contentPadding = 9.dp) {
                Text("⌕  Search", color = BrowseBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x227C9CFF)).border(1.dp, Color(0x557C9CFF), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Text("OH", color = Color(0xFFB9C7FF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    FocusSurface(
        onClick = onClick,
        background = if (selected) Color(0x16FFB86B) else Color.Transparent,
        focusedBackground = if (selected) Color(0x28FFB86B) else Color(0xFF263247),
        contentPadding = 9.dp,
        focusRequester = focusRequester,
    ) { Text(label, color = if (selected) Color(0xFFFFD09A) else BrowseMuted, fontSize = 11.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal) }
}

@Composable
fun MovieGenreMenu(
    open: Boolean,
    selected: String?,
    totalMovies: Int,
    counts: Map<String, Int>,
    onToggle: () -> Unit,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    BackHandler(enabled = open, onBack = onDismiss)
    val selectedLabel = selected?.let(::displayGenre) ?: "All genres"
    Box(Modifier.width(220.dp)) {
        FocusSurface(onToggle, Modifier.fillMaxWidth(), BrowsePanel, Color(0xFF202938), 10.dp, focusRequester) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("◇  $selectedLabel", color = BrowseBright, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (open) "⌃" else "⌄", color = BrowsePeach, fontWeight = FontWeight.Bold)
            }
        }
        if (open) {
            val firstFocus = androidx.compose.runtime.remember { FocusRequester() }
            val popupOffset = with(LocalDensity.current) { 48.dp.roundToPx() }
            LaunchedEffect(Unit) { firstFocus.requestFocus() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, popupOffset),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                androidx.compose.foundation.layout.Column(
                    Modifier.width(220.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFA192230), Color(0xFA111720))))
                        .border(1.dp, Color(0xFF4B586D), RoundedCornerShape(16.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("CHOOSE A GENRE", color = Color(0xFF8391A6), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, modifier = Modifier.padding(8.dp))
                    GenreOption("All genres", totalMovies, selected == null, { onSelect(null) }, firstFocus)
                    counts.forEach { (genre, count) -> GenreOption(displayGenre(genre), count, selected == genre, { onSelect(genre) }) }
                }
            }
        }
    }
}

@Composable
private fun GenreOption(label: String, count: Int, selected: Boolean, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    FocusSurface(
        onClick, Modifier.fillMaxWidth(),
        if (selected) Color(0x20FFB86B) else Color.Transparent,
        if (selected) Color(0x30FFB86B) else Color(0xFF263247), 8.dp, focusRequester,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column {
                Text(label, color = if (selected) Color(0xFFFFE0B8) else Color(0xFFD0D7E2), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${NumberFormat.getIntegerInstance(Locale.US).format(count)} titles", color = Color(0xFF718096), fontSize = 8.sp)
            }
            if (selected) Text("✓", color = BrowsePeach, fontSize = 11.sp)
        }
    }
}

private fun displayGenre(value: String): String = if (value == "Science Fiction") "Sci-Fi" else value
