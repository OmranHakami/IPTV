package com.oh.tv.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieSubtitleMatcherTest {
    @Test fun directSubtitleMatchDoesNotCallTmdb() = runBlocking {
        var metadataCalls = 0
        var searches = 0
        val matcher = MovieSubtitleMatcher(
            object : SubtitleLookup {
                override suspend fun search(movie: SubtitleMovie, languages: List<String>): List<SubtitleMatch> {
                    searches++
                    return listOf(SubtitleMatch("subtitle-1", "ar"))
                }
            },
            object : MovieIdentityLookup {
                override suspend fun resolve(movie: SubtitleMovie): SubtitleMovie? {
                    metadataCalls++
                    return movie.copy(imdbId = "tt1234567")
                }
            },
        )
        assertEquals(1, matcher.find(SubtitleMovie("Example", 2024), listOf("ar")).size)
        assertEquals(1, matcher.find(SubtitleMovie("Example", 2024), listOf("ar")).size)
        assertEquals(1, searches)
        matcher.find(SubtitleMovie("Example", 2024), listOf("en"))
        assertEquals(2, searches)
        assertEquals(0, metadataCalls)
    }

    @Test fun noResultsRetriesWithResolvedImdbIdentity() = runBlocking {
        val searched = mutableListOf<SubtitleMovie>()
        val matcher = MovieSubtitleMatcher(
            object : SubtitleLookup {
                override suspend fun search(movie: SubtitleMovie, languages: List<String>): List<SubtitleMatch> {
                    searched += movie
                    return if (movie.imdbId == "tt1234567") listOf(SubtitleMatch("subtitle-2", "en")) else emptyList()
                }
            },
            object : MovieIdentityLookup {
                override suspend fun resolve(movie: SubtitleMovie) = movie.copy(imdbId = "tt1234567")
            },
        )
        assertEquals("subtitle-2", matcher.find(SubtitleMovie("Example"), listOf("en")).single().id)
        assertEquals("subtitle-2", matcher.find(SubtitleMovie("Example", imdbId = "tt1234567"), listOf("en")).single().id)
        assertEquals(listOf(null, "tt1234567"), searched.map { it.imdbId })
    }

    @Test fun unresolvedOrUnchangedIdentityDoesNotRepeatSearch() = runBlocking {
        var searches = 0
        val movie = SubtitleMovie("Example", 2024, tmdbId = 42)
        val matcher = MovieSubtitleMatcher(
            object : SubtitleLookup {
                override suspend fun search(movie: SubtitleMovie, languages: List<String>): List<SubtitleMatch> {
                    searches++
                    return emptyList()
                }
            },
            object : MovieIdentityLookup {
                override suspend fun resolve(movie: SubtitleMovie) = movie.copy(title = "Localized title")
            },
        )
        assertEquals(emptyList<SubtitleMatch>(), matcher.find(movie, listOf("ar")))
        assertEquals(1, searches)
    }
}
