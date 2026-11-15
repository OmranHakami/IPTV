package com.oh.tv.data.remote

import com.squareup.moshi.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** OpenSubtitles.com API; callers must supply their own API key. */
class OpenSubtitlesGateway(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
) : SubtitleLookup {
    val enabled get() = apiKey.isNotBlank()

    override suspend fun search(movie: SubtitleMovie, languages: List<String>): List<SubtitleMatch> = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext emptyList()
        val url = "https://api.opensubtitles.com/api/v1/subtitles".toHttpUrl().newBuilder()
            .addQueryParameter("languages", languages.joinToString(","))
            .apply {
                if (!movie.imdbId.isNullOrBlank()) addQueryParameter("imdb_id", movie.imdbId.removePrefix("tt"))
                else if (movie.tmdbId != null) addQueryParameter("tmdb_id", movie.tmdbId.toString())
                else {
                    addQueryParameter("query", movie.title)
                    movie.year?.let { addQueryParameter("year", it.toString()) }
                }
            }.build()
        val request = Request.Builder().url(url).header("Api-Key", apiKey)
            .header("User-Agent", "OH-IPTV/0.3.1").header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Subtitle search failed: HTTP ${response.code}" }
            val root = JsonReader.of(requireNotNull(response.body).source()).readJsonValue().asMap()
            root["data"].asList().mapNotNull { entry ->
                val item = entry.asMap()
                val attributes = item["attributes"].asMap()
                val file = attributes["files"].asList().firstOrNull()?.asMap()
                val id = file?.get("file_id").asLong()?.toString() ?: return@mapNotNull null
                SubtitleMatch(id, attributes["language"].asString() ?: "", attributes["release"].asString())
            }
        }
    }

    /** Download URL is short-lived; consume immediately rather than storing it. */
    suspend fun download(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        check(enabled) { "OpenSubtitles API key is not configured" }
        val json = """{"file_id":${fileId.toLongOrNull() ?: error("Invalid subtitle file ID")}}"""
        val request = Request.Builder().url("https://api.opensubtitles.com/api/v1/download")
            .header("Api-Key", apiKey).header("User-Agent", "OH-IPTV/0.3.1")
            .post(json.toRequestBody("application/json".toMediaType())).build()
        val link = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Subtitle download request failed: HTTP ${response.code}" }
            JsonReader.of(requireNotNull(response.body).source()).readJsonValue().asMap()["link"].asString()
                ?: error("Subtitle download link missing")
        }
        val uri = link.toHttpUrl()
        check(uri.isHttps && (uri.host == "opensubtitles.com" || uri.host.endsWith(".opensubtitles.com"))) {
            "Unexpected subtitle download host"
        }
        client.newCall(Request.Builder().url(uri).build()).execute().use { response ->
            check(response.isSuccessful) { "Subtitle file download failed: HTTP ${response.code}" }
            val body = requireNotNull(response.body)
            check(body.contentLength() <= 2_000_000L) { "Subtitle file too large" }
            val bytes = body.bytes()
            check(bytes.size <= 2_000_000) { "Subtitle file too large" }
            bytes
        }
    }
}
