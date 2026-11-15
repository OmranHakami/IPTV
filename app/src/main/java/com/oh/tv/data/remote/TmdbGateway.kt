package com.oh.tv.data.remote

import com.squareup.moshi.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** Resolve movie metadata only when subtitle lookup has no direct match. */
    suspend fun resolveSubtitleMovie(movie: SubtitleMovie): SubtitleMovie? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        val url = "https://api.themoviedb.org/3/".toHttpUrl().newBuilder().apply {
            if (movie.tmdbId != null) {
                addPathSegment("movie").addPathSegment(movie.tmdbId.toString())
                addQueryParameter("append_to_response", "external_ids")
            } else {
                addPathSegment("search").addPathSegment("movie")
                addQueryParameter("query", movie.title)
                movie.year?.let { addQueryParameter("year", it.toString()) }
            }
            addQueryParameter("api_key", apiKey)
        }.build()
        val candidate = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val root = JsonReader.of(requireNotNull(response.body).source()).readJsonValue().asMap()
            if (movie.tmdbId != null) root else root["results"].asList().firstOrNull()?.asMap() ?: return@withContext null
        }
        val id = candidate["id"].asLong() ?: return@withContext null
        val imdb = if (movie.tmdbId != null) candidate["imdb_id"].asString()
            ?: candidate["external_ids"].asMap()["imdb_id"].asString()
        else client.newCall(Request.Builder().url("https://api.themoviedb.org/3/movie/$id/external_ids".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey).build()).build()).execute().use { response ->
            if (response.isSuccessful) JsonReader.of(requireNotNull(response.body).source()).readJsonValue().asMap()["imdb_id"].asString() else null
        }
        movie.copy(
            title = candidate["title"].asString() ?: movie.title,
            year = candidate["release_date"].asString()?.take(4)?.toIntOrNull() ?: movie.year,
            tmdbId = id,
            imdbId = imdb ?: movie.imdbId,
        )
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
                )
            }
        }
    }
}

