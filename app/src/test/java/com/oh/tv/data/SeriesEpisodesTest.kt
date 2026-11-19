package com.oh.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesEpisodesTest {
    @Test fun onlyAnExplicitEmptyMapClearsEpisodes() {
        assertTrue(parseSeriesEpisodes(emptyMap<String, Any?>(), "a", "s").isEmpty())
        val valid = mapOf("id" to "e")
        val malformed = listOf(
            null,
            emptyList<Any?>(),
            mapOf("1" to "unavailable"),
            mapOf("1" to emptyList<Any?>()),
            mapOf("1" to listOf(mapOf("title" to "Missing ID"))),
            mapOf("1" to listOf(valid, mapOf("id" to ""))),
        )
        for (payload in malformed) {
            assertThrows(IllegalArgumentException::class.java) { parseSeriesEpisodes(payload, "a", "s") }
        }
    }

    @Test fun providerIdsAndSeasonFallbackSurviveParsing() {
        val payload = mapOf("2" to listOf(mapOf("id" to 123.0, "episode_num" to "3"), mapOf("id" to "004")))
        val episodes = parseSeriesEpisodes(payload, "a", "s")
        assertEquals(listOf("123", "004"), episodes.map { it.providerId })
        assertEquals(2, episodes.first().seasonNumber)
        assertEquals("Episode 3", episodes.first().title)
    }
}
