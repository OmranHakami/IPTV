package com.oh.tv.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMovieMatchTest {
    @Test fun stripsProviderSuffixesAndExtractsYear() {
        assertEquals("Dune" to 2021, movieSearchTerms("Dune (2021) [4K] - DUAL AUDIO", null))
        assertEquals("2001: A Space Odyssey" to null, movieSearchTerms("2001: A Space Odyssey", null))
    }

    @Test fun onlyAcceptsAnUnambiguousTitleAndYear() {
        val results = listOf(
            mapOf("id" to 1, "title" to "Dune", "release_date" to "1984-12-14"),
            mapOf("id" to 2, "original_title" to "Dune", "release_date" to "2021-10-22"),
            mapOf("id" to 3, "title" to "Dune: Part Two", "release_date" to "2024-03-01"),
        )
        assertEquals(2L, uniqueMovieMatch(results, "DUNE", 2021))
        assertNull(uniqueMovieMatch(results, "Dune", null))
        assertNull(uniqueMovieMatch(results, "Dune", 2024))
    }

    @Test fun fallsBackToTitleForMissingIdButNotForAnOutage() = runBlocking {
        val requests = mutableListOf<String>()
        var status = 404
        val gateway = TmdbGateway("test", OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            requests += path
            val (code, body) = when (path) {
                "/3/movie/9" -> status to "{}"
                "/3/search/movie" -> {
                    assertEquals("Dune", request.url.queryParameter("query"))
                    assertEquals("2021", request.url.queryParameter("primary_release_year"))
                    200 to """{"total_pages":1,"results":[{"id":42,"title":"Dune","release_date":"2021-10-22"}]}"""
                }
                else -> 200 to """{"id":42,"title":"Dune","imdb_id":"tt123"}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody()).build()
        }.build())
        assertEquals("tt123", gateway.matchMovie(9, "Dune (2021) [4K]", null)?.imdbId)
        assertEquals(listOf("/3/movie/9", "/3/search/movie", "/3/movie/42"), requests)
        status = 503
        requests.clear()
        assertTrue(runCatching { gateway.matchMovie(9, "Dune", 2021) }.isFailure)
        assertEquals(listOf("/3/movie/9"), requests)
    }
}
