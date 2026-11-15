package com.oh.tv.data

object PlaybackPolicy {
    fun isCompleted(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs.coerceAtLeast(0) >= durationMs * 0.9

    fun resumePosition(positionMs: Long, durationMs: Long, completed: Boolean): Long = when {
        completed || durationMs <= 0 -> 0L
        positionMs < 5_000 -> 0L
        positionMs >= durationMs -> 0L
        else -> positionMs
    }
}
