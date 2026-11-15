package com.oh.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.oh.tv.BuildConfig
import com.oh.tv.data.remote.MovieSubtitleMatcher
import com.oh.tv.data.remote.MovieSubtitle
import com.oh.tv.data.remote.OpenSubtitlesGateway
import com.oh.tv.data.remote.TmdbGateway
import com.oh.tv.data.remote.MovieIdentityLookup
import com.oh.tv.data.remote.SubtitleMatch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.net.Uri
import java.io.File
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.LazyPagingItems
import androidx.paging.LoadState
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.oh.tv.R
import com.oh.tv.data.local.LiveCategoryEntity
import com.oh.tv.data.local.LiveChannelEntity
import com.oh.tv.data.local.MovieEntity
import com.oh.tv.data.local.SeriesEntity
import com.oh.tv.data.local.SeriesEpisodeEntity
import com.oh.tv.data.local.PlaybackProgressEntity
import com.oh.tv.data.SyncScopes
import com.oh.tv.data.SyncState
import com.oh.tv.data.remote.AccountInput
import com.oh.tv.ui.components.FocusSurface
import com.oh.tv.ui.components.MetadataLine
import com.oh.tv.ui.components.OhButton
import com.oh.tv.ui.components.OhTopBar
import com.oh.tv.ui.components.Screen
import com.oh.tv.ui.components.StatusPanel
import com.oh.tv.ui.theme.ErrorRed
import com.oh.tv.ui.theme.FocusBlue
import com.oh.tv.ui.theme.Graphite
import com.oh.tv.ui.theme.Muted
import com.oh.tv.ui.theme.Surface
import com.oh.tv.ui.theme.SurfaceLifted
import com.oh.tv.ui.theme.WarmWhite
import kotlinx.coroutines.delay

@Composable
fun OhApp(viewModel: OhViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.liveCategories.collectAsStateWithLifecycle()
    val channels = viewModel.liveChannels.collectAsLazyPagingItems()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val seriesProgress by viewModel.seriesProgress.collectAsStateWithLifecycle()
    val favoriteMovies by viewModel.favoriteMovies.collectAsStateWithLifecycle()
    val favoriteSeries by viewModel.favoriteSeries.collectAsStateWithLifecycle()
    val continueMovies by viewModel.continueMovies.collectAsStateWithLifecycle()
    val catalogCounts by viewModel.catalogCounts.collectAsStateWithLifecycle()

    BackHandler(enabled = state.destination !in setOf(Destination.Loading, Destination.Onboarding, Destination.Hub)) {
        viewModel.navigate(NavigationPolicy.back(state.destination, state.settingsReturn, state.playerReturn))
    }

    when (state.destination) {
        Destination.Loading -> Screen { StatusPanel("OH", "Opening your local library…") }
        Destination.Onboarding -> OnboardingChoiceScreen(
            onPhone = viewModel::startPhonePairing,
            onManual = { viewModel.navigate(Destination.ManualSetup) },
        )
        Destination.PhonePairing -> PhonePairingScreen(
            state = state.pairing,
            onRetry = viewModel::startPhonePairing,
            onManual = { viewModel.cancelPhonePairing(Destination.ManualSetup) },
            onBack = { viewModel.cancelPhonePairing() },
        )
        Destination.ManualSetup -> ManualSetupScreen(
            busy = state.busy,
            message = state.message,
            onConnect = viewModel::connect,
            onBack = { viewModel.navigate(Destination.Onboarding) },
        )
        Destination.Sync -> LibraryPreparationScreen(
            state = state,
            onOpenReady = { viewModel.navigate(Destination.Hub) },
            onRetry = { viewModel.sync() },
        )
        Destination.Hub -> ExperienceChoiceScreen(
            counts = catalogCounts,
            onLive = { viewModel.navigate(Destination.LiveTv) },
            onMovies = { viewModel.navigate(Destination.Movies) },
            onSettings = { viewModel.openSettings(Destination.Hub) },
        )
        Destination.LiveTv -> LiveTvScreen(
            categories = categories,
            channels = channels,
            selectedCategoryId = state.selectedCategoryId,
            onCategory = viewModel::selectLiveCategory,
            onChannel = viewModel::playLive,
            onHome = { viewModel.navigate(Destination.Hub) },
            onSettings = { viewModel.openSettings(Destination.LiveTv) },
            message = state.message,
            busy = state.busy,
        )
        Destination.OnDemand -> OnDemandScreen(
            onMovies = { viewModel.navigate(Destination.Movies) },
            onShows = { viewModel.navigate(Destination.TvShows) },
            onHome = { viewModel.navigate(Destination.Hub) },
            onSettings = { viewModel.openSettings(Destination.OnDemand) },
            onSearch = { viewModel.navigate(Destination.Search) },
            onMyList = { viewModel.navigate(Destination.MyList) },
            continueMovies = continueMovies,
            onMovie = viewModel::openMovie,
            onMovieVisible = viewModel::loadMovieMetadata,
        )
        Destination.Movies -> MovieCatalogScreen(viewModel, viewModel::openMovie)
        Destination.TvShows -> SeriesCatalogScreen(viewModel, viewModel::openSeries)
        Destination.MovieDetails -> state.selectedMovie?.let { MovieDetailsScreen(it, state.favorite, state.message, viewModel::playMovie, viewModel::toggleFavorite) { viewModel.navigate(Destination.Movies) } }
            ?: StatusPanel("Movie unavailable", "The selected movie is no longer in the local catalog.")
        Destination.SeriesDetails -> state.selectedSeries?.let { SeriesDetailsScreen(it, episodes, seriesProgress, state, viewModel::playEpisode, viewModel::toggleFavorite) { viewModel.navigate(Destination.TvShows) } }
            ?: Screen { StatusPanel("TV show unavailable", "The selected TV show is no longer in the local catalog.") }
        Destination.Search -> SearchScreen(state, viewModel::search, viewModel::openMovie, viewModel::openSeries, viewModel::loadMovieMetadata, viewModel::loadSeriesMetadata) { viewModel.navigate(Destination.Movies) }
        Destination.MyList -> MyListScreen(favoriteMovies, favoriteSeries, viewModel::openMovie, viewModel::openSeries, viewModel::loadMovieMetadata, viewModel::loadSeriesMetadata) { viewModel.navigate(Destination.Movies) }
        Destination.Settings -> SettingsScreen(state, viewModel::closeSettings, viewModel::sync, viewModel::removeAccount)
        Destination.Player -> state.playbackUrl?.let { PlayerScreen(it, state.playbackTitle.orEmpty(), state.playbackStartMs, viewModel::savePlaybackProgress, if (state.playbackType == PlaybackType.Movie) state.selectedMovie?.let { movie -> MovieSubtitle(movie.name, movie.releaseDate?.take(4)?.toIntOrNull(), tmdbId = movie.tmdbId) } else null) }
            ?: Screen { StatusPanel("Playback unavailable", "The stream URL could not be resolved.") }
    }
}

@Composable
private fun OnboardingScreen(busy: Boolean, message: String?, connect: (AccountInput) -> Unit) {
    var accountName by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Screen {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(38.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                androidx.compose.foundation.Image(painterResource(R.drawable.oh_logo), null, Modifier.size(50.dp))
                Text("Connect your service", color = WarmWhite, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("Enter the Xtream information provided by your TV service.", color = Muted, fontSize = 13.sp)
                Text("Your information stays on this TV.", color = FocusBlue, fontSize = 12.sp)
            }
            Column(Modifier.weight(1f).fillMaxWidth(0.86f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OhTextField(accountName, { accountName = it }, "Account name (optional)")
                OhTextField(server, { server = it }, "Server URL")
                OhTextField(username, { username = it }, "Username")
                OhTextField(password, { password = it }, "Password", password = true)
                if (message != null) Text(message, color = ErrorRed, fontSize = 15.sp)
                OhButton(if (busy) "Connecting…" else "Connect", {
                    if (!busy) connect(AccountInput(accountName, server, username, password))
                }, Modifier.fillMaxWidth(), primary = true)
            }
        }
    }
}

@Composable
private fun OhTextField(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = WarmWhite, fontSize = 14.sp),
        cursorBrush = SolidColor(FocusBlue),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        decorationBox = { input -> if (value.isBlank()) Text(label, color = Muted, fontSize = 14.sp) else input() },
        modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp)).background(Surface).padding(horizontal = 11.dp, vertical = 9.dp),
    )
}

@Composable
private fun SyncScreen(state: OhUiState, retry: () -> Unit) {
    Screen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Preparing OH", color = WarmWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(11.dp))
            Column(Modifier.width(430.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SyncScopes.userVisible.forEach { scope ->
                    val progress = state.syncProgress.firstOrNull { it.scope == scope }
                    Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Surface).padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(scope, color = WarmWhite)
                            Text(
                                when (progress?.state) {
                                    SyncState.Running -> "Syncing • ${progress.processed}"
                                    SyncState.Ready -> "Ready • ${progress.processed}"
                                    SyncState.Empty -> "Empty"
                                    SyncState.Stale -> "Cached • ${progress.processed}"
                                    SyncState.Failed -> "Failed"
                                    else -> "Waiting"
                                },
                                color = when (progress?.state) {
                                    SyncState.Failed -> ErrorRed
                                    SyncState.Ready, SyncState.Stale -> FocusBlue
                                    else -> Muted
                                },
                            )
                        }
                        progress?.failure?.let { Text(it, color = ErrorRed, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (state.message != null) {
                    Text(state.message, color = ErrorRed)
                    OhButton("Try again", retry, primary = true)
                }
            }
        }
    }
}

@Composable
private fun HubScreen(onLive: () -> Unit, onDemand: () -> Unit, onSettings: () -> Unit) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
    Screen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OhTopBar(onSettings = onSettings)
            Text("What would you like to watch?", color = WarmWhite, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text("Use ◀  ▶ to choose, then press OK", color = FocusBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HubCard("Live TV", "Channels, guide and what’s on now", "▦  ⚽  ◇", onLive, Modifier.weight(1f), initialFocus)
                HubCard("Movies & TV Shows", "Your library, favorites and continue watching", "◉  ✦  ▷", onDemand, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HubCard(title: String, subtitle: String, symbols: String, click: () -> Unit, modifier: Modifier, focusRequester: FocusRequester? = null) {
    FocusSurface(click, modifier.fillMaxHeight(0.66f), Surface, Color(0xFF2A3446), focusRequester = focusRequester) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF27364F)), contentAlignment = Alignment.Center) {
                Text(symbols, color = WarmWhite, fontSize = 28.sp)
            }
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = WarmWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 11.sp)
                Text("Press OK to open", color = FocusBlue, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LiveTvScreen(
    categories: List<LiveCategoryEntity>, channels: LazyPagingItems<LiveChannelEntity>, selectedCategoryId: String?,
    onCategory: (String?) -> Unit, onChannel: (LiveChannelEntity) -> Unit, onHome: () -> Unit, onSettings: () -> Unit,
    message: String?, busy: Boolean,
) {
    Screen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            OhTopBar("Live TV", onHome, onSettings)
            if (message != null) Text(message, color = ErrorRed)
            else if (busy) Text("Opening channel…", color = Muted)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(Modifier.width(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { MenuItem("All Channels", selectedCategoryId == null) { onCategory(null) } }
                    items(categories, key = { it.providerId }) { MenuItem(it.name, selectedCategoryId == it.providerId) { onCategory(it.providerId) } }
                }
                LazyColumn(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        count = channels.itemCount,
                        key = { index -> channels.peek(index)?.providerId ?: "channel-$index" },
                    ) { index -> channels[index]?.let { channel -> ChannelRow(channel) { onChannel(channel) } } }
                    when (val append = channels.loadState.append) {
                        is LoadState.Loading -> item { MetadataLine("Loading more channels…") }
                        is LoadState.Error -> item { MetadataLine("More channels could not be loaded") }
                        else -> Unit
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(Surface), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Select a channel", color = WarmWhite, fontSize = 19.sp)
                        MetadataLine("Current and next program information appears here")
                    }
                }
            }
        }
    }
}

@Composable private fun MenuItem(label: String, selected: Boolean, click: () -> Unit) {
    FocusSurface(click, Modifier.fillMaxWidth(), if (selected) FocusBlue else Surface, SurfaceLifted, 14.dp) {
        Text(label, color = WarmWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun ChannelRow(channel: LiveChannelEntity, click: () -> Unit) {
    FocusSurface(click, Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(channel.number.toString(), color = Muted, modifier = Modifier.width(38.dp))
            AsyncImage(channel.iconUrl, null, Modifier.size(42.dp), contentScale = ContentScale.Fit)
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = WarmWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MetadataLine("Live")
            }
        }
    }
}

@Composable
private fun OnDemandScreen(
    onMovies: () -> Unit, onShows: () -> Unit, onHome: () -> Unit, onSettings: () -> Unit,
    onSearch: () -> Unit, onMyList: () -> Unit, continueMovies: List<MovieEntity>, onMovie: (MovieEntity) -> Unit,
    onMovieVisible: (MovieEntity) -> Unit,
) {
    Screen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            OhTopBar("Movies & TV Shows", onHome, onSettings)
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                OhButton("Movies", onMovies, primary = true)
                OhButton("TV Shows", onShows)
                OhButton("Search", onSearch)
                OhButton("My List", onMyList)
            }
            Text("Continue Watching", color = WarmWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (continueMovies.isEmpty()) EmptyShelf("Your unfinished movies will appear here")
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(continueMovies, key = { it.providerId }) { movie ->
                    LaunchedEffect(movie.accountId, movie.providerId, movie.metadataUpdatedAt) { onMovieVisible(movie) }
                    MovieShelfCard(movie) { onMovie(movie) }
                }
            }
            Text("Recently Added", color = WarmWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            EmptyShelf("Open Movies or TV Shows to browse the complete local catalog")
        }
    }
}

@Composable private fun EmptyShelf(message: String) {
    Box(Modifier.fillMaxWidth().height(96.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(Surface), contentAlignment = Alignment.Center) {
        Text(message, color = Muted)
    }
}

@Composable private fun MovieShelfCard(movie: MovieEntity, click: () -> Unit) {
    FocusSurface(click, Modifier.width(120.dp).height(88.dp), Surface, SurfaceLifted) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(movie.providerIconUrl, movie.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(movie.name, color = WarmWhite, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color(0xD917191D)).padding(8.dp))
        }
    }
}

@Composable
private fun MovieCatalogScreen(viewModel: OhViewModel, select: (MovieEntity) -> Unit) {
    val movies = viewModel.movies.collectAsLazyPagingItems()
    val selectedGenre by viewModel.selectedMovieGenre.collectAsStateWithLifecycle()
    val genreCounts by viewModel.movieGenreCounts.collectAsStateWithLifecycle()
    val counts by viewModel.catalogCounts.collectAsStateWithLifecycle()
    var genreOpen by remember { mutableStateOf(false) }
    val genreFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { genreFocus.requestFocus() }
    BrowseScreen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MediaNavigationBar(
                active = MediaSection.Movies,
                onHome = { viewModel.navigate(Destination.Hub) },
                onMovies = { genreOpen = false },
                onShows = { viewModel.navigate(Destination.TvShows) },
                onMyList = { viewModel.navigate(Destination.MyList) },
                onSearch = { viewModel.navigate(Destination.Search) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Movies", color = WarmWhite, fontSize = 20.sp)
                MovieGenreMenu(
                    open = genreOpen,
                    selected = selectedGenre,
                    totalMovies = counts.movies,
                    counts = genreCounts,
                    onToggle = { genreOpen = !genreOpen },
                    onSelect = { genre -> viewModel.selectMovieGenre(genre); genreOpen = false },
                    onDismiss = { genreOpen = false },
                    focusRequester = genreFocus,
                )
            }
            LazyVerticalGrid(GridCells.Adaptive(118.dp), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(movies.itemCount) { index -> movies[index]?.let { movie ->
                    LaunchedEffect(movie.accountId, movie.providerId, movie.metadataUpdatedAt) { viewModel.loadMovieMetadata(movie) }
                    MovieCard(movie) { select(movie) }
                } }
            }
        }
    }
}

@Composable
private fun SeriesCatalogScreen(viewModel: OhViewModel, select: (SeriesEntity) -> Unit) {
    val shows = viewModel.series.collectAsLazyPagingItems()
    val navFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { navFocus.requestFocus() }
    BrowseScreen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MediaNavigationBar(
                active = MediaSection.Shows,
                onHome = { viewModel.navigate(Destination.Hub) },
                onMovies = { viewModel.navigate(Destination.Movies) },
                onShows = {},
                onMyList = { viewModel.navigate(Destination.MyList) },
                onSearch = { viewModel.navigate(Destination.Search) },
                initialFocus = navFocus,
            )
            Text("TV Shows", color = WarmWhite, fontSize = 20.sp)
            LazyVerticalGrid(GridCells.Adaptive(118.dp), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(shows.itemCount) { index -> shows[index]?.let { show ->
                    LaunchedEffect(show.accountId, show.providerId, show.metadataUpdatedAt) { viewModel.loadSeriesMetadata(show) }
                    SeriesCard(show) { select(show) }
                } }
            }
        }
    }
}

@Composable private fun MovieCard(movie: MovieEntity, click: () -> Unit) {
    FocusSurface(click, Modifier.aspectRatio(0.68f), Surface, SurfaceLifted) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(movie.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: movie.providerIconUrl, movie.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color(0xD917191D)).padding(10.dp)) {
                Text(movie.name, color = WarmWhite, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            }
        }
    }
}

@Composable private fun SeriesCard(show: SeriesEntity, click: () -> Unit) {
    FocusSurface(click, Modifier.aspectRatio(0.68f), Surface, SurfaceLifted) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(show.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: show.providerIconUrl, show.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color(0xD917191D)).padding(10.dp)) {
                Text(show.name, color = WarmWhite, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MovieDetailsScreen(movie: MovieEntity, favorite: Boolean, message: String?, play: () -> Unit, toggleFavorite: () -> Unit, back: () -> Unit) {
    Screen {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(movie.backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.28f)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                OhTopBar(onHome = back)
                Spacer(Modifier.weight(1f))
                Text(movie.name, color = WarmWhite, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                MetadataLine(listOfNotNull(
                    movie.releaseDate?.take(4),
                    movie.runtimeMinutes?.let { "${it} min" },
                    movie.genres,
                    movie.tmdbRating?.let { "TMDB %.1f".format(it) }
                        ?: movie.providerRating?.let { "Rating %.1f".format(it) },
                ).joinToString("  •  "))
                Text(movie.overview ?: "Additional information is unavailable. You can still play this movie.", color = WarmWhite, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth(0.50f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (message != null) Text(message, color = ErrorRed)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { OhButton("▶  Play", play, primary = true); OhButton(if (favorite) "✓  In My List" else "＋  My List", toggleFavorite) }
            }
        }
    }
}

@Composable
private fun SeriesDetailsScreen(
    series: SeriesEntity,
    episodes: List<SeriesEpisodeEntity>,
    progressItems: List<PlaybackProgressEntity>,
    state: OhUiState,
    play: (SeriesEpisodeEntity) -> Unit,
    toggleFavorite: () -> Unit,
    back: () -> Unit,
) {
    var season by remember(series.providerId, episodes) { mutableStateOf(episodes.minOfOrNull { it.seasonNumber } ?: 1) }
    val seasons = episodes.map { it.seasonNumber }.distinct().sorted()
    val progressByEpisode = progressItems.associateBy { it.contentId }
    val continueEpisode = progressItems.firstOrNull { !it.completed }?.let { progress -> episodes.firstOrNull { it.providerId == progress.contentId } }
        ?: episodes.firstOrNull()
    Screen {
        Box(Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(Color.Black)) {
            AsyncImage(
                series.backdropPath?.let { "https://image.tmdb.org/t/p/original$it" } ?: series.providerIconUrl,
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.42f,
            )
            AsyncImage(
                series.posterPath?.let { "https://image.tmdb.org/t/p/w780$it" } ?: series.providerIconUrl,
                series.name,
                Modifier.fillMaxHeight().fillMaxWidth(0.41f),
                contentScale = ContentScale.Crop,
                alpha = 0.9f,
            )
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x22000000), Color(0xB8000000), Color(0xF0000000)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22000000), Color(0x55000000), Color.Black))))

            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("OH", color = WarmWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    OhButton("×", back, compact = true)
                }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(0.40f).fillMaxHeight().padding(start = 52.dp, bottom = 34.dp), verticalArrangement = Arrangement.Bottom) {
                        Text(series.name, color = WarmWhite, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(7.dp))
                        MetadataLine(listOfNotNull(
                            series.releaseDate?.take(4),
                            series.genres,
                            seasons.takeIf { it.isNotEmpty() }?.let { "${it.size} seasons" },
                            series.tmdbRating?.let { "TMDB %.1f".format(it) }
                                ?: series.providerRating?.let { "Rating %.1f".format(it) },
                        ).joinToString("  •  "))
                        Spacer(Modifier.height(8.dp))
                        Text(series.overview ?: "Show information is provided by your service.", color = WarmWhite, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        continueEpisode?.let { episode ->
                            val progress = progressByEpisode[episode.providerId]
                            progress?.let { EpisodeProgressBar(it, Modifier.fillMaxWidth(0.72f)) }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OhButton(if (progress != null && progress.positionMs > 5_000) "▶  Continue" else "▶  Start watching", { play(episode) }, primary = true)
                                OhButton(if (state.favorite) "✓  In My List" else "＋  My List", toggleFavorite)
                            }
                        }
                    }

                    Row(Modifier.weight(0.60f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("SEASON $season", color = WarmWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            when {
                                state.busy -> StatusPanel("Loading episodes", "Reading this show from your service…")
                                state.message != null -> StatusPanel("Episodes unavailable", state.message)
                                episodes.isEmpty() -> StatusPanel("No episodes", "The provider returned no playable episodes for this show.")
                                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(episodes.filter { it.seasonNumber == season }, key = { it.providerId }) { episode ->
                                        EpisodeCard(episode, progressByEpisode[episode.providerId]) { play(episode) }
                                    }
                                }
                            }
                        }
                        Column(Modifier.width(34.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            seasons.asReversed().forEach { number ->
                                FocusSurface({ season = number }, Modifier.size(30.dp), if (number == season) FocusBlue else Color.Transparent, FocusBlue) {
                                    Text(number.toString(), color = WarmWhite, fontSize = 11.sp, fontWeight = if (number == season) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: SeriesEpisodeEntity, progress: PlaybackProgressEntity?, play: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val cardColor = if (focused) Color(0xFFF5F5F3) else Color(0xD915171A)
    val primary = if (focused) Color(0xFF17191D) else WarmWhite
    val secondary = if (focused) Color(0xFF747A82) else Muted
    Row(
        Modifier.fillMaxWidth().height(72.dp)
            .clip(shape)
            .background(cardColor)
            .border(if (focused) 2.dp else 0.dp, if (focused) WarmWhite else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = play)
            .focusable()
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Box(Modifier.width(106.dp).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(Color(0xFF242830))) {
                AsyncImage(episode.posterUrl, episode.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Text("${episode.episodeNumber}", color = WarmWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).background(Color(0xB8000000)).padding(horizontal = 5.dp, vertical = 2.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(episode.title, color = primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(progress?.let { remainingLabel(it) } ?: episode.duration.orEmpty(), color = secondary, fontSize = 9.sp)
                }
                Text(episode.overview ?: "Episode ${episode.episodeNumber}", color = secondary, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (progress != null) EpisodeProgressBar(progress)
            }
    }
}

@Composable
private fun EpisodeProgressBar(progress: PlaybackProgressEntity, modifier: Modifier = Modifier) {
    val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth().height(3.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(Color(0xFF49505A))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(FocusBlue))
    }
}

private fun remainingLabel(progress: PlaybackProgressEntity): String {
    if (progress.completed) return "Watched"
    val minutes = ((progress.durationMs - progress.positionMs).coerceAtLeast(0) / 60_000)
    return if (minutes > 0) "${minutes}m left" else "Resume"
}

@Composable
private fun SearchScreen(
    state: OhUiState,
    search: (String) -> Unit,
    openMovie: (MovieEntity) -> Unit,
    openSeries: (SeriesEntity) -> Unit,
    loadMovie: (MovieEntity) -> Unit,
    loadSeries: (SeriesEntity) -> Unit,
    back: () -> Unit,
) {
    Screen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            OhTopBar("Search", back)
            OhTextField(state.searchQuery, search, "Search movies and TV shows")
            if (state.searchQuery.isNotBlank() && state.searchMovies.isEmpty() && state.searchSeries.isEmpty()) {
                EmptyShelf("No local results")
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.searchMovies, key = { "m:${it.providerId}" }) { movie ->
                    LaunchedEffect(movie.accountId, movie.providerId, movie.metadataUpdatedAt) { loadMovie(movie) }
                    MovieShelfCard(movie) { openMovie(movie) }
                }
                items(state.searchSeries, key = { "s:${it.providerId}" }) { show ->
                    LaunchedEffect(show.accountId, show.providerId, show.metadataUpdatedAt) { loadSeries(show) }
                    FocusSurface({ openSeries(show) }, Modifier.width(120.dp).height(88.dp), Surface, SurfaceLifted) {
                        Text(show.name, color = WarmWhite, modifier = Modifier.padding(12.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun MyListScreen(
    movies: List<MovieEntity>,
    series: List<SeriesEntity>,
    openMovie: (MovieEntity) -> Unit,
    openSeries: (SeriesEntity) -> Unit,
    loadMovie: (MovieEntity) -> Unit,
    loadSeries: (SeriesEntity) -> Unit,
    back: () -> Unit,
) {
    Screen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            OhTopBar("My List", back)
            if (movies.isEmpty() && series.isEmpty()) EmptyShelf("Add a movie or TV show to see it here")
            else LazyVerticalGrid(GridCells.Adaptive(118.dp), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(movies, key = { "m:${it.providerId}" }) { movie ->
                    LaunchedEffect(movie.accountId, movie.providerId, movie.metadataUpdatedAt) { loadMovie(movie) }
                    MovieCard(movie) { openMovie(movie) }
                }
                items(series, key = { "s:${it.providerId}" }) { show ->
                    LaunchedEffect(show.accountId, show.providerId, show.metadataUpdatedAt) { loadSeries(show) }
                    SeriesCard(show) { openSeries(show) }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: OhUiState, back: () -> Unit, sync: () -> Unit, remove: () -> Unit) {
    Screen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            OhTopBar("Settings", back)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.width(215.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Account", "Synchronization", "Playback", "Audio & Subtitles", "Parental Controls", "Live TV", "Storage", "Diagnostics", "Startup", "About & Credits").forEachIndexed { index, name ->
                        MenuItem(name, index == 0) { }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingRow("Subscription status", state.account?.status ?: "Unavailable")
                    SettingRow("Expires", state.account?.expiresAtEpochSeconds?.let { java.time.Instant.ofEpochSecond(it).toString().take(10) } ?: "Unknown")
                    SettingRow("Server", state.account?.baseUrl?.substringAfter("://") ?: "Unavailable")
                    OhButton("Refresh content", sync, Modifier.fillMaxWidth(), primary = true)
                    OhButton("Remove account", remove, Modifier.fillMaxWidth())
                    Text("This product can use TMDB data but is not endorsed or certified by TMDB.", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable private fun SettingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Surface).padding(horizontal = 13.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = WarmWhite); Text(value, color = Muted)
    }
}

@Composable
private fun PlayerScreen(url: String, title: String, startPositionMs: Long, saveProgress: (Long, Long) -> Unit, subtitleMovie: MovieSubtitle? = null) {
    val context = LocalContext.current
    var error by remember(url) { mutableStateOf<String?>(null) }
    var buffering by remember(url) { mutableStateOf(true) }
    val subtitleApi = remember { OpenSubtitlesGateway(BuildConfig.OPENSUBTITLES_API_KEY) }
    val tmdbApi = remember { TmdbGateway(BuildConfig.TMDB_API_KEY) }
    val matcher = remember { MovieSubtitleMatcher(subtitleApi, object : MovieIdentityLookup {
        override suspend fun resolve(movie: MovieSubtitle) = tmdbApi.resolveSubtitleMovie(movie)
    }) }
    val coroutineScope = rememberCoroutineScope()
    var subtitleResults by remember(url) { mutableStateOf<List<SubtitleMatch>>(emptyList()) }
    var subtitleMessage by remember(url) { mutableStateOf<String?>(null) }
    var subtitleBusy by remember(url) { mutableStateOf(false) }
    var showSubtitleSearch by remember(url) { mutableStateOf(false) }
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            if (startPositionMs > 0) seekTo(startPositionMs)
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(10_000)
            saveProgress(exoPlayer.currentPosition.coerceAtLeast(0), exoPlayer.duration.coerceAtLeast(0))
        }
    }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
            override fun onPlayerError(playbackError: PlaybackException) {
                buffering = false
                error = "OH could not play this stream. Check the service or try again."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            saveProgress(exoPlayer.currentPosition.coerceAtLeast(0), exoPlayer.duration.coerceAtLeast(0))
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = true; keepScreenOn = true } }, modifier = Modifier.fillMaxSize())
        if (subtitleMovie != null) {
            Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
                OhButton("Online subtitles", { showSubtitleSearch = !showSubtitleSearch })
                if (showSubtitleSearch) {
                    Column(Modifier.background(Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OhButton(if (subtitleBusy) "Searching…" else "Search Arabic / English", {
                            if (!subtitleBusy) coroutineScope.launch {
                                subtitleBusy = true
                                subtitleMessage = null
                                try {
                                    subtitleResults = matcher.find(subtitleMovie, listOf("ar", "en"))
                                    if (subtitleResults.isEmpty()) subtitleMessage = "No subtitles found"
                                } catch (_: Exception) { subtitleMessage = "Subtitle search failed" }
                                finally { subtitleBusy = false }
                            }
                        })
                        subtitleResults.take(12).forEach { subtitle ->
                            OhButton("${subtitle.language} · ${subtitle.release ?: "Subtitle"}", {
                                if (!subtitleBusy) coroutineScope.launch {
                                    subtitleBusy = true
                                    try {
                                        val bytes = subtitleApi.download(subtitle.id)
                                        val file = withContext(Dispatchers.IO) {
                                            val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
                                            File(dir, "${subtitle.id}.srt").apply { writeBytes(bytes) }
                                        }
                                        val position = exoPlayer.currentPosition
                                        val media = MediaItem.Builder().setUri(url)
                                            .setSubtitleConfigurations(listOf(
                                                MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                                                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                                                    .setLanguage(subtitle.language)
                                                    .setSelectionFlags(1)
                                                    .build()
                                            )).build()
                                        exoPlayer.setMediaItem(media, position)
                                        exoPlayer.prepare()
                                        exoPlayer.play()
                                        showSubtitleSearch = false
                                    } catch (_: Exception) { subtitleMessage = "Subtitle download failed" }
                                    finally { subtitleBusy = false }
                                }
                            })
                        }
                        subtitleMessage?.let { Text(it, color = Muted) }
                    }
                }
            }
        }
        if (buffering && error == null) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                Text("Loading ${title.ifBlank { "stream" }}…", color = WarmWhite, fontSize = 16.sp)
            }
        }
        error?.let { message ->
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Playback unavailable", color = WarmWhite, fontSize = 23.sp)
                    Text(message, color = Muted)
                    OhButton("Try again", {
                        error = null
                        buffering = true
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }, primary = true)
                }
            }
        }
    }
}
