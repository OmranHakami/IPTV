package com.oh.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.oh.tv.data.OhRepository
import com.oh.tv.data.CatalogCounts
import com.oh.tv.data.BrowseMovieGenres
import com.oh.tv.data.SyncProgress
import com.oh.tv.data.local.AccountEntity
import com.oh.tv.data.local.LiveCategoryEntity
import com.oh.tv.data.local.LiveChannelEntity
import com.oh.tv.data.local.VodCategoryEntity
import com.oh.tv.data.local.MovieEntity
import com.oh.tv.data.local.SeriesEpisodeEntity
import com.oh.tv.data.local.SeriesEntity
import com.oh.tv.data.local.PlaybackProgressEntity
import com.oh.tv.data.remote.AccountInput
import com.oh.tv.data.remote.LocalPairingServer
import com.oh.tv.security.PairingCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class Destination { Loading, Onboarding, PhonePairing, ManualSetup, Sync, Hub, LiveTv, OnDemand, Movies, TvShows, MovieDetails, SeriesDetails, Search, MyList, Settings, Player }
enum class PlaybackType { Live, Movie, Episode }
enum class PairingPhase { Idle, Creating, Waiting, Received, Unavailable, Expired, Failed }

data class PairingUiState(
    val phase: PairingPhase = PairingPhase.Idle,
    val code: String = "",
    val linkUrl: String = "",
    val expiresAtEpochMs: Long = 0,
    val message: String? = null,
)

data class OhUiState(
    val destination: Destination = Destination.Loading,
    val account: AccountEntity? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val syncProgress: List<SyncProgress> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedMovie: MovieEntity? = null,
    val selectedSeries: SeriesEntity? = null,
    val selectedEpisode: SeriesEpisodeEntity? = null,
    val playbackUrl: String? = null,
    val playbackType: PlaybackType? = null,
    val playbackContentId: String? = null,
    val playbackTitle: String? = null,
    val playbackStartMs: Long = 0,
    val playerReturn: Destination = Destination.Hub,
    val favorite: Boolean = false,
    val searchQuery: String = "",
    val searchMovies: List<MovieEntity> = emptyList(),
    val searchSeries: List<SeriesEntity> = emptyList(),
    val settingsReturn: Destination = Destination.Hub,
    val pairing: PairingUiState = PairingUiState(),
)

class OhViewModel(
    private val repository: OhRepository,
    private val localPairingServer: LocalPairingServer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OhUiState())
    val state: StateFlow<OhUiState> = mutableState
    private var searchJob: Job? = null
    private val metadataRequests = mutableSetOf<String>()
    private val metadataSemaphore = Semaphore(4)
    private var pairingJob: Job? = null

    private val selectedMovieCategoryState = MutableStateFlow<String?>(null)
    private val selectedSeriesCategoryState = MutableStateFlow<String?>(null)
    val selectedMovieCategory: StateFlow<String?> = selectedMovieCategoryState
    val selectedSeriesCategory: StateFlow<String?> = selectedSeriesCategoryState
    val movieCategories: StateFlow<List<VodCategoryEntity>> = repository.activeAccount.filterNotNull()
        .flatMapLatest { repository.vodCategories(it.id, "movie") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val seriesCategories: StateFlow<List<VodCategoryEntity>> = repository.activeAccount.filterNotNull()
        .flatMapLatest { repository.vodCategories(it.id, "series") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val movies: Flow<PagingData<MovieEntity>> = repository.activeAccount.filterNotNull()
        .combine(selectedMovieCategoryState) { account, category -> account.id to category }
        .flatMapLatest { (accountId, category) -> repository.movies(accountId, category) }.cachedIn(viewModelScope)
    val series: Flow<PagingData<SeriesEntity>> = repository.activeAccount.filterNotNull()
        .combine(selectedSeriesCategoryState) { account, category -> account.id to category }
        .flatMapLatest { (accountId, category) -> repository.series(accountId, category) }.cachedIn(viewModelScope)

    val liveCategories: StateFlow<List<LiveCategoryEntity>> = repository.activeAccount.filterNotNull()
        .flatMapLatest { repository.liveCategories(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val liveChannels: Flow<PagingData<LiveChannelEntity>> = repository.activeAccount.filterNotNull()
        .combine(state.map { it.selectedCategoryId }.distinctUntilChanged()) { account, categoryId -> account.id to categoryId }
        .flatMapLatest { (accountId, categoryId) -> repository.liveChannels(accountId, categoryId) }
        .cachedIn(viewModelScope)

    val episodes: StateFlow<List<SeriesEpisodeEntity>> = state
        .flatMapLatest { ui ->
            val account = ui.account
            val series = ui.selectedSeries
            if (account == null || series == null) flowOf(emptyList()) else repository.episodes(account.id, series.providerId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val seriesProgress: StateFlow<List<PlaybackProgressEntity>> = state
        .flatMapLatest { ui ->
            val account = ui.account
            val series = ui.selectedSeries
            if (account == null || series == null) flowOf(emptyList()) else repository.seriesProgress(account.id, series.providerId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteMovies = repository.activeAccount.filterNotNull().flatMapLatest { repository.favoriteMovies(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favoriteSeries = repository.activeAccount.filterNotNull().flatMapLatest { repository.favoriteSeries(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val continueMovies = repository.activeAccount.filterNotNull().flatMapLatest { repository.continueMovies(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val catalogCounts: StateFlow<CatalogCounts> = repository.activeAccount
        .flatMapLatest { account -> if (account == null) flowOf(CatalogCounts()) else repository.catalogCounts(account.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogCounts())
    val movieGenreCounts: StateFlow<Map<String, Int>> = repository.activeAccount
        .flatMapLatest { account ->
            if (account == null) flowOf(BrowseMovieGenres.associateWith { 0 }) else repository.movieGenreCounts(account.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseMovieGenres.associateWith { 0 })

    init {
        viewModelScope.launch {
            repository.activeAccount.collect { account ->
                mutableState.value = mutableState.value.copy(
                    account = account,
                    destination = when {
                        mutableState.value.destination != Destination.Loading -> mutableState.value.destination
                        account == null -> Destination.Onboarding
                        else -> Destination.Hub
                    },
                )
            }
        }
    }

    fun connect(input: AccountInput) {
        if (mutableState.value.busy) return
        val fromPhonePairing = mutableState.value.destination == Destination.PhonePairing
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            repository.connect(input).fold(
                onSuccess = { account ->
                    mutableState.value = mutableState.value.copy(
                        account = account,
                        destination = Destination.Sync,
                        busy = false,
                        syncProgress = emptyList(),
                    )
                    sync(account)
                },
                onFailure = {
                    val safeMessage = "OH could not connect. Check the server URL, username and password."
                    mutableState.value = if (fromPhonePairing) {
                        mutableState.value.copy(
                            busy = false,
                            pairing = mutableState.value.pairing.copy(phase = PairingPhase.Failed, message = safeMessage),
                        )
                    } else {
                        mutableState.value.copy(busy = false, message = safeMessage)
                    }
                },
            )
        }
    }

    fun startPhonePairing() {
        pairingJob?.cancel()
        mutableState.value = mutableState.value.copy(
            destination = Destination.PhonePairing,
            message = null,
            pairing = PairingUiState(phase = PairingPhase.Creating),
        )
        pairingJob = viewModelScope.launch {
            val crypto = withContext(Dispatchers.Default) { PairingCrypto.create() }
            runCatching { localPairingServer.start(crypto.modulusBase64, crypto.exponent) }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        pairing = PairingUiState(PairingPhase.Failed, message = it.message ?: "The local pairing page could not be started."),
                    )
                }
                .onSuccess { session ->
                    mutableState.value = mutableState.value.copy(
                        pairing = PairingUiState(
                            phase = PairingPhase.Waiting,
                            code = session.code,
                            linkUrl = session.linkUrl,
                            expiresAtEpochMs = session.expiresAtEpochMs,
                        ),
                    )
                    val chunks = localPairingServer.awaitSubmission(session)
                    if (mutableState.value.destination != Destination.PhonePairing) return@onSuccess
                    if (chunks == null) {
                        mutableState.value = mutableState.value.copy(
                            pairing = mutableState.value.pairing.copy(phase = PairingPhase.Expired, message = "The code expired. Create a new one."),
                        )
                    } else {
                        mutableState.value = mutableState.value.copy(
                            pairing = mutableState.value.pairing.copy(phase = PairingPhase.Received, message = "Verifying your provider…"),
                        )
                        val input = runCatching { crypto.decrypt(chunks) }.getOrElse {
                            localPairingServer.stop()
                            mutableState.value = mutableState.value.copy(
                                pairing = mutableState.value.pairing.copy(phase = PairingPhase.Failed, message = "The encrypted payload could not be opened."),
                            )
                            return@onSuccess
                        }
                        localPairingServer.stop()
                        connect(input)
                    }
                }
        }
    }

    fun cancelPhonePairing(destination: Destination = Destination.Onboarding) {
        pairingJob?.cancel()
        pairingJob = null
        localPairingServer.stop()
        mutableState.value = mutableState.value.copy(destination = destination, pairing = PairingUiState(), message = null)
    }

    fun sync(account: AccountEntity? = mutableState.value.account) {
        if (account == null) {
            mutableState.value = mutableState.value.copy(busy = false, message = "No account is connected.", destination = Destination.Onboarding)
            return
        }
        mutableState.value = mutableState.value.copy(destination = Destination.Sync, busy = true, message = null, syncProgress = emptyList())
        viewModelScope.launch {
            val summary = repository.syncAll(account) { update ->
                val withoutScope = mutableState.value.syncProgress.filterNot { it.scope == update.scope }
                mutableState.value = mutableState.value.copy(syncProgress = withoutScope + update)
            }
            val currentDestination = mutableState.value.destination
            mutableState.value = mutableState.value.copy(
                destination = if (summary.usable && currentDestination != Destination.Sync) currentDestination else Destination.Sync,
                busy = false,
                message = if (summary.usable) null else "The initial synchronization failed. Your account is saved; try again.",
            )
        }
    }

    fun navigate(destination: Destination) {
        if (mutableState.value.destination == Destination.PhonePairing && destination != Destination.PhonePairing) {
            cancelPhonePairing(destination)
            return
        }
        mutableState.value = mutableState.value.copy(destination = destination, message = null, playbackUrl = null)
    }

    fun openSettings(from: Destination) {
        mutableState.value = mutableState.value.copy(destination = Destination.Settings, settingsReturn = from)
    }

    fun closeSettings() = navigate(mutableState.value.settingsReturn)

    fun selectLiveCategory(id: String?) {
        mutableState.value = mutableState.value.copy(selectedCategoryId = id)
    }

    fun selectMovieCategory(categoryId: String?) { selectedMovieCategoryState.value = categoryId }

    fun selectSeriesCategory(categoryId: String?) { selectedSeriesCategoryState.value = categoryId }

    fun playLive(channel: LiveChannelEntity) {
        val account = mutableState.value.account ?: return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            runCatching { repository.livePlaybackUrl(account, channel.providerId) }
                .onSuccess { mutableState.value = mutableState.value.copy(
                    destination = Destination.Player, playbackUrl = it, busy = false,
                    playbackType = PlaybackType.Live, playbackContentId = channel.providerId,
                    playbackTitle = channel.name, playbackStartMs = 0, playerReturn = Destination.LiveTv,
                ) }
                .onFailure { mutableState.value = mutableState.value.copy(busy = false, message = "This channel could not be opened.") }
        }
    }

    fun openMovie(movie: MovieEntity) {
        mutableState.value = mutableState.value.copy(selectedMovie = movie, selectedSeries = null, selectedEpisode = null, destination = Destination.MovieDetails, favorite = false)
        viewModelScope.launch {
            val account = mutableState.value.account
            val favorite = account?.let { repository.databaseFavorite(it.id, "movie", movie.providerId) } ?: false
            mutableState.value = mutableState.value.copy(favorite = favorite)
        }
    }

    fun loadMovieMetadata(movie: MovieEntity) {
        if (movie.tmdbId == null || movie.metadataUpdatedAt != null) return
        val requestKey = "movie:${movie.accountId}:${movie.providerId}"
        if (!metadataRequests.add(requestKey)) return
        viewModelScope.launch {
            try {
                val enriched = metadataSemaphore.withPermit { repository.enrichMovie(movie) }
                if (mutableState.value.selectedMovie?.providerId == enriched.providerId) {
                    mutableState.value = mutableState.value.copy(selectedMovie = enriched)
                }
            }
            finally { metadataRequests.remove(requestKey) }
        }
    }

    fun loadSeriesMetadata(series: SeriesEntity) {
        if (series.tmdbId == null || series.metadataUpdatedAt != null) return
        val requestKey = "series:${series.accountId}:${series.providerId}"
        if (!metadataRequests.add(requestKey)) return
        viewModelScope.launch {
            try {
                val enriched = metadataSemaphore.withPermit { repository.enrichSeriesMetadata(series) }
                if (mutableState.value.selectedSeries?.providerId == enriched.providerId) {
                    mutableState.value = mutableState.value.copy(selectedSeries = enriched)
                }
            }
            finally { metadataRequests.remove(requestKey) }
        }
    }

    fun playMovie() {
        val account = mutableState.value.account ?: return
        val movie = mutableState.value.selectedMovie ?: return
        viewModelScope.launch {
            val start = repository.playbackPosition(account.id, "movie", movie.providerId)
            runCatching { repository.moviePlaybackUrl(account, movie) }
                .onSuccess { mutableState.value = mutableState.value.copy(
                    destination = Destination.Player, playbackUrl = it, playbackType = PlaybackType.Movie,
                    playbackContentId = movie.providerId, playbackTitle = movie.name, playbackStartMs = start, playerReturn = Destination.MovieDetails,
                ) }
                .onFailure { mutableState.value = mutableState.value.copy(message = "This movie could not be opened.") }
        }
    }

    fun openSeries(series: SeriesEntity) {
        val account = mutableState.value.account ?: return
        mutableState.value = mutableState.value.copy(selectedMovie = null, selectedSeries = series, selectedEpisode = null, destination = Destination.SeriesDetails, busy = true, message = null, favorite = false)
        viewModelScope.launch {
            runCatching { repository.loadSeriesDetails(account, series) }
                .onSuccess { updated ->
                    mutableState.value = mutableState.value.copy(
                        selectedSeries = updated, busy = false,
                        favorite = repository.databaseFavorite(account.id, "series", series.providerId),
                    )
                }
                .onFailure { mutableState.value = mutableState.value.copy(busy = false, message = "Episodes could not be loaded. Try again.") }
        }
    }

    fun playEpisode(episode: SeriesEpisodeEntity) {
        val account = mutableState.value.account ?: return
        mutableState.value = mutableState.value.copy(selectedEpisode = episode, busy = true, message = null)
        viewModelScope.launch {
            val start = repository.playbackPosition(account.id, "episode", episode.providerId)
            runCatching { repository.episodePlaybackUrl(account, episode) }
                .onSuccess { url -> mutableState.value = mutableState.value.copy(
                    destination = Destination.Player, playbackUrl = url, busy = false,
                    playbackType = PlaybackType.Episode, playbackContentId = episode.providerId,
                    playbackTitle = episode.title, playbackStartMs = start, playerReturn = Destination.SeriesDetails,
                ) }
                .onFailure { mutableState.value = mutableState.value.copy(busy = false, message = "This episode could not be opened.") }
        }
    }

    fun toggleFavorite() {
        val account = mutableState.value.account ?: return
        val type: String
        val id: String
        when {
            mutableState.value.selectedMovie != null -> { type = "movie"; id = mutableState.value.selectedMovie!!.providerId }
            mutableState.value.selectedSeries != null -> { type = "series"; id = mutableState.value.selectedSeries!!.providerId }
            else -> return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(favorite = repository.toggleFavorite(account.id, type, id))
        }
    }

    fun search(query: String) {
        val account = mutableState.value.account ?: return
        mutableState.value = mutableState.value.copy(searchQuery = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            if (query.isBlank()) mutableState.value = mutableState.value.copy(searchMovies = emptyList(), searchSeries = emptyList())
            else mutableState.value = mutableState.value.copy(
                searchMovies = repository.searchMovies(account.id, query),
                searchSeries = repository.searchSeries(account.id, query),
            )
        }
    }

    fun savePlaybackProgress(positionMs: Long, durationMs: Long) {
        val ui = mutableState.value
        val account = ui.account ?: return
        val id = ui.playbackContentId ?: return
        val type = when (ui.playbackType) { PlaybackType.Movie -> "movie"; PlaybackType.Episode -> "episode"; else -> return }
        viewModelScope.launch { repository.saveProgress(account.id, type, id, positionMs, durationMs) }
    }

    fun removeAccount() {
        val account = mutableState.value.account ?: return
        viewModelScope.launch {
            repository.deleteAccount(account)
            mutableState.value = OhUiState(destination = Destination.Onboarding)
        }
    }

    override fun onCleared() {
        localPairingServer.stop()
        super.onCleared()
    }

    class Factory(
        private val repository: OhRepository,
        private val localPairingServer: LocalPairingServer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OhViewModel(repository, localPairingServer) as T
    }
}
