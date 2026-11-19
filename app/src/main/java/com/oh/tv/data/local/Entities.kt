package com.oh.tv.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val baseUrl: String,
    val credentialAlias: String,
    val status: String,
    val expiresAtEpochSeconds: Long?,
    val lastAuthenticatedAt: Long,
)

@Entity(
    tableName = "live_categories",
    primaryKeys = ["accountId", "providerId"],
    indices = [Index("accountId")],
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class LiveCategoryEntity(
    val accountId: String,
    val providerId: String,
    val name: String,
    val sortOrder: Int,
    val syncGeneration: Long,
)

@Entity(
    tableName = "live_channels",
    primaryKeys = ["accountId", "providerId"],
    indices = [Index("accountId", "categoryId"), Index("accountId", "epgChannelId")],
)
data class LiveChannelEntity(
    val accountId: String,
    val providerId: String,
    val categoryId: String,
    val number: Int,
    val name: String,
    val iconUrl: String?,
    val epgChannelId: String?,
    val hasCatchup: Boolean,
    val syncGeneration: Long,
)

@Entity(
    tableName = "vod_categories",
    primaryKeys = ["accountId", "kind", "providerId"],
    indices = [Index("accountId", "kind")],
)
data class VodCategoryEntity(
    val accountId: String,
    val kind: String,
    val providerId: String,
    val name: String,
    val sortOrder: Int,
    val syncGeneration: Long,
)

@Entity(
    tableName = "movies",
    primaryKeys = ["accountId", "providerId"],
    indices = [Index("accountId", "name"), Index("accountId", "addedAt")],
)
data class MovieEntity(
    val accountId: String,
    val providerId: String,
    val name: String,
    val containerExtension: String?,
    val providerIconUrl: String?,
    val providerRating: Double?,
    val addedAt: Long?,
    val categoryId: String? = null,
    val tmdbId: Long?,
    val overview: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val tmdbRating: Double?,
    val tmdbVoteCount: Int?,
    val genres: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val metadataUpdatedAt: Long?,
    val tmdbTitle: String? = null,
    val tmdbOverview: String? = null,
    val tmdbReleaseDate: String? = null,
    val syncGeneration: Long,
    val resolvedTmdbId: Long? = null,
    val resolvedImdbId: String? = null,
    val tmdbRetryAfter: Long? = null,
) {
    val displayName: String get() = tmdbTitle?.takeIf { it.isNotBlank() } ?: name
    val displayOverview: String? get() = tmdbOverview?.takeIf { it.isNotBlank() } ?: overview
    val displayReleaseDate: String? get() = tmdbReleaseDate ?: releaseDate
}

@Entity(
    tableName = "series",
    primaryKeys = ["accountId", "providerId"],
    indices = [Index("accountId", "name"), Index("accountId", "lastModified")],
)
data class SeriesEntity(
    val accountId: String,
    val providerId: String,
    val name: String,
    val providerIconUrl: String?,
    val providerRating: Double?,
    val lastModified: Long?,
    val categoryId: String? = null,
    val tmdbId: Long?,
    val overview: String?,
    val releaseDate: String?,
    val tmdbRating: Double?,
    val tmdbVoteCount: Int?,
    val genres: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val metadataUpdatedAt: Long?,
    val tmdbTitle: String? = null,
    val tmdbOverview: String? = null,
    val tmdbReleaseDate: String? = null,
    val detailsLoaded: Boolean,
    val syncGeneration: Long,
) {
    val displayName: String get() = tmdbTitle?.takeIf { it.isNotBlank() } ?: name
    val displayOverview: String? get() = tmdbOverview?.takeIf { it.isNotBlank() } ?: overview
    val displayReleaseDate: String? get() = tmdbReleaseDate ?: releaseDate
}

@Entity(
    tableName = "series_episodes",
    primaryKeys = ["accountId", "seriesId", "providerId"],
    indices = [Index("accountId", "seriesId", "seasonNumber", "episodeNumber")],
)
data class SeriesEpisodeEntity(
    val accountId: String,
    val seriesId: String,
    val providerId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val containerExtension: String?,
    val overview: String?,
    val duration: String?,
    val posterUrl: String?,
    val syncGeneration: Long,
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["accountId", "contentType", "contentId"],
    indices = [Index("accountId", "createdAt")],
)
data class FavoriteEntity(
    val accountId: String,
    val contentType: String,
    val contentId: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playback_progress",
    primaryKeys = ["accountId", "contentType", "contentId"],
    indices = [Index("accountId", "updatedAt")],
)
data class PlaybackProgressEntity(
    val accountId: String,
    val contentType: String,
    val contentId: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)
