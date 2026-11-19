package com.oh.tv.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubtitleCacheTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun reusesDownloadedFileWithoutAnApiCall() = runBlocking {
        val file = directory.newFile("42.srt").apply { writeText("cached subtitle") }
        val client = OkHttpClient.Builder().addInterceptor { error("Unexpected network request") }.build()
        val gateway = OpenSubtitlesGateway("test", client)
        assertEquals(file, gateway.cachedDownload(directory.root, "42"))
        assertEquals("cached subtitle", file.readText())
    }
}
