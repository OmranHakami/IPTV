package com.oh.tv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        LiveCategoryEntity::class,
        LiveChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        SeriesEpisodeEntity::class,
        FavoriteEntity::class,
        PlaybackProgressEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class OhDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun catalogDao(): CatalogDao
    abstract fun personalDao(): PersonalDao
}
