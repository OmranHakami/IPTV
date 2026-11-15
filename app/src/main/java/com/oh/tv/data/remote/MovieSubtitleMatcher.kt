package com.oh.tv.data.remote

/**
 * Resolves a movie's identity for an online subtitle search.
 * The subtitle provider is queried first; TMDB is only used when the initial
 * subtitle lookup has no results. TMDB supplies metadata, not subtitle files.
 */
data class SubtitleMovie(val title: String, val year: Int? = null, val imdbId: String? = null, val tmdbId: Long? = null)
data class SubtitleMatch(val id: String, val language: String, val release: String? = null)

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
    suspend fun find(movie: SubtitleMovie, languages: List<String>): List<SubtitleMatch> {
        require(movie.title.isNotBlank() || movie.imdbId != null || movie.tmdbId != null)
        require(languages.isNotEmpty())
        val first = subtitles.search(movie, languages)
        if (first.isNotEmpty()) return first

        val resolved = identities.resolve(movie) ?: return emptyList()
        // Avoid a duplicate request when metadata resolution did not improve identity.
        if (resolved == movie) return emptyList()
        return subtitles.search(resolved, languages)
    }
}
