package com.oh.tv.data

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oh.tv.data.local.AccountEntity
import com.oh.tv.data.local.OhDatabase
import com.oh.tv.data.local.SeriesEpisodeEntity
import com.oh.tv.data.remote.LocalPairingServer
import com.oh.tv.data.remote.TmdbGateway
import com.oh.tv.data.remote.XtreamGateway
import com.oh.tv.security.CredentialVault
import com.oh.tv.security.XtreamCredentials
import com.oh.tv.ui.Destination
import com.oh.tv.ui.NavigationPolicy
import com.oh.tv.ui.OhViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CatalogSyncTest {
    private val account = AccountEntity("test", "Test", "http://example.test", "catalog-sync-test", "Active", null, 1)
    private lateinit var db: OhDatabase
    private lateinit var vault: CredentialVault

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OhDatabase::class.java).build()
        db.accountDao().upsert(account)
        vault = CredentialVault(context)
        vault.save(account.credentialAlias, XtreamCredentials("test", "test"))
    }

    @After fun tearDown() {
        vault.remove(account.credentialAlias)
        db.close()
    }

    private fun repository(reply: (String) -> String): OhRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = reply(requireNotNull(request.url.queryParameter("action")))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        return OhRepository(db, vault, XtreamGateway(client), TmdbGateway(""))
    }

    @Test fun failedOrCancelledBatchRestoresAllContentAndCategories() = runBlocking {
        fun row(id: Int, category: String) =
            """{"series_id":"$id","stream_id":"$id","category_id":"$category","name":"$category-$id"}"""
        var categories = """[{"category_id":"old"}]"""
        var content = "[${row(1, "old")}]"
        val repository = repository { action ->
            if (action.endsWith("_categories")) categories else content
        }
        val tables = listOf("live_channels", "movies", "series", "live_categories", "vod_categories")
        fun cachedRows() = tables.associateWith { table ->
            db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
                buildList { while (cursor.moveToNext()) add(List(cursor.columnCount) { cursor.getString(it) }) }
            }
        }
        assertTrue(repository.syncAll(account) {}.scopes.all { it.state == SyncState.Ready })
        val before = cachedRows()
        val ids = (1..301).map { it.toString().padStart(3, '0') }
        categories = ids.joinToString(prefix = "[", postfix = "]") { """{"category_id":"$it"}""" }
        content = (1..301).joinToString(prefix = "[", postfix = ",") { row(it, "001") }
        val processedBatch = ConcurrentHashMap.newKeySet<String>()
        val stale = repository.syncAll(account) {
            if (it.state == SyncState.Running && it.processed == 300) processedBatch.add(it.scope)
        }.scopes
        assertEquals(SyncScopes.userVisible.toSet(), processedBatch)
        assertTrue(stale.all { it.state == SyncState.Stale && it.processed == 1 })
        assertEquals(before, cachedRows())

        content = content.dropLast(1) + "]"
        val cancelled = launch {
            val job = coroutineContext.job
            repository.syncAll(account) { if (it.processed == 300) job.cancel() }
        }
        cancelled.join()
        assertTrue(cancelled.isCancelled)
        assertEquals(before, cachedRows())

        assertTrue(repository.syncAll(account) {}.scopes.all { it.processed == 301 && it.state == SyncState.Ready })
        assertEquals(ids, repository.liveCategories(account.id).first().map { it.providerId })
        for (kind in listOf("movie", "series")) {
            assertEquals(ids, repository.vodCategories(account.id, kind).first().map { it.providerId })
        }
        categories = "[]"
        content = "[]"
        assertTrue(repository.syncAll(account) {}.scopes.all { it.state == SyncState.Empty })
        assertTrue(cachedRows().values.all { it.isEmpty() })
    }

    @Test fun delayedDetailsUseTheCurrentSeriesAndDoNotRestoreRemovedRows() = runBlocking {
        val requested = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val repository = repository { action ->
            when (action) {
                "get_series" -> """[{"series_id":"s","category_id":"old"}]"""
                "get_series_info" -> {
                    requested.complete(Unit)
                    check(release.await(10, TimeUnit.SECONDS))
                    """{"episodes":{"1":[{"id":"e"}]}}"""
                }
                else -> "[]"
            }
        }
        repository.syncAll(account) {}
        val dao = db.catalogDao()
        val original = requireNotNull(dao.seriesItem(account.id, "s"))
        val pending = async { repository.loadSeriesDetails(account, original) }
        try {
            withTimeout(5_000) { requested.await() }
            dao.upsertSeries(listOf(original.copy(categoryId = "new")))
        } finally {
            release.countDown()
        }
        assertEquals("new", pending.await().categoryId)
        assertEquals("new", dao.seriesItem(account.id, "s")?.categoryId)

        dao.deleteAllSeries(account.id)
        dao.deleteEpisodes(account.id, "s")
        assertEquals(original, repository.loadSeriesDetails(account, original))
        assertNull(dao.seriesItem(account.id, "s"))
        assertTrue(repository.episodes(account.id, "s").first().isEmpty())
    }

    @Test fun malformedDetailsPreserveCachedEpisodesAndLoadedFlag() = runBlocking {
        var details = """{"episodes":{"1":[{"id":"001","title":"Cached"}]}}"""
        val repository = repository { action ->
            when (action) {
                "get_series" -> """[{"series_id":"s"}]"""
                "get_series_info" -> details
                else -> "[]"
            }
        }
        repository.syncAll(account) {}
        val dao = db.catalogDao()
        val series = requireNotNull(dao.seriesItem(account.id, "s"))
        repository.loadSeriesDetails(account, series)
        val episodes = repository.episodes(account.id, "s").first()
        assertEquals("001", episodes.single().providerId)
        dao.upsertSeries(listOf(series.copy(detailsLoaded = false)))
        for (invalid in listOf("{}", """{"episodes":[]}""", """{"episodes":{"1":[{}]}}""")) {
            details = invalid
            val error = runCatching { repository.loadSeriesDetails(account, series) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertEquals(series, dao.seriesItem(account.id, "s"))
            assertEquals(episodes, repository.episodes(account.id, "s").first())
        }
        details = """{"episodes":{}}"""
        repository.loadSeriesDetails(account, series)
        assertTrue(requireNotNull(dao.seriesItem(account.id, "s")).detailsLoaded)
        assertTrue(repository.episodes(account.id, "s").first().isEmpty())
    }

    @Test fun seriesDetailRetrySurvivesFailedEpisodePlaybackUntilRefreshSucceeds() = verifyDetailRetry(enterPlayer = false)

    @Test fun seriesDetailRetryCompletesInPlayerAndBackShowsRefreshedDetails() = verifyDetailRetry(enterPlayer = true)

    private fun verifyDetailRetry(enterPlayer: Boolean) = runBlocking {
        val release = CountDownLatch(1)
        val repository = repository { action ->
            when (action) {
                "get_series" -> """[{"series_id":"s","name":"Show"}]"""
                "get_series_info" -> {
                    check(release.await(10, TimeUnit.SECONDS))
                    """{"episodes":{"1":[{"id":"001","title":"Cached"}]}}"""
                }
                else -> "[]"
            }
        }
        repository.syncAll(account) {}
        val series = requireNotNull(db.catalogDao().seriesItem(account.id, "s"))
        val cached = SeriesEpisodeEntity(account.id, series.providerId, "001", 1, 1, "Cached", "mp4", null, null, null, 1)
        db.catalogDao().upsertEpisodes(listOf(cached))
        vault.remove(account.credentialAlias)
        val store = ViewModelStore()
        try {
            val model = withContext(Dispatchers.Main) {
                val pairing = LocalPairingServer(ApplicationProvider.getApplicationContext())
                ViewModelProvider(store, OhViewModel.Factory(repository, pairing))[OhViewModel::class.java]
            }
            withTimeout(5_000) { model.state.first { it.account?.id == account.id } }
            withContext(Dispatchers.Main) { model.openSeries(series) }
            val failedDetails = withTimeout(5_000) { model.state.first { it.seriesDetailsError != null } }
            withContext(Dispatchers.Main) { model.playEpisode(cached) }
            val failedPlay = withTimeout(5_000) { model.state.first { it.message != null } }
            assertEquals(failedDetails.seriesDetailsError, failedPlay.seriesDetailsError)
            assertEquals(cached, withTimeout(5_000) { model.episodes.first { it.isNotEmpty() }.single() })

            vault.save(account.credentialAlias, XtreamCredentials("test", "test"))
            repository.toggleFavorite(account.id, "series", series.providerId)
            withContext(Dispatchers.Main) {
                model.openSeries(series)
                assertEquals(failedDetails.seriesDetailsError, model.state.value.seriesDetailsError)
            }
            val duringRetry = if (enterPlayer) {
                withContext(Dispatchers.Main) { model.playEpisode(cached) }
                withTimeout(5_000) { model.state.first { it.destination == Destination.Player } }
            } else model.state.value
            assertTrue(duringRetry.seriesDetailsLoading)
            release.countDown()
            val refreshed = withTimeout(5_000) { model.state.first { !it.seriesDetailsLoading } }
            assertTrue(refreshed.selectedSeries?.detailsLoaded == true)
            assertEquals(duringRetry.copy(
                selectedSeries = refreshed.selectedSeries, favorite = true, seriesDetailsLoading = false, seriesDetailsError = null,
            ), refreshed)
            if (enterPlayer) {
                withContext(Dispatchers.Main) {
                    model.navigate(NavigationPolicy.back(refreshed.destination, refreshed.settingsReturn, refreshed.playerReturn, refreshed.browseReturn))
                }
                assertEquals(Destination.SeriesDetails, model.state.value.destination)
                assertNull(model.state.value.seriesDetailsError)
            }
        } finally {
            release.countDown()
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test fun duplicateSyncKeepsViewModelProgress() = runBlocking {
        val release = CountDownLatch(1)
        val repository = repository {
            check(release.await(10, TimeUnit.SECONDS))
            "[]"
        }
        val store = ViewModelStore()
        val pairing = LocalPairingServer(ApplicationProvider.getApplicationContext())
        try {
            val model = withContext(Dispatchers.Main) {
                ViewModelProvider(store, OhViewModel.Factory(repository, pairing))[OhViewModel::class.java]
            }
            withContext(Dispatchers.Main) { model.sync(account) }
            withTimeout(5_000) { model.state.first { it.syncProgress.size == 2 } }
            withContext(Dispatchers.Main) {
                val progress = model.state.value.syncProgress
                model.sync(account)
                assertEquals(progress, model.state.value.syncProgress)
                assertTrue(model.state.value.busy)
            }
            release.countDown()
            val finished = withTimeout(5_000) { model.state.first { !it.busy && it.syncProgress.size == 3 } }
            assertTrue(finished.syncProgress.all { it.state == SyncState.Empty })
        } finally {
            release.countDown()
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    @Test fun secondSyncWaitsUntilAllScopesFinish() = runBlocking {
        val repository = repository { "[]" }
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CountDownLatch(1)
        val first = async {
            repository.syncAll(account) { progress ->
                if (progress.state == SyncState.Running && firstStarted.complete(Unit)) {
                    check(releaseFirst.await(10, TimeUnit.SECONDS))
                }
            }
        }
        withTimeout(5_000) { firstStarted.await() }
        val secondStarted = CompletableDeferred<Unit>()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            repository.syncAll(account) { secondStarted.complete(Unit) }
        }
        try {
            assertNull(withTimeoutOrNull(500) { secondStarted.await() })
        } finally {
            releaseFirst.countDown()
        }
        assertTrue(first.await().scopes.all { it.state == SyncState.Empty })
        assertTrue(second.await().scopes.all { it.state == SyncState.Empty })
    }
}
