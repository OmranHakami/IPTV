package com.oh.tv.data.remote

import com.squareup.moshi.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Retrieves only official YouTube trailer keys; does not mutate IPTV or TMDB catalog records. */
class TmdbTrailerGateway(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
) {
    suspend fun trailerKey(type: String, tmdbId: Long): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || tmdbId <= 0 || type !in setOf("movie", "tv")) return@withContext null
        runCatching {
            val url = "https://api.themoviedb.org/3/".toHttpUrl().newBuilder()
                .addPathSegment(type).addPathSegment(tmdbId.toString()).addPathSegment("videos")
                .addQueryParameter("api_key", apiKey).addQueryParameter("language", "en-US").build()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = JsonReader.of(requireNotNull(response.body).source()).readJsonValue() as? Map<*, *>
                val videos = root?.get("results") as? List<*> ?: return@use null
                videos.asSequence().mapNotNull { it as? Map<*, *> }
                    .filter { it["site"] == "YouTube" && it["type"] == "Trailer" }
                    .sortedByDescending { it["official"] == true }
                    .mapNotNull { it["key"] as? String }
                    .firstOrNull { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
            }
        }.getOrNull()
    }
}
