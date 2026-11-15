package com.oh.tv

import android.content.Context
import androidx.room.Room
import com.oh.tv.data.OhRepository
import com.oh.tv.data.local.OhDatabase
import com.oh.tv.data.local.MIGRATION_2_3
import com.oh.tv.data.local.MIGRATION_3_4
import com.oh.tv.data.remote.PlaybackUrlFactory
import com.oh.tv.data.remote.LocalPairingServer
import com.oh.tv.data.remote.TmdbGateway
import com.oh.tv.data.remote.XtreamGateway
import com.oh.tv.security.CredentialVault

class AppContainer(context: Context) {
    val database: OhDatabase = Room.databaseBuilder(context, OhDatabase::class.java, "oh.db")
        .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
        .fallbackToDestructiveMigration()
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    val credentialVault = CredentialVault(context)
    val xtreamGateway = XtreamGateway()
    val tmdbGateway = TmdbGateway(BuildConfig.TMDB_API_KEY)
    val playbackUrlFactory = PlaybackUrlFactory()
    val localPairingServer = LocalPairingServer(context)
    val repository = OhRepository(database, credentialVault, xtreamGateway, tmdbGateway, playbackUrlFactory)
}
