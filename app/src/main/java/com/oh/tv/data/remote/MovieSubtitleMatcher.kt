package com.oh.tv.data.remote

/**
 * Resolves a movie's identity for an online subtitle search.
 * The subtitle provider is queried first; TMDB is only used when the initial
 * subtitle lookup has no results. TMDB supplies metadata, not subtitle files.
 */
data class SubtitleMovie(val title: String, val year: Int? = null, val imdbId: String? = null, val tmdbId: Long? = null)
data class SubtitleMatch(val id: String, val language: String, val release: String? = null)

internal fun SubtitleMovie.searchParameters(): Map<String, String> = when {
    !imdbId.isNullOrBlank() -> mapOf("imdb_id" to imdbId.removePrefix("tt"))
    tmdbId != null -> mapOf("tmdb_id" to tmdbId.toString())
    year != null -> mapOf("query" to title, "year" to year.toString())
    else -> mapOf("query" to title)
}

interface SubtitleLookup {
    suspend fun search(movie: SubtitleMovie, languages: List<String>): List<SubtitleMatch>
}

interface MovieIdentityLookup {
    suspend fun resolve(movie: SubtitleMovie): SubtitleMovie?
}

class MovieSubtitleMatcher(
    private val subtitles: SubtitleLookup,
    private val identities: MovieIdentityLookup,
) {
    private val results = LinkedHashMap<Pair<Map<String, String>, List<String>>, Pair<Long, List<SubtitleMatch>>>()

    suspend fun find(movie: SubtitleMovie, languages: List<String>): List<SubtitleMatch> {
        require(movie.title.isNotBlank() || movie.imdbId != null || movie.tmdbId != null)
        require(languages.isNotEmpty())
        val key = movie.searchParameters()
        val languageKey = languages.distinct().sorted()
        val cacheKey = key to languageKey
        results[cacheKey]?.let { (expiresAt, matches) ->
            if (System.nanoTime() < expiresAt) return matches
            results.remove(cacheKey)
        }
        val first = subtitles.search(movie, languageKey)
        val resolved = if (first.isEmpty()) identities.resolve(movie) else null
        val resolvedKey = resolved?.searchParameters()?.takeIf { it != key }?.let { it to languageKey }
        val matches = if (resolved != null && resolvedKey != null) {
            val cached = results[resolvedKey]?.takeIf { (expiresAt, _) -> System.nanoTime() < expiresAt }
            cached?.second ?: subtitles.search(resolved, languageKey)
        } else first
        val entry = System.nanoTime() + 5 * 60_000_000_000L to matches
        for (resultKey in listOfNotNull(cacheKey, resolvedKey)) {
            if (resultKey !in results && results.size >= 32) results.remove(results.keys.first())
            results[resultKey] = entry
        }
        return matches
    }
}
