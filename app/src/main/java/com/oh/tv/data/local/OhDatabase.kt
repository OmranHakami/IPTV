package com.oh.tv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        LiveCategoryEntity::class,
        LiveChannelEntity::class,
        VodCategoryEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        SeriesEpisodeEntity::class,
        FavoriteEntity::class,
        PlaybackProgressEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class OhDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun catalogDao(): CatalogDao
    abstract fun personalDao(): PersonalDao
}

/** Preserve existing catalogs and accounts when adding provider VOD category metadata. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS vod_categories (accountId TEXT NOT NULL, kind TEXT NOT NULL, providerId TEXT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, syncGeneration INTEGER NOT NULL, PRIMARY KEY(accountId, kind, providerId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_categories_accountId_kind ON vod_categories(accountId, kind)")
        db.execSQL("ALTER TABLE movies ADD COLUMN categoryId TEXT")
        db.execSQL("ALTER TABLE series ADD COLUMN categoryId TEXT")
    }
}
