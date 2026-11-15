package com.oh.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.oh.tv.R
import com.oh.tv.data.local.MovieEntity
import com.oh.tv.data.local.SeriesEntity
import com.oh.tv.data.local.VodCategoryEntity
import kotlinx.coroutines.delay
import java.util.Locale

private val CatalogBackground = Color(0xFF090B10)
private val CatalogSurface = Color(0xFF121722)
private val CatalogInset = Color(0xFF181F2C)
private val CatalogStroke = Color(0xFF283247)
private val CatalogBright = Color(0xFFF7F8FC)
private val CatalogQuiet = Color(0xFF98A3B7)
private val CatalogPeach = Color(0xFFFFB86B)
private val CatalogBlue = Color(0xFF7C9CFF)

private data class CatalogItem(
    val id: String,
    val categoryId: String?,
    val title: String,
    val artworkUrl: String?,
    val genre: String,
    val year: String,
    val runtime: String?,
    val rating: Double?,
    val overview: String?,
    val metadataKey: Long?,
    val saved: Boolean,
    val onVisible: () -> Unit,
    val onToggleSaved: () -> Unit,
    val onOpen: () -> Unit,
)

@Composable
fun DesignedMovieCatalogScreen(viewModel: OhViewModel, select: (MovieEntity) -> Unit) {
    val paging = viewModel.movies.collectAsLazyPagingItems()
    val categories by viewModel.movieCategories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedMovieCategory.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteMovies.collectAsStateWithLifecycle()
    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.providerId }
    val loaded = loadedItems(paging, 72).map { movie ->
        CatalogItem(
            id = movie.providerId,
            categoryId = movie.categoryId,
            title = movie.displayName,
            artworkUrl = movie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: movie.providerIconUrl,
            genre = movie.genres?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() } ?: "MOVIE",
            year = movie.displayReleaseDate?.take(4).orEmpty(),
            runtime = movie.runtimeMinutes?.let(::formatRuntime),
            rating = movie.tmdbRating ?: movie.providerRating,
            overview = movie.displayOverview,
            metadataKey = movie.metadataUpdatedAt,
            saved = movie.providerId in favoriteIds,
            onVisible = { viewModel.loadMovieMetadata(movie) },
            onToggleSaved = { viewModel.toggleFavorite(movie) },
            onOpen = { select(movie) },
        )
    }
    CatalogBrowseScreen(
        section = MediaSection.Movies,
        title = "Movies",
        items = loaded,
        pagingLoading = paging.loadState.refresh is LoadState.Loading,
        categories = categories,
        selectedCategory = selectedCategory,
        onSelectCategory = viewModel::selectMovieCategory,
        onHome = { viewModel.navigate(Destination.Hub) },
        onMovies = {},
        onShows = { viewModel.navigate(Destination.TvShows) },
        onMyList = { viewModel.navigate(Destination.MyList) },
        onSearch = { viewModel.navigate(Destination.Search) },
        onProfile = { viewModel.openSettings(Destination.Movies) },
    )
}

@Composable
fun DesignedSeriesCatalogScreen(viewModel: OhViewModel, select: (SeriesEntity) -> Unit) {
    val paging = viewModel.series.collectAsLazyPagingItems()
    val categories by viewModel.seriesCategories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedSeriesCategory.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteSeries.collectAsStateWithLifecycle()
    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.providerId }
    val loaded = loadedItems(paging, 72).map { show ->
        CatalogItem(
            id = show.providerId,
            categoryId = show.categoryId,
            title = show.displayName,
            artworkUrl = show.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: show.providerIconUrl,
            genre = show.genres?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() } ?: "SERIES",
            year = show.displayReleaseDate?.take(4).orEmpty(),
            runtime = null,
            rating = show.tmdbRating ?: show.providerRating,
            overview = show.displayOverview,
            metadataKey = show.metadataUpdatedAt,
            saved = show.providerId in favoriteIds,
            onVisible = { viewModel.loadSeriesMetadata(show) },
            onToggleSaved = { viewModel.toggleFavorite(show) },
            onOpen = { select(show) },
        )
    }
    CatalogBrowseScreen(
        section = MediaSection.Shows,
        title = "TV Shows",
        items = loaded,
        pagingLoading = paging.loadState.refresh is LoadState.Loading,
        categories = categories,
        selectedCategory = selectedCategory,
        onSelectCategory = viewModel::selectSeriesCategory,
        onHome = { viewModel.navigate(Destination.Hub) },
        onMovies = { viewModel.navigate(Destination.Movies) },
        onShows = {},
        onMyList = { viewModel.navigate(Destination.MyList) },
        onSearch = { viewModel.navigate(Destination.Search) },
        onProfile = { viewModel.openSettings(Destination.TvShows) },
    )
}

@Composable
private fun CatalogBrowseScreen(
    section: MediaSection,
    title: String,
    items: List<CatalogItem>,
    pagingLoading: Boolean,
    categories: List<VodCategoryEntity>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onShows: () -> Unit,
    onMyList: () -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
) {
    var categoryOpen by remember { mutableStateOf(false) }
    val firstCardFocus = remember { FocusRequester() }
    val categoryName = categories.firstOrNull { it.providerId == selectedCategory }?.name
    BackHandler(enabled = categoryOpen || selectedCategory != null) {
        if (categoryOpen) categoryOpen = false else onSelectCategory(null)
    }
    LaunchedEffect(items.firstOrNull()?.id) { if (items.isNotEmpty()) firstCardFocus.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize().background(CatalogBackground)) {
        val density = LocalDensity.current
        val scale = minOf(maxWidth.value / 1440f, maxHeight.value / 810f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Box(Modifier.align(Alignment.Center).width(1440.dp).height(810.dp)) {
                Box(Modifier.fillMaxWidth().height(190.dp).background(Brush.verticalGradient(listOf(Color(0x8818233A), Color.Transparent))))
                CatalogNavigation(section, onHome, onMovies, onShows, onMyList, onSearch, onProfile)
                Column(
                    Modifier.offset(32.dp, 94.dp).width(1376.dp).height(716.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth().height(if (categoryName == null) 40.dp else 58.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (categoryName != null) CatalogIconButton("back") { onSelectCategory(null) }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(categoryName ?: title, color = CatalogBright, fontSize = if (categoryName == null) 28.sp else 24.sp, fontWeight = FontWeight.ExtraBold)
                                if (categoryName != null) Text("Browse this category. Press BACK to return to the full library.", color = CatalogQuiet, fontSize = 10.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CatalogCategorySelector(categoryName ?: "All categories", categories, selectedCategory, categoryOpen, { categoryOpen = !categoryOpen }) {
                                onSelectCategory(it)
                                categoryOpen = false
                            }
                            CatalogControl("sort", "Newest")
                        }
                    }
                    if (pagingLoading && items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading your library…", color = CatalogQuiet, fontSize = 14.sp) }
                    } else if (items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No titles are available in this category.", color = CatalogQuiet, fontSize = 14.sp) }
                    } else {
                        CatalogRows(items, categories, selectedCategory, firstCardFocus)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogNavigation(
    section: MediaSection,
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onShows: () -> Unit,
    onMyList: () -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        Modifier.offset(32.dp, 18.dp).width(1376.dp).height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC0E131E)).border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(painterResource(R.drawable.pairing_brand), null, Modifier.size(40.dp))
            Text("CHROMA", color = CatalogBright, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.1.sp)
        }
        Row(Modifier.height(40.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CatalogNavItem("Home", section == MediaSection.Home, onHome)
            CatalogNavItem("Movies", section == MediaSection.Movies, onMovies)
            CatalogNavItem("TV Shows", section == MediaSection.Shows, onShows)
            CatalogNavItem("My List", section == MediaSection.MyList, onMyList)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            var searchFocused by remember { mutableStateOf(false) }
            Row(
                Modifier.width(190.dp).height(40.dp).clip(RoundedCornerShape(10.dp)).background(if (searchFocused) Color(0xFF202839) else CatalogSurface)
                    .border(if (searchFocused) 2.dp else 1.dp, if (searchFocused) CatalogBlue else Color(0xFF34425A), RoundedCornerShape(10.dp))
                    .onFocusChanged { searchFocused = it.isFocused }.clickable(onClick = onSearch).padding(horizontal = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                PairingIcon("search", if (searchFocused) CatalogBright else Color(0xFFAAB5C6), Modifier.size(18.dp))
                Text("Search", color = if (searchFocused) CatalogBright else Color(0xFFC6CFDD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            var profileFocused by remember { mutableStateOf(false) }
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (profileFocused) Color(0xFF263152) else Color(0xFF1A2340))
                    .border(if (profileFocused) 2.dp else 1.dp, if (profileFocused) CatalogBlue else Color(0x777C9CFF), RoundedCornerShape(12.dp))
                    .onFocusChanged { profileFocused = it.isFocused }.clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) { Text("OH", color = Color(0xFFB9C7FF), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
private fun CatalogNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(40.dp).clip(shape)
            .background(if (focused) Color(0xFF202839) else if (selected) CatalogPeach.copy(alpha = 0.09f) else Color.Transparent)
            .border(if (focused) 2.dp else if (selected) 1.dp else 0.dp, if (focused) CatalogBlue else CatalogPeach.copy(alpha = 0.4f), shape)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 17.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (focused) CatalogBright else if (selected) Color(0xFFFFD09A) else Color(0xFF909CAF), fontSize = 13.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold) }
}

@Composable
private fun CatalogControl(icon: String, label: String) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(if (focused) Color(0xFF202839) else CatalogSurface.copy(alpha = 0.8f))
            .border(if (focused) 2.dp else 1.dp, if (focused) CatalogBlue else Color(0xFF34425A), RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }.clickable { }.padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        PairingIcon(icon, CatalogQuiet, Modifier.size(16.dp))
        Text(label, color = Color(0xFFF1F4F8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        PairingIcon("chevron", CatalogQuiet, Modifier.size(15.dp))
    }
}

@Composable
private fun CatalogCategorySelector(
    label: String,
    categories: List<VodCategoryEntity>,
    selected: String?,
    open: Boolean,
    onToggle: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    Box(Modifier.width(206.dp)) {
        var focused by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(11.dp)).background(if (focused || open) Color(0xFF202839) else CatalogSurface)
                .border(if (focused || open) 2.dp else 1.dp, if (focused || open) CatalogBlue else Color(0xFF34425A), RoundedCornerShape(11.dp))
                .onFocusChanged { focused = it.isFocused }.clickable(onClick = onToggle).padding(horizontal = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            PairingIcon("grid", CatalogQuiet, Modifier.size(17.dp))
            Text(label, color = Color(0xFFF1F4F8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            PairingIcon("chevron", CatalogQuiet, Modifier.size(15.dp))
        }
        if (open) {
            val firstFocus = remember { FocusRequester() }
            val popupOffset = with(LocalDensity.current) { 46.dp.roundToPx() }
            LaunchedEffect(Unit) { firstFocus.requestFocus() }
            Popup(alignment = Alignment.TopStart, offset = IntOffset(0, popupOffset), onDismissRequest = onToggle, properties = PopupProperties(focusable = true)) {
                LazyColumn(
                    Modifier.width(300.dp).height(430.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFA121722))
                        .border(1.dp, Color(0xFF4B586D), RoundedCornerShape(14.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item { CatalogCategoryOption("All categories", selected == null, firstFocus) { onSelect(null) } }
                    items(categories, key = { it.providerId }) { category ->
                        CatalogCategoryOption(category.name, selected == category.providerId) { onSelect(category.providerId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogCategoryOption(label: String, selected: Boolean, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(9.dp)).background(if (focused) Color(0xFF263247) else if (selected) CatalogPeach.copy(alpha = 0.1f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) Color(0xFFFFD09A) else CatalogBright, fontSize = 12.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selected) PairingIcon("check", CatalogPeach, Modifier.size(15.dp))
    }
}

@Composable
private fun CatalogRows(items: List<CatalogItem>, categories: List<VodCategoryEntity>, selectedCategory: String?, firstFocus: FocusRequester) {
    val names = remember(categories) { categories.associate { it.providerId to it.name } }
    val groups = if (selectedCategory != null) {
        items.chunked(6).mapIndexed { index, row -> (if (index == 0) names[selectedCategory] ?: "SELECTED CATEGORY" else "MORE TO EXPLORE") to row }
    } else {
        items.groupBy { it.categoryId }.map { (id, row) -> (names[id] ?: "RECENTLY ADDED") to row.take(6) }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(groups, key = { it.first + it.second.firstOrNull()?.id }) { (label, rowItems) ->
            var previewedCardId by remember { mutableStateOf<String?>(null) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(label.uppercase(Locale.US), color = Color(0xFFA8B3C6), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                LazyRow(
                    Modifier.fillMaxWidth().height(360.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(rowItems, key = { _, item -> item.id }) { index, item ->
                        CatalogMovieCard(
                            item = item,
                            focusRequester = if (item.id == items.first().id) firstFocus else null,
                            dimmed = previewedCardId != null && previewedCardId != item.id,
                            expandLeft = index >= 4,
                            onPreviewChanged = { showing ->
                                if (showing) previewedCardId = item.id else if (previewedCardId == item.id) previewedCardId = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogMovieCard(
    item: CatalogItem,
    focusRequester: FocusRequester?,
    dimmed: Boolean,
    expandLeft: Boolean,
    onPreviewChanged: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    val surroundingScale by animateFloatAsState(if (dimmed) 0.96f else 1f, tween(160), label = "surrounding card scale")
    val surroundingAlpha by animateFloatAsState(if (dimmed) 0.5f else 1f, tween(160), label = "surrounding card alpha")
    LaunchedEffect(item.id, item.metadataKey) { item.onVisible() }
    LaunchedEffect(focused) {
        preview = false
        onPreviewChanged(false)
        if (focused) {
            delay(800)
            preview = true
            onPreviewChanged(true)
        }
    }
    Box(
        Modifier.width(224.dp).height(340.dp).zIndex(if (preview) 3f else if (focused) 2f else 0f)
            .graphicsLayer { scaleX = surroundingScale; scaleY = surroundingScale; alpha = surroundingAlpha }
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(onClick = item.onOpen),
    ) {
        if (!preview) CatalogCompactCard(item, focused)
        AnimatedVisibility(
            visible = preview,
            modifier = Modifier.offset(x = if (expandLeft) (-196).dp else 0.dp)
                .wrapContentSize(Alignment.TopStart, unbounded = true).requiredWidth(420.dp).height(340.dp),
            enter = fadeIn(tween(160), initialAlpha = 0.75f),
            exit = fadeOut(tween(120)),
        ) { CatalogDelayedPreview(item, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun CatalogCompactCard(item: CatalogItem, focused: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(160), label = "card scale")
    val borderWidth by animateDpAsState(if (focused) 2.dp else 1.dp, tween(160), label = "card border")
    val borderColor by animateColorAsState(if (focused) CatalogBlue else CatalogStroke, tween(160), label = "card border color")
    Box(
        Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(CatalogSurface).border(borderWidth, borderColor, shape)
    ) {
        CatalogArtwork(item, Modifier.fillMaxWidth().height(246.dp))
        CatalogGenreBadge(item.genre, Modifier.offset(12.dp, 12.dp))
        CatalogRatingBadge(item.rating, Modifier.offset(164.dp, 12.dp))
        Column(Modifier.offset(16.dp, 272.dp).width(192.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.title, color = CatalogBright, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CatalogMetadata(item)
        }
    }
}

@Composable
private fun CatalogDelayedPreview(item: CatalogItem, modifier: Modifier) {
    val shape = RoundedCornerShape(17.dp)
    Box(modifier.clip(shape).background(CatalogSurface).border(2.dp, CatalogBlue, shape)) {
        CatalogArtwork(item, Modifier.fillMaxWidth().height(218.dp), play = true)
        CatalogGenreBadge(item.genre, Modifier.offset(14.dp, 14.dp))
        CatalogRatingBadge(item.rating, Modifier.offset(358.dp, 14.dp))
        Row(
            Modifier.offset(16.dp, 174.dp).height(34.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xCC090B10))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(9.dp)).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            PairingIcon("volume", Color.White, Modifier.size(14.dp))
            Text("Preview playing", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.offset(18.dp, 232.dp).width(384.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.title, color = CatalogBright, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CatalogMetadata(item)
            Text(item.overview ?: "More information is available when you open this title.", color = CatalogQuiet, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CatalogRatingBadge(rating: Double?, modifier: Modifier) {
    Box(
        modifier.width(48.dp).height(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC090B10)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            PairingIcon("star", CatalogPeach, Modifier.size(12.dp))
            Text(rating?.let { String.format(Locale.US, "%.1f", it) } ?: "—", color = CatalogBright, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun CatalogArtwork(item: CatalogItem, modifier: Modifier, play: Boolean = false) {
    Box(modifier.background(CatalogInset), contentAlignment = Alignment.Center) {
        AsyncImage(item.artworkUrl, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (item.artworkUrl == null || play) PairingIcon(if (play) "play" else "movie", if (play) Color.White else CatalogBlue, Modifier.size(if (play) 56.dp else 44.dp))
    }
}

@Composable
private fun CatalogGenreBadge(genre: String, modifier: Modifier) {
    Box(modifier.height(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC090B10)).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
        Text(genre.uppercase(Locale.US), color = CatalogBright, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, maxLines = 1)
    }
}

@Composable
private fun CatalogMetadata(item: CatalogItem) {
    Text(listOfNotNull(item.year.takeIf { it.isNotBlank() }, item.runtime).joinToString("  •  "), color = CatalogQuiet, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CatalogIconButton(icon: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (focused) Color(0xFF202839) else CatalogSurface)
            .border(if (focused) 2.dp else 1.dp, if (focused) CatalogBlue else CatalogStroke, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { PairingIcon(icon, CatalogBright, Modifier.size(17.dp)) }
}

@Composable
private fun <T : Any> loadedItems(items: LazyPagingItems<T>, limit: Int): List<T> =
    (0 until minOf(items.itemCount, limit)).mapNotNull { items[it] }

private fun formatRuntime(minutes: Int): String = if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"

