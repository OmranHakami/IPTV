package com.oh.tv.data.remote

/** Strictly parses the one request body length supported by the local pairing HTTP server. */
internal object HttpContentLength {
    sealed interface Result {
        data class Valid(val value: Int) : Result
        data object Invalid : Result
        data object TooLarge : Result
    }

    fun parse(headers: List<String>, required: Boolean, maxBytes: Int = 32 * 1024): Result {
        val values = headers.mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon < 0) return@mapNotNull null
            if (line.substring(0, colon).trim().equals("Content-Length", ignoreCase = true)) {
                line.substring(colon + 1).trim()
            } else null
        }
        if (values.isEmpty()) return if (required) Result.Invalid else Result.Valid(0)
        if (values.size != 1) return Result.Invalid
        val value = values.single()
        if (value.isEmpty() || !value.all { it in '0'..'9' }) return Result.Invalid
        val parsed = value.toLongOrNull() ?: return Result.Invalid
        if (parsed > maxBytes) return Result.TooLarge
        return Result.Valid(parsed.toInt())
    }
}
