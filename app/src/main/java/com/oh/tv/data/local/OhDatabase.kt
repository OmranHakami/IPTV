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
    version = 4,
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

/** Keep original IPTV fields and move legacy enriched values into TMDB-specific columns. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("movies", "series")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN tmdbTitle TEXT")
            db.execSQL("ALTER TABLE $table ADD COLUMN tmdbOverview TEXT")
            db.execSQL("ALTER TABLE $table ADD COLUMN tmdbReleaseDate TEXT")
            db.execSQL("UPDATE $table SET tmdbOverview = overview, tmdbReleaseDate = releaseDate WHERE metadataUpdatedAt IS NOT NULL")
        }
        // Previous movie enrichment replaced the IPTV title in-place. The original title
        // cannot be reconstructed reliably; retain the current title as the IPTV baseline.
    }
}
