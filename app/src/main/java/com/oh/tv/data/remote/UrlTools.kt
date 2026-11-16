package com.oh.tv.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlNormalizer {
    fun normalize(raw: String): String {
        val value = raw.trim().trimEnd('/')
        val withScheme = if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"
        val url = withScheme.toHttpUrlOrNull() ?: throw IllegalArgumentException("Enter a valid server URL")
        require(url.host.isNotBlank()) { "Enter a valid server URL" }
        val segments = url.pathSegments.filter { it.isNotBlank() }.toMutableList()
        if (segments.lastOrNull().equals("player_api.php", ignoreCase = true)) segments.removeAt(segments.lastIndex)
        val builder = url.newBuilder().encodedPath("/").query(null).fragment(null)
        segments.forEach(builder::addPathSegment)
        return builder.build().toString().trimEnd('/')
    }
}

class PlaybackUrlFactory {
    fun live(base: String, username: String, password: String, streamId: String, extension: String): HttpUrl =
        media(base, "live", username, password, streamId, extension)

    fun movie(base: String, username: String, password: String, streamId: String, extension: String): HttpUrl =
        media(base, "movie", username, password, streamId, extension)

    fun episode(base: String, username: String, password: String, streamId: String, extension: String): HttpUrl =
        media(base, "series", username, password, streamId, extension)

    private fun media(base: String, kind: String, username: String, password: String, id: String, extension: String): HttpUrl {
        val safeExtension = extension.lowercase().filter(Char::isLetterOrDigit).ifBlank { "m3u8" }
        return requireNotNull(base.toHttpUrlOrNull()).newBuilder()
            .addPathSegment(kind)
            .addPathSegment(username)
            .addPathSegment(password)
            .addPathSegment("$id.$safeExtension")
            .build()
    }
}

object SensitiveRedactor {
    private val querySecret = Regex("(?i)(username|password)=([^&\\s]+)")
    private val pathSecret = Regex("(?i)/(live|movie|series)/[^/]+/[^/]+/")

    fun redact(value: String): String = value
        .replace(querySecret) { "${it.groupValues[1]}=[REDACTED]" }
        .replace(pathSecret) { "/${it.groupValues[1]}/[REDACTED]/[REDACTED]/" }
}
