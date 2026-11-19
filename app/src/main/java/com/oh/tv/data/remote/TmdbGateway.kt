package com.oh.tv.data.remote

import com.squareup.moshi.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class TmdbGateway(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
) {
    val enabled: Boolean get() = apiKey.isNotBlank()

    suspend fun movie(id: Long): Result<TmdbMetadata> = details("movie", id)
    suspend fun series(id: Long): Result<TmdbMetadata> = details("tv", id)

    suspend fun matchMovie(providerId: Long?, name: String, year: Int?): TmdbMetadata? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        if (providerId != null && providerId > 0) {
            val result = movie(providerId)
            val error = result.exceptionOrNull()
            if (error == null) return@withContext result.getOrThrow()
            if (error !is MovieNotFound) throw error
        }
        val (title, releaseYear) = movieSearchTerms(name, year)
        if (title.isBlank()) return@withContext null
        val url = "https://api.themoviedb.org/3/search/movie".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("query", title)
            .apply { releaseYear?.let { addQueryParameter("primary_release_year", it.toString()) } }
            .build()
        val id = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "TMDB search failed (${response.code})" }
            val root = JsonReader.of(requireNotNull(response.body).source()).readJsonValue().asMap()
            if ((root["total_pages"].asInt() ?: 0) > 1) return@withContext null
            uniqueMovieMatch(root["results"].asList(), title, releaseYear)
        } ?: return@withContext null
        val result = movie(id)
        if (result.exceptionOrNull() is MovieNotFound) null else result.getOrThrow()
    }

    private suspend fun details(type: String, id: Long): Result<TmdbMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            check(enabled) { "TMDB is not configured" }
            val url = "https://api.themoviedb.org/3/".toHttpUrl().newBuilder()
                .addPathSegment(type).addPathSegment(id.toString())
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("language", "en-US")
                .build()
            client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { response ->
                if (type == "movie" && response.code == 404) throw MovieNotFound()
                check(response.isSuccessful) { "TMDB request failed (${response.code})" }
                val root = JsonReader.of(requireNotNull(response.body).source()).readJsonValue().asMap()
                val title = (if (type == "movie") root["title"] else root["name"]).asString().orEmpty()
                val date = (if (type == "movie") root["release_date"] else root["first_air_date"]).asString()
                TmdbMetadata(
                    id = root["id"].asLong() ?: id,
                    title = title,
                    overview = root["overview"].asString(),
                    releaseDate = date,
                    runtimeMinutes = root["runtime"].asInt(),
                    rating = root["vote_average"].asDouble(),
                    voteCount = root["vote_count"].asInt(),
                    genres = root["genres"].asList().mapNotNull { it.asMap()["name"].asString() }.joinToString().ifBlank { null },
                    posterPath = root["poster_path"].asString(),
                    backdropPath = root["backdrop_path"].asString(),
                    imdbId = root["imdb_id"].asString(),
                )
            }
        }
    }
}

private class MovieNotFound : Exception()

private val providerSuffix = Regex("""(?:[\s.\-]+|\s*[\[(])(?:4k|uhd|1080p|720p|dual[ .-]+audio)[\])]?\s*$""", RegexOption.IGNORE_CASE)
private val parenthesizedYear = Regex("""\s*\(((?:19|20)\d{2})\)\s*$""")
private val titleSeparators = Regex("""[^\p{L}\p{N}]+""")

internal fun movieSearchTerms(name: String, year: Int?): Pair<String, Int?> {
    var title = name.trim()
    while (true) {
        val clean = title.replace(providerSuffix, "").trim()
        if (clean == title) break
        title = clean
    }
    val suffix = parenthesizedYear.find(title)
    return title.replace(parenthesizedYear, "").trim() to (year ?: suffix?.groupValues?.get(1)?.toIntOrNull())
}

internal fun uniqueMovieMatch(results: List<Any?>, title: String, year: Int?): Long? {
    fun normalized(value: String?) = value.orEmpty().lowercase(Locale.ROOT).replace(titleSeparators, " ").trim()
    val target = normalized(title)
    if (target.isEmpty()) return null
    return results.mapNotNull { result ->
        val row = result.asMap()
        val matchesTitle = target == normalized(row["title"].asString()) || target == normalized(row["original_title"].asString())
        val matchesYear = year == null || year == row["release_date"].asString()?.take(4)?.toIntOrNull()
        row["id"].asLong()?.takeIf { it > 0 && matchesTitle && matchesYear }
    }.distinct().singleOrNull()
}

