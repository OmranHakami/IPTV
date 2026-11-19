package com.oh.tv.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import com.oh.tv.BuildConfig
import com.oh.tv.data.remote.TmdbTrailerGateway
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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
import com.oh.tv.data.local.PlaybackProgressEntity
import com.oh.tv.data.local.SeriesEntity
import com.oh.tv.data.local.SeriesEpisodeEntity
import com.oh.tv.data.local.VodCategoryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.util.Locale

private val CatalogBackground = Color(0xFF090B10)
private val CatalogSurface = Color(0xFF121722)
private val CatalogInset = Color(0xFF181F2C)
private val CatalogStroke = Color(0xFF283247)
private val CatalogBright = Color(0xFFF7F8FC)
private val CatalogQuiet = Color(0xFF98A3B7)
private val CatalogPeach = Color(0xFFFFB86B)
private val CatalogBlue = Color(0xFF7C9CFF)

private enum class MediaSection { Home, Movies, Shows, MyList }

internal data class CatalogItem(
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
    val tmdbId: Long? = null,
    val trailerType: String = "movie",
    val saved: Boolean,
    val onVisible: () -> Unit = {},
    val onToggleSaved: () -> Unit,
    val onOpen: () -> Unit,
    val onHeld: (suspend () -> Long?)? = null,
)

@Composable
fun DesignedMovieCatalogScreen(viewModel: OhViewModel, select: (MovieEntity) -> Unit) {
    val paging = viewModel.movies.collectAsLazyPagingItems()
    val categories by viewModel.movieCategories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedMovieCategory.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteMovies.collectAsStateWithLifecycle()
    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.providerId }
    var visibleLimit by remember(selectedCategory) { mutableStateOf(72) }
    val loaded = loadedItems(paging, visibleLimit).map { movie ->
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
            onHeld = { viewModel.loadMovieMetadata(movie).resolvedTmdbId },
            onToggleSaved = { viewModel.toggleFavorite(movie) },
            onOpen = { select(movie) },
        )
    }
    CatalogBrowseScreen(
        section = MediaSection.Movies,
        items = loaded,
        pagingLoading = paging.loadState.refresh is LoadState.Loading,
        pagingError = paging.loadState.refresh is LoadState.Error,
        appendLoading = paging.loadState.append is LoadState.Loading,
        appendError = paging.loadState.append is LoadState.Error,
        hasMore = loaded.size < paging.itemCount,
        onLoadMore = { visibleLimit += 72 },
        onRetry = { paging.retry() },
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
    var visibleLimit by remember(selectedCategory) { mutableStateOf(72) }
    val loaded = loadedItems(paging, visibleLimit).map { show ->
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
            tmdbId = show.tmdbId,
            trailerType = "tv",
            saved = show.providerId in favoriteIds,
            onVisible = { viewModel.loadSeriesMetadata(show) },
            onToggleSaved = { viewModel.toggleFavorite(show) },
            onOpen = { select(show) },
        )
    }
    CatalogBrowseScreen(
        section = MediaSection.Shows,
        items = loaded,
        pagingLoading = paging.loadState.refresh is LoadState.Loading,
        pagingError = paging.loadState.refresh is LoadState.Error,
        appendLoading = paging.loadState.append is LoadState.Loading,
        appendError = paging.loadState.append is LoadState.Error,
        hasMore = loaded.size < paging.itemCount,
        onLoadMore = { visibleLimit += 72 },
        onRetry = { paging.retry() },
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

private enum class SearchResultType { All, Movies, Shows }
private data class SearchEntry(val item: CatalogItem, val type: SearchResultType)

@Composable
fun DesignedSearchScreen(viewModel: OhViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favoriteMovies by viewModel.favoriteMovies.collectAsStateWithLifecycle()
    val favoriteSeries by viewModel.favoriteSeries.collectAsStateWithLifecycle()
    val movieFavoriteIds = favoriteMovies.mapTo(mutableSetOf()) { it.providerId }
    val seriesFavoriteIds = favoriteSeries.mapTo(mutableSetOf()) { it.providerId }
    val entries = state.searchMovies.map { movie ->
        SearchEntry(
            CatalogItem(
                id = "movie:${movie.providerId}", categoryId = movie.categoryId, title = movie.displayName,
                artworkUrl = movie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: movie.providerIconUrl,
                genre = movie.genres?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() } ?: "MOVIE",
                year = movie.displayReleaseDate?.take(4).orEmpty(), runtime = movie.runtimeMinutes?.let(::formatRuntime),
                rating = movie.tmdbRating ?: movie.providerRating, overview = movie.displayOverview,
                metadataKey = movie.metadataUpdatedAt, saved = movie.providerId in movieFavoriteIds,
                onHeld = { viewModel.loadMovieMetadata(movie).resolvedTmdbId }, onToggleSaved = { viewModel.toggleFavorite(movie) },
                onOpen = { viewModel.openMovie(movie) },
            ),
            SearchResultType.Movies,
        )
    } + state.searchSeries.map { show ->
        SearchEntry(
            CatalogItem(
                id = "series:${show.providerId}", categoryId = show.categoryId, title = show.displayName,
                artworkUrl = show.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: show.providerIconUrl,
                genre = show.genres?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() } ?: "SERIES",
                year = show.displayReleaseDate?.take(4).orEmpty(), runtime = null,
                rating = show.tmdbRating ?: show.providerRating, overview = show.displayOverview,
                metadataKey = show.metadataUpdatedAt, tmdbId = show.tmdbId, trailerType = "tv", saved = show.providerId in seriesFavoriteIds,
                onVisible = { viewModel.loadSeriesMetadata(show) }, onToggleSaved = { viewModel.toggleFavorite(show) },
                onOpen = { viewModel.openSeries(show) },
            ),
            SearchResultType.Shows,
        )
    }
    SearchExperience(
        state = state,
        entries = entries,
        onQuery = viewModel::search,
        onHome = { viewModel.navigate(Destination.Hub) },
        onMovies = { viewModel.navigate(Destination.Movies) },
        onShows = { viewModel.navigate(Destination.TvShows) },
        onMyList = { viewModel.navigate(Destination.MyList) },
        onProfile = { viewModel.openSettings(Destination.Search) },
        onBack = { viewModel.navigate(state.browseReturn) },
    )
}

@Composable
fun DesignedWatchlistScreen(viewModel: OhViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val movies by viewModel.favoriteMovies.collectAsStateWithLifecycle()
    val series by viewModel.favoriteSeries.collectAsStateWithLifecycle()
    val continueMovies by viewModel.continueMovies.collectAsStateWithLifecycle()
    val continueEpisodes by viewModel.continueEpisodes.collectAsStateWithLifecycle()
    val progressItems by viewModel.continueWatching.collectAsStateWithLifecycle()
    val initialFocus = remember { FocusRequester() }
    var filter by remember { mutableStateOf(SearchResultType.All) }
    val saved = movies.map { movie ->
        SearchEntry(
            CatalogItem(
                id = "movie:${movie.providerId}", categoryId = movie.categoryId, title = movie.displayName,
                artworkUrl = movie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: movie.providerIconUrl,
                genre = movie.genres?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() } ?: "MOVIE",
                year = movie.displayReleaseDate?.take(4).orEmpty(), runtime = movie.runtimeMinutes?.let(::formatRuntime),
                rating = movie.tmdbRating ?: movie.providerRating, overview = movie.displayOverview,
                metadataKey = movie.metadataUpdatedAt, saved = true,
                onHeld = { viewModel.loadMovieMetadata(movie).resolvedTmdbId }, onToggleSaved = { viewModel.toggleFavorite(movie) },
                onOpen = { viewModel.openMovie(movie) },
            ),
            SearchResultType.Movies,
        )
    } + series.map { show ->
        SearchEntry(
            CatalogItem(
                id = "series:${show.providerId}", categoryId = show.categoryId, title = show.displayName,
                artworkUrl = show.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: show.providerIconUrl,
                genre = show.genres?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() } ?: "SERIES",
                year = show.displayReleaseDate?.take(4).orEmpty(), runtime = null,
                rating = show.tmdbRating ?: show.providerRating, overview = show.displayOverview,
                metadataKey = show.metadataUpdatedAt, tmdbId = show.tmdbId, trailerType = "tv", saved = true,
                onVisible = { viewModel.loadSeriesMetadata(show) }, onToggleSaved = { viewModel.toggleFavorite(show) },
                onOpen = { viewModel.openSeries(show) },
            ),
            SearchResultType.Shows,
        )
    }
    val filtered = saved.filter { filter == SearchResultType.All || it.type == filter }
    val continueProgress = progressItems.firstOrNull()
    val continueMovie = continueProgress?.takeIf { it.contentType == "movie" }?.let { progress -> continueMovies.firstOrNull { it.providerId == progress.contentId } }
    val continueEpisode = continueProgress?.takeIf { it.contentType == "episode" }?.let { progress -> continueEpisodes.firstOrNull { it.providerId == progress.contentId } }
    val hasContinueCard = continueProgress != null && (continueMovie != null || continueEpisode != null)
    LaunchedEffect(continueMovie?.providerId, continueEpisode?.providerId, filtered.firstOrNull()?.item?.id) {
        if (hasContinueCard || filtered.isNotEmpty()) initialFocus.requestFocus()
    }
    BackHandler { viewModel.navigate(state.browseReturn) }
    BoxWithConstraints(Modifier.fillMaxSize().background(CatalogBackground)) {
        val density = LocalDensity.current
        val scale = minOf(maxWidth.value / 1440f, maxHeight.value / 810f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Box(Modifier.align(Alignment.Center).width(1440.dp).height(810.dp).background(CatalogBackground)) {
                CatalogNavigation(
                    MediaSection.MyList,
                    onHome = { viewModel.navigate(Destination.Hub) },
                    onMovies = { viewModel.navigate(Destination.Movies) },
                    onShows = { viewModel.navigate(Destination.TvShows) },
                    onMyList = {},
                    onSearch = { viewModel.navigate(Destination.Search) },
                    onProfile = { viewModel.openSettings(Destination.MyList) },
                )
                Column(
                    Modifier.offset(48.dp, 104.dp).width(1344.dp).height(678.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Watchlist", color = CatalogBright, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                            Text("${saved.size} saved  •  ${progressItems.size} in progress", color = CatalogQuiet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchChip("All", filter == SearchResultType.All) { filter = SearchResultType.All }
                            SearchChip("Movies", filter == SearchResultType.Movies) { filter = SearchResultType.Movies }
                            SearchChip("TV Shows", filter == SearchResultType.Shows) { filter = SearchResultType.Shows }
                        }
                    }
                    Column(Modifier.fillMaxWidth().height(205.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Continue watching", color = CatalogBright, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Newest progress first", color = CatalogQuiet, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        when {
                            continueMovie != null -> WatchlistContinueCard(
                                title = continueMovie.displayName,
                                artworkUrl = continueMovie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: continueMovie.providerIconUrl,
                                metadata = listOfNotNull("MOVIE", continueMovie.displayReleaseDate?.take(4)).joinToString("  •  "),
                                progress = continueProgress,
                                focusRequester = initialFocus,
                                onHeld = { viewModel.loadMovieMetadata(continueMovie) },
                                onResume = { viewModel.resumeMovie(continueMovie) },
                            )
                            continueEpisode != null -> WatchlistContinueCard(
                                title = continueEpisode.title,
                                artworkUrl = continueEpisode.posterUrl,
                                metadata = "TV SHOW  •  S${continueEpisode.seasonNumber} E${continueEpisode.episodeNumber}",
                                progress = continueProgress,
                                focusRequester = initialFocus,
                                onResume = { viewModel.resumeEpisode(continueEpisode) },
                            )
                            else -> WatchlistEmpty("Your unfinished movies and episodes will appear here", Modifier.width(438.dp).height(172.dp))
                        }
                    }
                    Column(Modifier.fillMaxWidth().height(373.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Saved for later", color = CatalogBright, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("${filtered.size} titles", color = CatalogQuiet, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (filtered.isEmpty()) WatchlistEmpty("Add a movie or TV show to see it here", Modifier.fillMaxWidth().height(340.dp))
                        else LazyRow(Modifier.fillMaxWidth().height(340.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(filtered, key = { _, entry -> entry.item.id }) { index, entry ->
                                WatchlistSavedCard(entry.item, if (!hasContinueCard && index == 0) initialFocus else null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistContinueCard(
    title: String,
    artworkUrl: String?,
    metadata: String,
    progress: PlaybackProgressEntity,
    focusRequester: FocusRequester,
    onHeld: (suspend () -> Unit)? = null,
    onResume: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) CatalogBlue else CatalogStroke, tween(160), label = "continue border")
    val borderWidth by animateDpAsState(if (focused) 3.dp else 1.dp, tween(160), label = "continue border width")
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, tween(160), label = "continue scale")
    val fraction = (progress.positionMs.toFloat() / progress.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val lookup by rememberUpdatedState(onHeld)
    LaunchedEffect(focused, progress.contentType, progress.contentId) {
        if (focused && lookup != null) {
            delay(MoviePreviewHoldMs)
            lookup?.invoke()
        }
    }
    Row(
        Modifier.width(438.dp).height(172.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 12.dp else 0.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF27345B), Color(0xFF111827))))
            .border(borderWidth, border, RoundedCornerShape(16.dp)).focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onResume),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            artworkUrl,
            title,
            Modifier.width(120.dp).fillMaxHeight(),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(metadata, color = Color(0xFFB9C7FF), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(title, color = CatalogBright, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(watchlistRemainingLabel(progress), color = CatalogQuiet, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF465064))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(CatalogPeach))
            }
            Row(
                Modifier.height(31.dp).clip(RoundedCornerShape(9.dp)).background(if (focused) CatalogBright else Color(0x1AFFFFFF)).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                PairingIcon("play", if (focused) CatalogSurface else CatalogBright, Modifier.size(13.dp))
                Text("Resume", color = if (focused) CatalogSurface else CatalogBright, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun WatchlistSavedCard(item: CatalogItem, focusRequester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    CatalogCardMetadata(item, focused)
    Box(
        Modifier.width(224.dp).height(340.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = item.onOpen),
    ) { CatalogCompactCard(item, focused, savedBadge = true) }
}

@Composable
private fun WatchlistEmpty(message: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(CatalogSurface).border(1.dp, CatalogStroke, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Text(message, color = CatalogQuiet, fontSize = 12.sp)
    }
}

internal fun watchlistRemainingLabel(progress: PlaybackProgressEntity): String {
    val minutes = ((progress.durationMs - progress.positionMs).coerceAtLeast(0) / 60_000)
    return if (minutes > 0) "${minutes}m left" else "Resume"
}

@Composable
private fun SearchExperience(
    state: OhUiState,
    entries: List<SearchEntry>,
    onQuery: (String) -> Unit,
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onShows: () -> Unit,
    onMyList: () -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit,
) {
    var filter by remember { mutableStateOf(SearchResultType.All) }
    val firstResultFocus = remember { FocusRequester() }
    var focusedId by remember(state.searchQuery) { mutableStateOf<String?>(null) }
    val filtered = entries.filter { filter == SearchResultType.All || it.type == filter }
    val focused = filtered.firstOrNull { it.item.id == focusedId } ?: filtered.firstOrNull()
    BackHandler { onBack() }
    BoxWithConstraints(Modifier.fillMaxSize().background(CatalogBackground)) {
        val density = LocalDensity.current
        val scale = minOf(maxWidth.value / 1440f, maxHeight.value / 810f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Box(Modifier.align(Alignment.Center).width(1440.dp).height(810.dp).background(CatalogBackground)) {
                SearchNavigation(state.searchQuery, onQuery, onHome, onMovies, onShows, onMyList, onProfile, if (filtered.isNotEmpty() && !state.searching && state.searchError == null) firstResultFocus else null)
                when {
                    state.searchQuery.isBlank() -> SearchDiscover(state.recentSearches, onQuery)
                    state.searching -> SearchLoading(state.searchQuery)
                    state.searchError != null -> SearchFailure(state.searchError, { onQuery(state.searchQuery) })
                    entries.isEmpty() -> SearchNoResults(state.searchQuery, onQuery)
                    else -> SearchResults(state.searchQuery, filtered, filter, { filter = it }, focused, { focusedId = it }, onQuery, firstResultFocus)
                }
            }
        }
    }
}

@Composable
private fun SearchNavigation(
    query: String,
    onQuery: (String) -> Unit,
    onHome: () -> Unit,
    onMovies: () -> Unit,
    onShows: () -> Unit,
    onMyList: () -> Unit,
    onProfile: () -> Unit,
    firstResultFocus: FocusRequester?,
) {
    Row(
        Modifier.offset(32.dp, 18.dp).width(1376.dp).height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC0E131E)).border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(painterResource(R.drawable.pairing_brand), null, Modifier.size(40.dp))
            Text("CHROMA", color = CatalogBright, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.1.sp)
        }
        Row(Modifier.height(40.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CatalogNavItem("Home", false, onHome)
            CatalogNavItem("Movies", true, onMovies)
            CatalogNavItem("TV Shows", false, onShows)
            CatalogNavItem("Watchlist", false, onMyList)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchInput(query, onQuery, firstResultFocus)
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
private fun SearchInput(query: String, onQuery: (String) -> Unit, firstResultFocus: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    val requester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { requester.requestFocus() }
    LaunchedEffect(query) {
        if (query != fieldValue.text) fieldValue = fieldValue.copy(text = query, selection = TextRange(query.length))
    }
    BasicTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            if (it.text != query) onQuery(it.text)
        },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = CatalogBright, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(CatalogBlue),
        modifier = Modifier.width(250.dp).height(40.dp).focusRequester(requester)
            .focusProperties { if (firstResultFocus != null) down = firstResultFocus }
            .onFocusChanged { focused = it.isFocused }
            .tvTextFieldNavigation(focusManager, fieldValue),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(if (focused) Color(0xFF202839) else CatalogSurface)
                    .border(if (focused) 2.dp else 1.dp, if (focused) CatalogBlue else Color(0xFF34425A), RoundedCornerShape(10.dp)).padding(horizontal = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                PairingIcon("search", if (focused) CatalogBright else CatalogQuiet, Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (fieldValue.text.isBlank()) Text("Search movies and shows", color = CatalogQuiet, fontSize = 11.sp)
                    field()
                }
                if (fieldValue.text.isNotBlank()) Text("×", color = CatalogQuiet, fontSize = 16.sp, modifier = Modifier.clickable { onQuery("") })
            }
        },
    )
}

@Composable
private fun SearchDiscover(recentSearches: List<String>, onQuery: (String) -> Unit) {
    Row(Modifier.offset(48.dp, 108.dp).width(1344.dp).height(660.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.width(768.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("What do you want to watch?", color = CatalogBright, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Press OK on the search field to use your TV keyboard. Results appear as you type.", color = CatalogQuiet, fontSize = 12.sp)
            Text("RECENT SEARCHES", color = Color(0xFFA8B3C6), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recentSearches.isEmpty()) Text("Your successful searches will appear here.", color = CatalogQuiet, fontSize = 11.sp)
                else recentSearches.forEach { label -> SearchSuggestion(label) { onQuery(label) } }
            }
            Text("POPULAR RIGHT NOW", color = Color(0xFFA8B3C6), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Sci-Fi", "Award winners", "Comedy").forEach { label -> SearchChip(label, false) { onQuery(label) } }
            }
        }
    }
}

@Composable
private fun SearchLoading(query: String) {
    Column(
        Modifier.offset(48.dp, 112.dp).width(1344.dp).height(650.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        PairingIcon("search", CatalogBlue, Modifier.size(38.dp))
        Spacer(Modifier.height(18.dp))
        Text("Searching for “$query”", color = CatalogBright, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text("Checking your local movie and TV library…", color = CatalogQuiet, fontSize = 12.sp)
    }
}

@Composable
private fun SearchFailure(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.offset(48.dp, 112.dp).width(1344.dp).height(650.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = CatalogBright, fontSize = 20.sp)
        Spacer(Modifier.height(18.dp))
        SearchChip("Retry search", true, onRetry)
    }
}

@Composable
private fun SearchNoResults(query: String, onQuery: (String) -> Unit) {
    Row(Modifier.offset(48.dp, 112.dp).width(1344.dp).height(650.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(
            Modifier.width(754.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(CatalogSurface), contentAlignment = Alignment.Center) {
                PairingIcon("search", Color(0xFFFF7A7A), Modifier.size(30.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("No matches for “$query”", color = CatalogBright, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Check the spelling or try a title, actor, director, or genre.", color = CatalogQuiet, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Sci-Fi", "Comedy", "TV Shows").forEach { label -> SearchChip(label, false) { onQuery(label) } }
            }
            Spacer(Modifier.height(18.dp))
            SearchChip("Clear search", true) { onQuery("") }
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    entries: List<SearchEntry>,
    filter: SearchResultType,
    onFilter: (SearchResultType) -> Unit,
    focused: SearchEntry?,
    onFocused: (String) -> Unit,
    onQuery: (String) -> Unit,
    firstResultFocus: FocusRequester,
) {
    Column(Modifier.offset(32.dp, 106.dp).width(1376.dp).height(672.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth().height(59.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("SEARCH RESULTS", color = Color(0xFFA8B3C6), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                Text("${entries.size} results for “$query”", color = CatalogBright, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchChip("All", filter == SearchResultType.All) { onFilter(SearchResultType.All) }
                SearchChip("Movies", filter == SearchResultType.Movies) { onFilter(SearchResultType.Movies) }
                SearchChip("TV Shows", filter == SearchResultType.Shows) { onFilter(SearchResultType.Shows) }
            }
        }
        LazyRow(Modifier.fillMaxWidth().height(340.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(entries, key = { _, entry -> entry.item.id }) { index, entry ->
                SearchResultCard(entry, focused?.item?.id == entry.item.id, if (index == 0) firstResultFocus else null) { onFocused(entry.item.id) }
            }
        }
        focused?.let { SearchFocusedPreview(it, onQuery) }
    }
}

@Composable
private fun SearchResultCard(entry: SearchEntry, selected: Boolean, requester: FocusRequester?, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    CatalogCardMetadata(entry.item, focused)
    Box(
        Modifier.width(224.dp).height(340.dp).zIndex(if (focused) 2f else 0f)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }.clickable(onClick = entry.item.onOpen),
    ) { CatalogCompactCard(entry.item, focused || selected) }
}

@Composable
private fun CatalogCardMetadata(item: CatalogItem, focused: Boolean) {
    val lookup by rememberUpdatedState(item.onHeld)
    LaunchedEffect(item.id, item.metadataKey) { item.onVisible() }
    LaunchedEffect(item.id, focused) {
        if (focused && lookup != null) {
            delay(MoviePreviewHoldMs)
            lookup?.invoke()
        }
    }
}

private const val MoviePreviewHoldMs = 5_000L

@Composable
private fun SearchFocusedPreview(entry: SearchEntry, onQuery: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(166.dp).clip(RoundedCornerShape(16.dp)).background(CatalogSurface)
            .border(1.dp, CatalogStroke, RoundedCornerShape(16.dp)).padding(22.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        CatalogArtwork(entry.item, Modifier.width(190.dp).height(122.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(entry.item.title, color = CatalogBright, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            CatalogMetadata(entry.item)
            Text(entry.item.overview ?: "Open this title to see more information.", color = CatalogQuiet, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(if (entry.type == SearchResultType.Movies) "MOVIE" else "TV SHOW", color = CatalogPeach, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            SearchChip("Open details", true, entry.item.onOpen)
            Text("Search related", color = CatalogQuiet, fontSize = 10.sp, modifier = Modifier.clickable { onQuery(entry.item.genre) })
        }
    }
}

@Composable
private fun SearchSuggestion(label: String, onClick: () -> Unit) {
    Row(
        Modifier.width(250.dp).height(34.dp).clip(RoundedCornerShape(9.dp)).background(CatalogSurface)
            .border(1.dp, CatalogStroke, RoundedCornerShape(9.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        PairingIcon("history", CatalogQuiet, Modifier.size(14.dp))
        Text(label, color = CatalogBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SearchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) CatalogPeach.copy(alpha = 0.14f) else if (focused) Color(0xFF263247) else CatalogSurface)
            .border(if (focused) 2.dp else 1.dp, if (focused) CatalogBlue else if (selected) CatalogPeach.copy(alpha = 0.55f) else CatalogStroke, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) Color(0xFFFFD09A) else CatalogBright, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun CatalogBrowseScreen(
    section: MediaSection,
    items: List<CatalogItem>,
    pagingLoading: Boolean,
    pagingError: Boolean,
    appendLoading: Boolean,
    appendError: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
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
                CatalogNavigation(section, onHome, onMovies, onShows, onMyList, onSearch, onProfile)
                Column(
                    Modifier.offset(32.dp, 94.dp).width(1376.dp).height(716.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth().height(if (categoryName == null) 40.dp else 58.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (categoryName != null) CatalogIconButton("back") { onSelectCategory(null) }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(categoryName ?: "All categories", color = CatalogBright, fontSize = if (categoryName == null) 28.sp else 24.sp, fontWeight = FontWeight.ExtraBold)
                                if (categoryName != null) Text("Browse this category. Press BACK to return to the full library.", color = CatalogQuiet, fontSize = 10.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CatalogCategorySelector(categoryName ?: "All categories", categories, selectedCategory, categoryOpen, { categoryOpen = !categoryOpen }) {
                                onSelectCategory(it)
                                categoryOpen = false
                            }
                        }
                    }
                    if (pagingLoading && items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading your library…", color = CatalogQuiet, fontSize = 14.sp) }
                    } else if (pagingError && items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CatalogCategoryOption("Loading failed — Retry", false, onClick = onRetry) }
                    } else if (items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No titles are available in this category.", color = CatalogQuiet, fontSize = 14.sp) }
                    } else {
                        CatalogRows(items, categories, selectedCategory, firstCardFocus, hasMore, appendLoading, appendError, onLoadMore, onRetry)
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
    Box(
        Modifier.offset(32.dp, 18.dp).width(1376.dp).height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC0E131E)).border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(painterResource(R.drawable.pairing_brand), null, Modifier.size(40.dp))
            Text("CHROMA", color = CatalogBright, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.1.sp)
        }
        Row(
            Modifier.align(Alignment.Center).width(417.dp).height(40.dp).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatalogNavItem("Home", section == MediaSection.Home, onHome)
            CatalogNavItem("Movies", section == MediaSection.Movies, onMovies)
            CatalogNavItem("TV Shows", section == MediaSection.Shows, onShows)
            CatalogNavItem("Watchlist", section == MediaSection.MyList, onMyList)
        }
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var searchFocused by remember { mutableStateOf(false) }
            val searchBackground by animateColorAsState(if (searchFocused) Color(0xFF18233A) else CatalogSurface, tween(140), label = "search background")
            val searchBorder by animateColorAsState(if (searchFocused) CatalogBlue else Color(0xFF34425A), tween(140), label = "search border")
            val searchBorderWidth by animateDpAsState(if (searchFocused) 2.dp else 1.dp, tween(140), label = "search border width")
            val searchIcon by animateColorAsState(if (searchFocused) Color(0xFFB9C7FF) else Color(0xFFAAB5C6), tween(140), label = "search icon")
            val searchText by animateColorAsState(if (searchFocused) Color.White else Color(0xFFC6CFDD), tween(140), label = "search text")
            Row(
                Modifier.width(274.dp).height(40.dp).clip(RoundedCornerShape(10.dp)).background(searchBackground)
                    .border(searchBorderWidth, searchBorder, RoundedCornerShape(10.dp))
                    .onFocusChanged { searchFocused = it.isFocused }.clickable(onClick = onSearch).padding(horizontal = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                PairingIcon("search", searchIcon, Modifier.size(18.dp))
                Text("Search", color = searchText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            var profileFocused by remember { mutableStateOf(false) }
            val profileBackground by animateColorAsState(if (profileFocused) Color(0xFF24325A) else Color(0xFF1A2340), tween(140), label = "profile background")
            val profileBorder by animateColorAsState(if (profileFocused) CatalogBlue else Color(0x777C9CFF), tween(140), label = "profile border")
            val profileBorderWidth by animateDpAsState(if (profileFocused) 2.dp else 1.dp, tween(140), label = "profile border width")
            val profileText by animateColorAsState(if (profileFocused) Color.White else Color(0xFFB9C7FF), tween(140), label = "profile text")
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(profileBackground)
                    .border(profileBorderWidth, profileBorder, RoundedCornerShape(12.dp))
                    .onFocusChanged { profileFocused = it.isFocused }.clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) { Text("OH", color = profileText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
private fun CatalogNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val background by animateColorAsState(if (focused) Color(0xFF18233A) else if (selected) CatalogPeach.copy(alpha = 0.09f) else Color.Transparent, tween(140), label = "nav background")
    val border by animateColorAsState(if (focused) CatalogBlue else CatalogPeach.copy(alpha = 0.4f), tween(140), label = "nav border")
    val borderWidth by animateDpAsState(if (focused) 2.dp else if (selected) 1.dp else 0.dp, tween(140), label = "nav border width")
    val textColor by animateColorAsState(if (focused) CatalogBright else if (selected) Color(0xFFFFD09A) else Color(0xFF909CAF), tween(140), label = "nav text")
    Box(
        Modifier.height(40.dp).clip(shape)
            .background(background)
            .border(borderWidth, border, shape)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 17.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = textColor, fontSize = 13.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold) }
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
        val background by animateColorAsState(if (focused || open) Color(0xFF18233A) else CatalogSurface, tween(140), label = "category background")
        val border by animateColorAsState(if (open) CatalogPeach else if (focused) CatalogBlue else Color(0xFF34425A), tween(140), label = "category border")
        val borderWidth by animateDpAsState(if (focused || open) 2.dp else 1.dp, tween(140), label = "category border width")
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(11.dp)).background(background)
                .border(borderWidth, border, RoundedCornerShape(11.dp))
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
private fun CatalogRows(items: List<CatalogItem>, categories: List<VodCategoryEntity>, selectedCategory: String?, firstFocus: FocusRequester, hasMore: Boolean, appendLoading: Boolean, appendError: Boolean, onLoadMore: () -> Unit, onRetry: () -> Unit) {
    val names = remember(categories) { categories.associate { it.providerId to it.name } }
    val groups = if (selectedCategory != null) {
        items.chunked(6).mapIndexed { index, row -> (if (index == 0) names[selectedCategory] ?: "SELECTED CATEGORY" else "MORE TO EXPLORE") to row }
    } else {
        items.groupBy { it.categoryId }.map { (id, row) -> (names[id] ?: "RECENTLY ADDED") to row }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(groups, key = { it.first + it.second.firstOrNull()?.id }) { (label, rowItems) ->
            var previewedCardId by remember { mutableStateOf<String?>(null) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(label.uppercase(Locale.US), color = Color(0xFFA8B3C6), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                LazyRow(
                    Modifier.fillMaxWidth().height(360.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(rowItems, key = { it.id }) { item ->
                        CatalogMovieCard(
                            item = item,
                            focusRequester = if (item.id == items.first().id) firstFocus else null,
                            dimmed = previewedCardId != null && previewedCardId != item.id,
                            onPreviewChanged = { showing ->
                                if (showing) previewedCardId = item.id else if (previewedCardId == item.id) previewedCardId = null
                            },
                        )
                    }
                }
            }
        }
        if (appendError) item { CatalogCategoryOption("More titles failed — Retry", false, onClick = onRetry) }
        else if (appendLoading) item { Text("Loading more titles…", color = CatalogQuiet) }
        else if (hasMore) item { CatalogCategoryOption("Load more titles", false, onClick = onLoadMore) }
    }
}

@Composable
internal fun CatalogMovieCard(
    item: CatalogItem,
    focusRequester: FocusRequester?,
    dimmed: Boolean,
    onPreviewChanged: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var trailerKey by remember(item.id, item.tmdbId) { mutableStateOf<String?>(null) }
    val lookup by rememberUpdatedState(item.onHeld)
    val gateway = remember { TmdbTrailerGateway(BuildConfig.TMDB_API_KEY) }
    val bringIntoView = remember { BringIntoViewRequester() }
    val width by animateDpAsState(if (focused) 420.dp else 224.dp, tween(180), label = "catalog card width")
    val expanded = focused && width == 420.dp
    val surroundingAlpha by animateFloatAsState(if (dimmed) 0.5f else 1f, tween(160), label = "surrounding card alpha")
    LaunchedEffect(item.id, item.metadataKey) { item.onVisible() }
    LaunchedEffect(expanded) {
        if (expanded) {
            withFrameNanos { } // Let the row measure the expanded card before scrolling.
            bringIntoView.bringIntoView()
        }
    }
    LaunchedEffect(focused, item.tmdbId, item.id) {
        trailerKey = null
        onPreviewChanged(false)
        if (focused) {
            // No network request or playback until focus remains uninterrupted for 5 seconds.
            delay(MoviePreviewHoldMs)
            val id = lookup?.invoke() ?: item.tmdbId
            if (id != null) {
                val key = gateway.trailerKey(item.trailerType, id)
                coroutineContext.ensureActive()
                if (key != null) {
                    trailerKey = key
                    onPreviewChanged(true)
                }
            }
        }
    }
    Box(
        Modifier.width(width).height(340.dp)
            .bringIntoViewRequester(bringIntoView)
            .graphicsLayer { alpha = surroundingAlpha }
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(onClick = item.onOpen),
    ) {
        if (focused) {
            CatalogDelayedPreview(
                item,
                Modifier.fillMaxSize().shadow(12.dp, RoundedCornerShape(17.dp), clip = false),
                trailerKey,
            )
        } else CatalogCompactCard(item, false)
    }
}

@Composable
private fun CatalogCompactCard(item: CatalogItem, focused: Boolean, savedBadge: Boolean = false) {
    val shape = RoundedCornerShape(14.dp)
    val borderWidth by animateDpAsState(if (focused) 2.dp else 1.dp, tween(160), label = "card border")
    val borderColor by animateColorAsState(if (focused) CatalogPeach else CatalogStroke, tween(160), label = "card border color")
    Box(
        Modifier.fillMaxSize().clip(shape).background(CatalogSurface).border(borderWidth, borderColor, shape)
    ) {
        CatalogArtwork(item, Modifier.fillMaxWidth().height(250.dp))
        CatalogGenreBadge(item.genre, Modifier.offset(12.dp, 12.dp))
        if (savedBadge) CatalogSavedBadge(Modifier.offset(184.dp, 12.dp))
        else CatalogRatingBadge(item.rating, Modifier.offset(164.dp, 12.dp))
        Column(Modifier.offset(16.dp, 268.dp).width(192.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.title, color = CatalogBright, fontSize = 15.sp, fontWeight = FontWeight(760), maxLines = 1, overflow = TextOverflow.Ellipsis)
            CatalogMetadata(item)
        }
    }
}

@Composable
private fun CatalogSavedBadge(modifier: Modifier) {
    Box(
        modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC090B10))
            .border(1.dp, CatalogPeach.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) { PairingIcon("bookmark-check", Color(0xFFFFD09A), Modifier.size(14.dp)) }
}

@Composable
private fun CatalogDelayedPreview(item: CatalogItem, modifier: Modifier, trailerKey: String?) {
    val shape = RoundedCornerShape(17.dp)
    Box(modifier.clip(shape).background(CatalogSurface).border(2.dp, CatalogPeach, shape)) {
        CatalogArtwork(item, Modifier.fillMaxWidth().height(218.dp))
        if (trailerKey != null) {
            // Dispose WebView immediately on focus loss to stop playback and release resources.
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = WebViewClient()
                        setBackgroundColor(android.graphics.Color.BLACK)
                        loadUrl("https://www.youtube-nocookie.com/embed/$trailerKey?autoplay=1&controls=0&playsinline=1&rel=0")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(218.dp),
                onRelease = { view -> view.stopLoading(); view.loadUrl("about:blank"); view.destroy() },
            )
        }
        CatalogGenreBadge(item.genre, Modifier.offset(14.dp, 14.dp))
        CatalogRatingBadge(item.rating, Modifier.offset(358.dp, 14.dp))
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
            Text(rating?.let { String.format(Locale.US, "%.1f", it) } ?: "—", color = CatalogBright, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
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
        Text(genre.uppercase(Locale.US), color = CatalogBright, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, maxLines = 1)
    }
}

@Composable
private fun CatalogMetadata(item: CatalogItem) {
    Text(listOfNotNull(item.year.takeIf { it.isNotBlank() }, item.runtime).joinToString("  •  "), color = CatalogQuiet, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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

