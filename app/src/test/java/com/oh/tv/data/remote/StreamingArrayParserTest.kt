package com.oh.tv.data.remote

import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingArrayParserTest {
    @Test fun largePayloadIsConsumedInBoundedBatches() = runBlocking {
        val itemCount = 25_000
        val source = Buffer().writeUtf8(
            buildString {
                append('[')
                repeat(itemCount) { index ->
                    if (index > 0) append(',')
                    append("{\"stream_id\":").append(index).append(",\"name\":\"Channel ").append(index).append("\"}")
                }
                append(']')
            },
        )
        var processed = 0
        var largestBatch = 0

        StreamingArrayParser.forEachBatchSuspending(source) { batch ->
            processed += batch.size
            largestBatch = maxOf(largestBatch, batch.size)
        }

        assertEquals(itemCount, processed)
        assertTrue(largestBatch <= 300)
    }
}
