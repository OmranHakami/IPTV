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
import com.oh.tv.data.remote.asList
import com.oh.tv.data.remote.asMap
import com.oh.tv.data.remote.asString
import com.oh.tv.security.CredentialVault
import com.oh.tv.security.XtreamCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    val userVisible = listOf(LIVE_TV, MOVIES, TV_SHOWS)
}

data class SyncSummary(val scopes: List<SyncProgress>) {
    val usable: Boolean get() = scopes.any { it.state == SyncState.Ready || it.state == SyncState.Stale }
}

data class CatalogCounts(val liveChannels: Int = 0, val movies: Int = 0, val series: Int = 0) {
    val titles: Int get() = movies + series
}

val BrowseMovieGenres = listOf("Action", "Comedy", "Drama", "Documentary", "Science Fiction")

class OhRepository(
    private val database: OhDatabase,
    private val vault: CredentialVault,
    private val xtream: XtreamGateway,
    private val tmdb: TmdbGateway,
    private val playbackUrlFactory: PlaybackUrlFactory = PlaybackUrlFactory(),
) {
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

    suspend fun syncAll(account: AccountEntity, onProgress: (SyncProgress) -> Unit): SyncSummary = withContext(Dispatchers.IO) {
        val credentials = requireNotNull(vault.load(account.credentialAlias)) { "Account credentials are unavailable" }
        suspend fun scope(name: String, cachedCount: suspend () -> Int, block: suspend () -> Int): SyncProgress {
            val startedAt = System.currentTimeMillis()
            onProgress(SyncProgress(name, state = SyncState.Running))
            val result = try {
                val count = block()
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

        val results = mutableListOf<SyncProgress>()
        results += scope(SyncScopes.LIVE_TV, { database.catalogDao().liveChannelCount(account.id) }) {
            syncLiveCategories(account, credentials) { }
            syncLiveChannels(account, credentials) { onProgress(SyncProgress(SyncScopes.LIVE_TV, it, SyncState.Running)) }
        }
        delay(350)
        results += scope(SyncScopes.MOVIES, { database.catalogDao().movieCount(account.id) }) {
            syncVodCategories(account, credentials, "movie", "get_vod_categories")
            syncMovies(account, credentials) { onProgress(SyncProgress(SyncScopes.MOVIES, it, SyncState.Running)) }
        }
        delay(350)
        results += scope(SyncScopes.TV_SHOWS, { database.catalogDao().seriesCount(account.id) }) {
            syncVodCategories(account, credentials, "series", "get_series_categories")
            syncSeries(account, credentials) { onProgress(SyncProgress(SyncScopes.TV_SHOWS, it, SyncState.Running)) }
        }
        SyncSummary(results)
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
        val current = database.catalogDao().seriesItem(account.id, series.providerId) ?: series
        val updated = current.copy(
            overview = current.overview ?: info["plot"].asString(),
            releaseDate = current.releaseDate ?: info["releaseDate"].asString(),
            genres = current.genres ?: info["genre"].asString(),
            detailsLoaded = true,
        )
        val generation = System.currentTimeMillis()
        val episodes = root["episodes"].asMap().flatMap { (seasonKey, rawEpisodes) ->
            rawEpisodes.asList().mapNotNull { rawValue ->
                val raw = rawValue.asMap()
                val id = raw["id"].asString() ?: return@mapNotNull null
                val episodeInfo = raw["info"].asMap()
                SeriesEpisodeEntity(
                    accountId = account.id,
                    seriesId = series.providerId,
                    providerId = id,
                    seasonNumber = raw["season"].asInt() ?: seasonKey.toIntOrNull() ?: 0,
                    episodeNumber = raw["episode_num"].asInt() ?: 0,
                    title = raw["title"].asString() ?: "Episode ${raw["episode_num"].asInt() ?: 0}",
                    containerExtension = raw["container_extension"].asString(),
                    overview = episodeInfo["plot"].asString(),
                    duration = episodeInfo["duration"].asString(),
                    posterUrl = episodeInfo["movie_image"].asString(),
                    syncGeneration = generation,
                )
            }
        }
        database.withTransaction {
            database.catalogDao().upsertSeries(listOf(updated))
            database.catalogDao().deleteEpisodes(account.id, series.providerId)
            database.catalogDao().upsertEpisodes(episodes)
        }
        return updated
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
        if (item.metadataUpdatedAt != null) return item
        val id = item.tmdbId ?: return item
        val metadata = tmdb.movie(id).getOrNull() ?: return item
        val updated = item.copy(
            tmdbTitle = metadata.title.takeIf { it.isNotBlank() },
            tmdbOverview = metadata.overview ?: item.tmdbOverview,
            tmdbReleaseDate = metadata.releaseDate ?: item.tmdbReleaseDate,
            runtimeMinutes = metadata.runtimeMinutes ?: item.runtimeMinutes,
            tmdbRating = metadata.rating,
            tmdbVoteCount = metadata.voteCount,
            genres = metadata.genres,
            posterPath = metadata.posterPath,
            backdropPath = metadata.backdropPath,
            metadataUpdatedAt = System.currentTimeMillis(),
        )
        database.catalogDao().upsertMovies(listOf(updated))
        return updated
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

    private suspend fun syncLiveCategories(account: AccountEntity, credentials: XtreamCredentials, progress: (Int) -> Unit): Int {
        val generation = System.currentTimeMillis()
        var count = 0
        xtream.openArray(account.baseUrl, credentials.username, credentials.password, "get_live_categories").use { body ->
            StreamingArrayParser.forEachBatchSuspending(body.source()) { batch ->
                val mapped = batch.mapIndexedNotNull { index, raw ->
                    val id = raw["category_id"].asString() ?: return@mapIndexedNotNull null
                    LiveCategoryEntity(account.id, id, raw["category_name"].asString() ?: "Untitled", count + index, generation)
                }
                database.withTransaction { database.catalogDao().upsertLiveCategories(mapped) }
                count += mapped.size
                progress(count)
            }
        }
        database.catalogDao().deleteStaleLiveCategories(account.id, generation)
        return count
    }

    private suspend fun syncLiveChannels(account: AccountEntity, credentials: XtreamCredentials, progress: (Int) -> Unit): Int {
        val generation = System.currentTimeMillis()
        var count = 0
        xtream.openArray(account.baseUrl, credentials.username, credentials.password, "get_live_streams").use { body ->
            StreamingArrayParser.forEachBatchSuspending(body.source()) { batch ->
                val mapped = batch.mapIndexedNotNull { index, raw ->
                    val id = raw["stream_id"].asString() ?: return@mapIndexedNotNull null
                    LiveChannelEntity(
                        accountId = account.id,
                        providerId = id,
                        categoryId = raw["category_id"].asString().orEmpty(),
                        number = raw["num"].asInt() ?: count + index + 1,
                        name = raw["name"].asString() ?: "Untitled channel",
                        iconUrl = raw["stream_icon"].asString(),
                        epgChannelId = raw["epg_channel_id"].asString(),
                        hasCatchup = raw["tv_archive"].asBoolean() ?: false,
                        syncGeneration = generation,
                    )
                }
                database.withTransaction { database.catalogDao().upsertLiveChannels(mapped) }
                count += mapped.size
                progress(count)
            }
        }
        database.catalogDao().deleteStaleLiveChannels(account.id, generation)
        return count
    }

    private suspend fun syncVodCategories(account: AccountEntity, credentials: XtreamCredentials, kind: String, action: String) {
        val generation = System.currentTimeMillis()
        var order = 0
        xtream.openArray(account.baseUrl, credentials.username, credentials.password, action).use { body ->
            StreamingArrayParser.forEachBatchSuspending(body.source()) { batch ->
                val mapped = batch.mapNotNull { raw ->
                    val id = raw["category_id"].asString() ?: return@mapNotNull null
                    VodCategoryEntity(account.id, kind, id, raw["category_name"].asString() ?: "Untitled", order++, generation)
                }
                database.withTransaction { database.catalogDao().upsertVodCategories(mapped) }
            }
        }
        database.catalogDao().deleteStaleVodCategories(account.id, kind, generation)
    }

    private suspend fun syncMovies(account: AccountEntity, credentials: XtreamCredentials, progress: (Int) -> Unit): Int {
        val generation = System.currentTimeMillis()
        var count = 0
        xtream.openArray(account.baseUrl, credentials.username, credentials.password, "get_vod_streams").use { body ->
            StreamingArrayParser.forEachBatchSuspending(body.source()) { batch ->
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
                        releaseDate = null,
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
                database.withTransaction {
                    val existing = if (mapped.isEmpty()) emptyMap<String, MovieEntity>() else database.catalogDao()
                        .moviesByIds(account.id, mapped.map { it.providerId }).associateBy { it.providerId }
                    val merged = mapped.map { fresh ->
                        existing[fresh.providerId]?.let { old ->
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
                            )
                        } ?: fresh
                    }
                    database.catalogDao().upsertMovies(merged)
                }
                count += mapped.size
                progress(count)
            }
        }
        database.catalogDao().deleteStaleMovies(account.id, generation)
        return count
    }

    private suspend fun syncSeries(account: AccountEntity, credentials: XtreamCredentials, progress: (Int) -> Unit): Int {
        val generation = System.currentTimeMillis()
        var count = 0
        xtream.openArray(account.baseUrl, credentials.username, credentials.password, "get_series").use { body ->
            StreamingArrayParser.forEachBatchSuspending(body.source()) { batch ->
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
                database.withTransaction {
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
                }
                count += mapped.size
                progress(count)
            }
        }
        database.catalogDao().deleteStaleSeries(account.id, generation)
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
