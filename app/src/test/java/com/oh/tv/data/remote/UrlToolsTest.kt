package com.oh.tv.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlToolsTest {
    @Test
    fun normalizesServerAndRemovesPathsAndQueries() {
        assertEquals("https://example.com:8443", UrlNormalizer.normalize(" example.com:8443/player_api.php?x=1 "))
    }

    @Test
    fun preservesProviderBasePath() {
        assertEquals("http://example.com/panel", UrlNormalizer.normalize("http://example.com/panel/"))
    }

    @Test
    fun constructsEncodedPlaybackUrl() {
        val url = PlaybackUrlFactory().movie(
            base = "https://example.com:8443",
            username = "name@example.com",
            password = "p/a ss",
            streamId = "42",
            extension = "MKV",
        ).toString()
        assertTrue(url.startsWith("https://example.com:8443/movie/"))
        assertTrue(url.endsWith("/42.mkv"))
        assertFalse(url.contains("p/a ss"))
    }

    @Test
    fun redactsQueryAndPathCredentials() {
        val raw = "http://host/live/user/secret/42.ts?username=user&password=secret"
        val safe = SensitiveRedactor.redact(raw)
        assertFalse(safe.contains("secret"))
        assertFalse(safe.contains("/user/"))
        assertTrue(safe.contains("[REDACTED]"))
    }
}
