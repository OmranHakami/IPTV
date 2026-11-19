package com.oh.tv.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
    fun normalizesMixedCaseHttpAndHttpsSchemes() {
        assertEquals("http://example.com/panel", UrlNormalizer.normalize("HTTP://example.com/panel/"))
        assertEquals("https://example.com:8443/panel", UrlNormalizer.normalize("HtTpS://example.com:8443/panel/?x=1"))
    }

    @Test
    fun acceptsAtSignsInPathQueryAndFragment() {
        assertEquals(
            "https://example.com/panel@provider",
            UrlNormalizer.normalize("https://example.com/panel@provider?key=@example#@fragment"),
        )
    }

    @Test
    fun rejectsServerUrlsWithUserInfo() {
        val urls = listOf(
            "https://user@example.com/panel",
            "http://user:password@example.com:8443/panel",
            "https://:password@example.com/panel",
            "HTTP://user@example.com/panel",
            "HtTpS://user:password@example.com:8443/panel",
            "HtTp://:password@example.com/panel",
            "https://@example.com/panel",
            "https://:@example.com/panel",
            "https:///\\@example.com/panel",
        )
        for (url in urls) {
            val error = assertThrows(IllegalArgumentException::class.java) { UrlNormalizer.normalize(url) }
            assertEquals("Enter a valid server URL", error.message)
        }
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
