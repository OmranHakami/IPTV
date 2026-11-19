package com.oh.tv.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.oh.tv.data.local.AccountEntity
import com.oh.tv.data.local.LiveCategoryEntity
import com.oh.tv.data.local.LiveChannelEntity
import com.oh.tv.data.local.VodCategoryEntity
import com.oh.tv.data.local.MovieEntity
import com.oh.tv.data.local.OhDatabase
import com.oh.tv.data.local.FavoriteEntity
import com.oh.tv.data.local.PlaybackProgressEntity
import com.oh.tv.data.local.SeriesEpisodeEntity
import com.oh.tv.data.local.SeriesEntity
import com.oh.tv.data.remote.AccountInput
import com.oh.tv.data.remote.ProviderException
import com.oh.tv.data.remote.PlaybackUrlFactory
import com.oh.tv.data.remote.StreamingArrayParser
import com.oh.tv.data.remote.TmdbGateway
import com.oh.tv.data.remote.UrlNormalizer
import com.oh.tv.data.remote.XtreamGateway
import com.oh.tv.data.remote.asBoolean
import com.oh.tv.data.remote.asDouble
import com.oh.tv.data.remote.asInt
import com.oh.tv.data.remote.asLong
import com.oh.tv.data.remote.asMap
import com.oh.tv.data.remote.asString
import com.oh.tv.security.CredentialVault
import com.oh.tv.security.XtreamCredentials
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID

data class SyncProgress(
    val scope: String,
    val processed: Int = 0,
    val state: SyncState = SyncState.Waiting,
    val elapsedMs: Long = 0,
    val lastSuccessAtMs: Long? = null,
    val failure: String? = null,
) {
    val complete: Boolean get() = state == SyncState.Ready || state == SyncState.Empty
}

enum class SyncState { Waiting, Running, Ready, Empty, Failed, Stale }

object SyncScopes {
    const val LIVE_TV = "Live TV"
    const val MOVIES = "Movies"
    const val TV_SHOWS = "TV Shows"
    val userVisible = listOf(MOVIES, TV_SHOWS, LIVE_TV)
}

data class SyncSummary(val scopes: List<SyncProgress>) {
    val usable: Boolean get() = scopes.any { it.state == SyncState.Ready || it.state == SyncState.Stale }
}

data class CatalogCounts(val liveChannels: Int = 0, val movies: Int = 0, val series: Int = 0) {
    val titles: Int get() = movies + series
}

val BrowseMovieGenres = listOf("Action", "Comedy", "Drama", "Documentary", "Science Fiction")

internal fun MovieEntity.hasSameMatchInputs(other: MovieEntity): Boolean =
    accountId == other.accountId && providerId == other.providerId &&
        tmdbId == other.tmdbId && name == other.name && releaseDate == other.releaseDate

class OhRepository(
    private val database: OhDatabase,
    private val vault: CredentialVault,
    private val xtream: XtreamGateway,
    private val tmdb: TmdbGateway,
    private val playbackUrlFactory: PlaybackUrlFactory = PlaybackUrlFactory(),
) {
    private val catalogSync = Mutex()
    val activeAccount: Flow<AccountEntity?> = database.accountDao().observeActive()

    suspend fun connect(input: AccountInput): Result<AccountEntity> = runCatching {
        val base = UrlNormalizer.normalize(input.serverUrl)
        val auth = xtream.authenticate(base, input.username.trim(), input.password).getOrThrow()
        val id = UUID.randomUUID().toString()
        val alias = "xtream.$id"
        vault.save(alias, XtreamCredentials(input.username.trim(), input.password))
        val account = AccountEntity(
            id = id,
            displayName = input.displayName.trim().ifBlank { "My service" },
            baseUrl = base,
            credentialAlias = alias,
            status = auth.status,
            expiresAtEpochSeconds = auth.expiresAtEpochSeconds,
            lastAuthenticatedAt = System.currentTimeMillis(),
        )
        try {
            database.accountDao().upsert(account)
        } catch (error: Exception) {
            vault.remove(alias)
            throw error
        }
        account
    }

    suspend fun syncAll(account: AccountEntity, onProgress: (SyncProgress) -> Unit): SyncSummary =
        catalogSync.withLock { syncCatalog(account, onProgress) }

    private suspend fun syncCatalog(account: AccountEntity, onProgress: (SyncProgress) -> Unit): SyncSummary = withContext(Dispatchers.IO) {
        val credentials = requireNotNull(vault.load(account.credentialAlias)) { "Account credentials are unavailable" }
        suspend fun scope(name: String, cachedCount: suspend () -> Int, block: suspend () -> Int): SyncProgress {
            val startedAt = System.currentTimeMillis()
            onProgress(SyncProgress(name, state = SyncState.Running))
            val result = try {
                val count = database.withTransaction { block() }
                val finishedAt = System.currentTimeMillis()
                SyncProgress(
                    scope = name,
                    processed = count,
                    state = if (count == 0) SyncState.Empty else SyncState.Ready,
                    elapsedMs = finishedAt - startedAt,
                    lastSuccessAtMs = finishedAt,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val retained = runCatching { cachedCount() }.getOrDefault(0)
                SyncProgress(
                    scope = name,
                    processed = retained,
                    state = if (retained > 0) SyncState.Stale else SyncState.Failed,
                    elapsedMs = System.currentTimeMillis() - startedAt,
                    failure = safeFailure(error),
                )
            }
            onProgress(result)
            return result
        }

        val providerSlots = Semaphore(2)
        SyncSummary(
            listOf(
                async {
                    providerSlots.withPermit {
                        scope(SyncScopes.MOVIES, { database.catalogDao().movieCount(account.id) }) {
                            syncMovies(account, credentials) { onProgress(SyncProgress(SyncScopes.MOVIES, it, SyncState.Running)) }
                        }
                    }
                },
                async {
                    providerSlots.withPermit {
                        scope(SyncScopes.TV_SHOWS, { database.catalogDao().seriesCount(account.id) }) {
                            syncSeries(account, credentials) { onProgress(SyncProgress(SyncScopes.TV_SHOWS, it, SyncState.Running)) }
                        }
                    }
                },
                async {
                    providerSlots.withPermit {
                        scope(SyncScopes.LIVE_TV, { database.catalogDao().liveChannelCount(account.id) }) {
                            syncLiveChannels(account, credentials) { onProgress(SyncProgress(SyncScopes.LIVE_TV, it, SyncState.Running)) }
                        }
                    }
                },
            ).awaitAll(),
        )
    }

    fun liveCategories(accountId: String) = database.catalogDao().observeLiveCategories(accountId)
    fun catalogCounts(accountId: String): Flow<CatalogCounts> = combine(
        database.catalogDao().observeLiveChannelCount(accountId),
        database.catalogDao().observeMovieCount(accountId),
        database.catalogDao().observeSeriesCount(accountId),
    ) { live, movies, series -> CatalogCounts(live, movies, series) }
    fun liveChannels(accountId: String, categoryId: String?): Flow<PagingData<LiveChannelEntity>> = Pager(
        catalogPagingConfig(),
    ) { database.catalogDao().liveChannels(accountId, categoryId) }.flow

    fun movies(accountId: String, categoryId: String? = null): Flow<PagingData<MovieEntity>> = Pager(
        catalogPagingConfig(),
    ) { database.catalogDao().movies(accountId, categoryId) }.flow

    fun movieGenreCounts(accountId: String): Flow<Map<String, Int>> = combine(
        BrowseMovieGenres.map { genre -> database.catalogDao().observeMovieGenreCount(accountId, genre) },
    ) { counts -> BrowseMovieGenres.zip(counts.toList()).toMap() }

    fun vodCategories(accountId: String, kind: String) = database.catalogDao().observeVodCategories(accountId, kind)

    fun series(accountId: String, categoryId: String? = null): Flow<PagingData<SeriesEntity>> = Pager(
        catalogPagingConfig(),
    ) { database.catalogDao().series(accountId, categoryId) }.flow

    fun favoriteMovies(accountId: String) = database.catalogDao().favoriteMovies(accountId)
    fun favoriteSeries(accountId: String) = database.catalogDao().favoriteSeries(accountId)
    fun continueMovies(accountId: String) = database.catalogDao().continueMovies(accountId)
    fun continueEpisodes(accountId: String) = database.catalogDao().continueEpisodes(accountId)
    fun continueWatching(accountId: String) = database.personalDao().continueWatching(accountId)
    suspend fun searchMovies(accountId: String, query: String) = database.catalogDao().searchMovies(accountId, query.trim())
    suspend fun searchSeries(accountId: String, query: String) = database.catalogDao().searchSeries(accountId, query.trim())

    suspend fun deleteAccount(account: AccountEntity) {
        database.withTransaction {
            database.catalogDao().deleteAllLiveCategories(account.id)
            database.catalogDao().deleteAllLiveChannels(account.id)
            database.catalogDao().deleteAllVodCategories(account.id)
            database.catalogDao().deleteAllMovies(account.id)
            database.catalogDao().deleteAllSeries(account.id)
            database.catalogDao().deleteAllEpisodes(account.id)
            database.personalDao().deleteAllFavorites(account.id)
            database.personalDao().deleteAllProgress(account.id)
            database.accountDao().delete(account.id)
        }
        vault.remove(account.credentialAlias)
    }

    suspend fun livePlaybackUrl(account: AccountEntity, streamId: String, preferredExtension: String = "m3u8"): String {
        val credentials = requireNotNull(vault.load(account.credentialAlias))
        return playbackUrlFactory.live(account.baseUrl, credentials.username, credentials.password, streamId, preferredExtension).toString()
    }

    suspend fun moviePlaybackUrl(account: AccountEntity, movie: MovieEntity): String {
        val credentials = requireNotNull(vault.load(account.credentialAlias))
        return playbackUrlFactory.movie(
            account.baseUrl,
            credentials.username,
            credentials.password,
            movie.providerId,
            movie.containerExtension ?: "mp4",
        ).toString()
    }

    suspend fun episodePlaybackUrl(account: AccountEntity, episode: SeriesEpisodeEntity): String {
        val credentials = requireNotNull(vault.load(account.credentialAlias))
        return playbackUrlFactory.episode(
            account.baseUrl,
            credentials.username,
            credentials.password,
            episode.providerId,
            episode.containerExtension ?: "mp4",
        ).toString()
    }

    fun episodes(accountId: String, seriesId: String) = database.catalogDao().observeEpisodes(accountId, seriesId)
    fun seriesProgress(accountId: String, seriesId: String) = database.personalDao().seriesProgress(accountId, seriesId)

    suspend fun loadSeriesDetails(account: AccountEntity, series: SeriesEntity): SeriesEntity {
        val credentials = requireNotNull(vault.load(account.credentialAlias))
        val root = xtream.seriesInfo(account.baseUrl, credentials.username, credentials.password, series.providerId)
        val info = root["info"].asMap()
        val episodes = parseSeriesEpisodes(root["episodes"], account.id, series.providerId)
        return database.withTransaction {
            val dao = database.catalogDao()
            val current = dao.seriesItem(account.id, series.providerId) ?: return@withTransaction series
            val updated = current.copy(
                overview = current.overview ?: info["plot"].asString(),
                releaseDate = current.releaseDate ?: info["releaseDate"].asString(),
                genres = current.genres ?: info["genre"].asString(),
                detailsLoaded = true,
            )
            dao.upsertSeries(listOf(updated))
            dao.deleteEpisodes(account.id, series.providerId)
            dao.upsertEpisodes(episodes)
            updated
        }
    }

    suspend fun toggleFavorite(accountId: String, type: String, contentId: String): Boolean {
        val dao = database.personalDao()
        val currentlyFavorite = dao.isFavorite(accountId, type, contentId)
        if (currentlyFavorite) dao.removeFavorite(accountId, type, contentId)
        else dao.upsertFavorite(FavoriteEntity(accountId, type, contentId, System.currentTimeMillis()))
        return !currentlyFavorite
    }

    suspend fun databaseFavorite(accountId: String, type: String, contentId: String): Boolean =
        database.personalDao().isFavorite(accountId, type, contentId)

    suspend fun saveProgress(accountId: String, type: String, contentId: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val completed = PlaybackPolicy.isCompleted(positionMs, durationMs)
        database.personalDao().upsertProgress(
            PlaybackProgressEntity(accountId, type, contentId, positionMs, durationMs, completed, System.currentTimeMillis()),
        )
    }

    suspend fun playbackPosition(accountId: String, type: String, contentId: String): Long =
        database.personalDao().progress(accountId, type, contentId)?.let {
            PlaybackPolicy.resumePosition(it.positionMs, it.durationMs, it.completed)
        } ?: 0L

    suspend fun enrichMovie(item: MovieEntity): MovieEntity {
        val dao = database.catalogDao()
        val ids = listOf(item.providerId)
        val source = dao.moviesByIds(item.accountId, ids).firstOrNull()
            ?: return item.copy(resolvedTmdbId = null, resolvedImdbId = null)
        if (!tmdb.enabled || source.resolvedTmdbId != null ||
            (source.tmdbRetryAfter ?: 0L) > System.currentTimeMillis()
        ) return source

        var retryDelay = 30 * 60_000L
        val metadata = try {
            tmdb.matchMovie(source.tmdbId, source.name, source.releaseDate?.take(4)?.toIntOrNull())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            retryDelay = 60_000L
            null
        }
        return database.withTransaction {
            val current = dao.moviesByIds(item.accountId, ids).firstOrNull()
                ?: return@withTransaction source.copy(resolvedTmdbId = null, resolvedImdbId = null)
            if (!current.hasSameMatchInputs(source) || current.resolvedTmdbId != null) return@withTransaction current
            val now = System.currentTimeMillis()
            val updated = if (metadata == null) current.copy(tmdbRetryAfter = now + retryDelay) else current.copy(
                tmdbTitle = metadata.title.takeIf { it.isNotBlank() } ?: current.tmdbTitle,
                tmdbOverview = metadata.overview ?: current.tmdbOverview,
                tmdbReleaseDate = metadata.releaseDate ?: current.tmdbReleaseDate,
                runtimeMinutes = metadata.runtimeMinutes ?: current.runtimeMinutes,
                tmdbRating = metadata.rating ?: current.tmdbRating,
                tmdbVoteCount = metadata.voteCount ?: current.tmdbVoteCount,
                genres = metadata.genres ?: current.genres,
                posterPath = metadata.posterPath ?: current.posterPath,
                backdropPath = metadata.backdropPath ?: current.backdropPath,
                metadataUpdatedAt = now,
                resolvedTmdbId = metadata.id,
                resolvedImdbId = metadata.imdbId,
                tmdbRetryAfter = null,
            )
            dao.upsertMovies(listOf(updated))
            updated
        }
    }

    suspend fun enrichSeriesMetadata(item: SeriesEntity): SeriesEntity {
        if (item.metadataUpdatedAt != null) return item
        val id = item.tmdbId ?: return item
        val metadata = tmdb.series(id).getOrNull() ?: return item
        val current = database.catalogDao().seriesItem(item.accountId, item.providerId) ?: item
        val updated = current.copy(
            tmdbTitle = metadata.title.takeIf { it.isNotBlank() } ?: current.tmdbTitle,
            tmdbOverview = metadata.overview ?: current.tmdbOverview,
            tmdbReleaseDate = metadata.releaseDate ?: current.tmdbReleaseDate,
            tmdbRating = metadata.rating ?: current.tmdbRating,
            tmdbVoteCount = metadata.voteCount ?: current.tmdbVoteCount,
            genres = metadata.genres ?: current.genres,
            posterPath = metadata.posterPath ?: current.posterPath,
            backdropPath = metadata.backdropPath ?: current.backdropPath,
            metadataUpdatedAt = System.currentTimeMillis(),
        )
        database.catalogDao().upsertSeries(listOf(updated))
        return updated
    }

    private suspend fun <T> syncCategories(
        account: AccountEntity, credentials: XtreamCredentials, action: String,
        upsert: suspend (List<T>) -> Unit,
        category: (id: String, name: String, order: Int) -> T,
    ) {
        var order = 0
        forEachCatalogBatch(account, credentials, action) { batch ->
            val categories = batch.mapNotNull { raw ->
                val id = raw["category_id"].asString() ?: return@mapNotNull null
                category(id, raw["category_name"].asString() ?: "Untitled", order++)
            }
            upsert(categories)
        }
    }

    private suspend fun forEachCatalogBatch(
        account: AccountEntity, credentials: XtreamCredentials, action: String,
        consume: suspend (List<Map<String, Any?>>) -> Unit,
    ) = xtream.openArray(account.baseUrl, credentials.username, credentials.password, action).use { body ->
        StreamingArrayParser.forEachBatchSuspending(body.source(), consume = consume)
    }

    private suspend fun syncLiveChannels(account: AccountEntity, credentials: XtreamCredentials, progress: (Int) -> Unit): Int {
        val generation = System.currentTimeMillis()
        syncCategories(account, credentials, "get_live_categories", database.catalogDao()::upsertLiveCategories) { id, name, order ->
            LiveCategoryEntity(account.id, id, name, order, generation)
        }
        var count = 0
        var seen = 0
        forEachCatalogBatch(account, credentials, "get_live_streams") { batch ->
            val batchStart = seen
            seen += batch.size
            val mapped = batch.mapIndexedNotNull { index, raw ->
                val id = raw["stream_id"].asString() ?: return@mapIndexedNotNull null
                LiveChannelEntity(
                    accountId = account.id,
                    providerId = id,
                    categoryId = raw["category_id"].asString().orEmpty(),
                    number = raw["num"].asInt() ?: fallbackChannelNumber(batchStart, index),
                    name = raw["name"].asString() ?: "Untitled channel",
                    iconUrl = raw["stream_icon"].asString(),
                    epgChannelId = raw["epg_channel_id"].asString(),
                    hasCatchup = raw["tv_archive"].asBoolean() ?: false,
                    syncGeneration = generation,
                )
            }
            database.catalogDao().upsertLiveChannels(mapped)
            count += mapped.size
            progress(count)
        }
        database.catalogDao().deleteStaleLiveChannels(account.id, generation)
        database.catalogDao().deleteStaleLiveCategories(account.id, generation)
        return count
    }

    private suspend fun syncMovies(account: AccountEntity, credentials: XtreamCredentials, progress: (Int) -> Unit): Int {
        val generation = System.currentTimeMillis()
        syncCategories(account, credentials, "get_vod_categories", database.catalogDao()::upsertVodCategories) { id, name, order ->
            VodCategoryEntity(account.id, "movie", id, name, order, generation)
        }
        var count = 0
        forEachCatalogBatch(account, credentials, "get_vod_streams") { batch ->
            val mapped = batch.mapNotNull { raw ->
                val id = raw["stream_id"].asString() ?: return@mapNotNull null
                MovieEntity(
                    accountId = account.id,
                    providerId = id,
                    name = raw["name"].asString() ?: "Untitled movie",
                    containerExtension = raw["container_extension"].asString(),
                    providerIconUrl = raw["stream_icon"].asString(),
                    providerRating = raw["rating"].asDouble(),
                    addedAt = raw["added"].asLong(),
                    categoryId = raw["category_id"].asString(),
                    tmdbId = raw["tmdb_id"].asLong(),
                    overview = null,
                    releaseDate = raw["release_date"].asString() ?: raw["releaseDate"].asString()
                        ?: raw["year"].asString(),
                    runtimeMinutes = null,
                    tmdbRating = null,
                    tmdbVoteCount = null,
                    genres = null,
                    posterPath = null,
                    backdropPath = null,
                    metadataUpdatedAt = null,
                    syncGeneration = generation,
                )
            }
            val existing = if (mapped.isEmpty()) emptyMap<String, MovieEntity>() else database.catalogDao()
                .moviesByIds(account.id, mapped.map { it.providerId }).associateBy { it.providerId }
            val merged = mapped.map { fresh ->
                existing[fresh.providerId]?.takeIf { it.hasSameMatchInputs(fresh) }?.let { old ->
                    fresh.copy(
                        tmdbTitle = old.tmdbTitle,
                        tmdbOverview = old.tmdbOverview,
                        tmdbReleaseDate = old.tmdbReleaseDate,
                        overview = old.overview,
                        releaseDate = old.releaseDate,
                        runtimeMinutes = old.runtimeMinutes,
                        tmdbRating = old.tmdbRating,
                        tmdbVoteCount = old.tmdbVoteCount,
                        genres = old.genres,
                        posterPath = old.posterPath,
                        backdropPath = old.backdropPath,
                        metadataUpdatedAt = old.metadataUpdatedAt,
                        resolvedTmdbId = old.resolvedTmdbId,
                        resolvedImdbId = old.resolvedImdbId,
                        tmdbRetryAfter = old.tmdbRetryAfter,
                    )
                } ?: fresh
            }
            database.catalogDao().upsertMovies(merged)
            count += mapped.size
            progress(count)
        }
        database.catalogDao().deleteStaleMovies(account.id, generation)
        database.catalogDao().deleteStaleVodCategories(account.id, "movie", generation)
        return count
    }

    private suspend fun syncSeries(account: AccountEntity, credentials: XtreamCredentials, progress: (Int) -> Unit): Int {
        val generation = System.currentTimeMillis()
        syncCategories(account, credentials, "get_series_categories", database.catalogDao()::upsertVodCategories) { id, name, order ->
            VodCategoryEntity(account.id, "series", id, name, order, generation)
        }
        var count = 0
        forEachCatalogBatch(account, credentials, "get_series") { batch ->
            val mapped = batch.mapNotNull { raw ->
                val id = raw["series_id"].asString() ?: return@mapNotNull null
                SeriesEntity(
                    accountId = account.id,
                    providerId = id,
                    name = raw["name"].asString() ?: "Untitled TV show",
                    providerIconUrl = raw["cover"].asString(),
                    providerRating = raw["rating"].asDouble(),
                    lastModified = raw["last_modified"].asLong(),
                    categoryId = raw["category_id"].asString(),
                    tmdbId = raw["tmdb_id"].asLong(),
                    overview = raw["plot"].asString(),
                    releaseDate = raw["releaseDate"].asString(),
                    tmdbRating = null,
                    tmdbVoteCount = null,
                    genres = null,
                    posterPath = null,
                    backdropPath = null,
                    metadataUpdatedAt = null,
                    detailsLoaded = false,
                    syncGeneration = generation,
                )
            }
            val existing = if (mapped.isEmpty()) emptyMap<String, SeriesEntity>() else database.catalogDao()
                .seriesByIds(account.id, mapped.map { it.providerId }).associateBy { it.providerId }
            val merged = mapped.map { fresh ->
                existing[fresh.providerId]?.let { old ->
                    fresh.copy(
                        tmdbTitle = old.tmdbTitle,
                        tmdbOverview = old.tmdbOverview,
                        tmdbReleaseDate = old.tmdbReleaseDate,
                        tmdbRating = old.tmdbRating,
                        tmdbVoteCount = old.tmdbVoteCount,
                        genres = old.genres,
                        posterPath = old.posterPath,
                        backdropPath = old.backdropPath,
                        metadataUpdatedAt = old.metadataUpdatedAt,
                        detailsLoaded = old.detailsLoaded,
                    )
                } ?: fresh
            }
            database.catalogDao().upsertSeries(merged)
            count += mapped.size
            progress(count)
        }
        database.catalogDao().deleteStaleSeries(account.id, generation)
        database.catalogDao().deleteStaleVodCategories(account.id, "series", generation)
        return count
    }

    private fun safeFailure(error: Exception): String = when (error) {
        is ProviderException -> error.failure.toString()
        else -> "The operation could not be completed"
    }

    private fun catalogPagingConfig() = PagingConfig(
        pageSize = 60,
        initialLoadSize = 120,
        prefetchDistance = 20,
        enablePlaceholders = false,
        maxSize = 300,
    )
}
