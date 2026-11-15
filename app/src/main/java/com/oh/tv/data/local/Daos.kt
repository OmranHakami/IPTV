package com.oh.tv.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY lastAuthenticatedAt DESC LIMIT 1")
    fun observeActive(): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts ORDER BY lastAuthenticatedAt DESC LIMIT 1")
    suspend fun active(): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun delete(accountId: String)
}

@Dao
interface CatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLiveCategories(items: List<LiveCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLiveChannels(items: List<LiveChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovies(items: List<MovieEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeries(items: List<SeriesEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(items: List<SeriesEpisodeEntity>)

    @Query("SELECT * FROM live_categories WHERE accountId = :accountId ORDER BY sortOrder, name")
    fun observeLiveCategories(accountId: String): Flow<List<LiveCategoryEntity>>

    @Query("SELECT * FROM live_channels WHERE accountId = :accountId AND (:categoryId IS NULL OR categoryId = :categoryId) ORDER BY number, name")
    fun liveChannels(accountId: String, categoryId: String?): PagingSource<Int, LiveChannelEntity>

    @Query("SELECT * FROM movies WHERE accountId = :accountId AND (:genre IS NULL OR genres LIKE '%' || :genre || '%') ORDER BY COALESCE(addedAt, 0) DESC, name")
    fun movies(accountId: String, genre: String?): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE accountId=:accountId AND providerId IN (:providerIds)")
    suspend fun moviesByIds(accountId: String, providerIds: List<String>): List<MovieEntity>

    @Query("SELECT * FROM series WHERE accountId = :accountId ORDER BY COALESCE(lastModified, 0) DESC, name")
    fun series(accountId: String): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE accountId=:accountId AND providerId IN (:providerIds)")
    suspend fun seriesByIds(accountId: String, providerIds: List<String>): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE accountId=:accountId AND providerId=:seriesId LIMIT 1")
    suspend fun seriesItem(accountId: String, seriesId: String): SeriesEntity?

    @Query("SELECT COUNT(*) FROM live_channels WHERE accountId=:accountId")
    suspend fun liveChannelCount(accountId: String): Int

    @Query("SELECT COUNT(*) FROM live_channels WHERE accountId=:accountId")
    fun observeLiveChannelCount(accountId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM movies WHERE accountId=:accountId")
    suspend fun movieCount(accountId: String): Int

    @Query("SELECT COUNT(*) FROM movies WHERE accountId=:accountId")
    fun observeMovieCount(accountId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM movies WHERE accountId=:accountId AND genres LIKE '%' || :genre || '%'")
    fun observeMovieGenreCount(accountId: String, genre: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM series WHERE accountId=:accountId")
    suspend fun seriesCount(accountId: String): Int

    @Query("SELECT COUNT(*) FROM series WHERE accountId=:accountId")
    fun observeSeriesCount(accountId: String): Flow<Int>

    @Query("SELECT * FROM series_episodes WHERE accountId=:accountId AND seriesId=:seriesId ORDER BY seasonNumber, episodeNumber")
    fun observeEpisodes(accountId: String, seriesId: String): Flow<List<SeriesEpisodeEntity>>

    @Query("SELECT * FROM movies WHERE accountId = :accountId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    suspend fun searchMovies(accountId: String, query: String, limit: Int = 50): List<MovieEntity>

    @Query("SELECT * FROM series WHERE accountId = :accountId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    suspend fun searchSeries(accountId: String, query: String, limit: Int = 50): List<SeriesEntity>

    @Query("SELECT movies.* FROM movies INNER JOIN favorites ON favorites.accountId=movies.accountId AND favorites.contentId=movies.providerId WHERE movies.accountId=:accountId AND favorites.contentType='movie' ORDER BY favorites.createdAt DESC")
    fun favoriteMovies(accountId: String): Flow<List<MovieEntity>>

    @Query("SELECT series.* FROM series INNER JOIN favorites ON favorites.accountId=series.accountId AND favorites.contentId=series.providerId WHERE series.accountId=:accountId AND favorites.contentType='series' ORDER BY favorites.createdAt DESC")
    fun favoriteSeries(accountId: String): Flow<List<SeriesEntity>>

    @Query("SELECT movies.* FROM movies INNER JOIN playback_progress ON playback_progress.accountId=movies.accountId AND playback_progress.contentId=movies.providerId WHERE movies.accountId=:accountId AND playback_progress.contentType='movie' AND playback_progress.completed=0 ORDER BY playback_progress.updatedAt DESC LIMIT :limit")
    fun continueMovies(accountId: String, limit: Int = 20): Flow<List<MovieEntity>>

    @Query("DELETE FROM live_categories WHERE accountId = :accountId AND syncGeneration != :generation")
    suspend fun deleteStaleLiveCategories(accountId: String, generation: Long)

    @Query("DELETE FROM live_channels WHERE accountId = :accountId AND syncGeneration != :generation")
    suspend fun deleteStaleLiveChannels(accountId: String, generation: Long)

    @Query("DELETE FROM movies WHERE accountId = :accountId AND syncGeneration != :generation")
    suspend fun deleteStaleMovies(accountId: String, generation: Long)

    @Query("DELETE FROM series WHERE accountId = :accountId AND syncGeneration != :generation")
    suspend fun deleteStaleSeries(accountId: String, generation: Long)

    @Query("DELETE FROM series_episodes WHERE accountId=:accountId AND seriesId=:seriesId")
    suspend fun deleteEpisodes(accountId: String, seriesId: String)

    @Query("DELETE FROM live_categories WHERE accountId=:accountId")
    suspend fun deleteAllLiveCategories(accountId: String)

    @Query("DELETE FROM live_channels WHERE accountId=:accountId")
    suspend fun deleteAllLiveChannels(accountId: String)

    @Query("DELETE FROM movies WHERE accountId=:accountId")
    suspend fun deleteAllMovies(accountId: String)

    @Query("DELETE FROM series WHERE accountId=:accountId")
    suspend fun deleteAllSeries(accountId: String)

    @Query("DELETE FROM series_episodes WHERE accountId=:accountId")
    suspend fun deleteAllEpisodes(accountId: String)
}

@Dao
interface PersonalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE accountId=:accountId AND contentType=:type AND contentId=:contentId")
    suspend fun removeFavorite(accountId: String, type: String, contentId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(item: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE accountId=:accountId AND contentType=:type AND contentId=:contentId LIMIT 1")
    suspend fun progress(accountId: String, type: String, contentId: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE accountId=:accountId AND completed=0 ORDER BY updatedAt DESC LIMIT :limit")
    fun continueWatching(accountId: String, limit: Int = 20): Flow<List<PlaybackProgressEntity>>

    @Query("SELECT playback_progress.* FROM playback_progress INNER JOIN series_episodes ON series_episodes.accountId=playback_progress.accountId AND series_episodes.providerId=playback_progress.contentId WHERE playback_progress.accountId=:accountId AND playback_progress.contentType='episode' AND series_episodes.seriesId=:seriesId ORDER BY playback_progress.updatedAt DESC")
    fun seriesProgress(accountId: String, seriesId: String): Flow<List<PlaybackProgressEntity>>

    @Query("SELECT * FROM favorites WHERE accountId=:accountId ORDER BY createdAt DESC")
    fun observeFavorites(accountId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE accountId=:accountId AND contentType=:type AND contentId=:contentId)")
    suspend fun isFavorite(accountId: String, type: String, contentId: String): Boolean

    @Query("DELETE FROM favorites WHERE accountId=:accountId")
    suspend fun deleteAllFavorites(accountId: String)

    @Query("DELETE FROM playback_progress WHERE accountId=:accountId")
    suspend fun deleteAllProgress(accountId: String)
}
