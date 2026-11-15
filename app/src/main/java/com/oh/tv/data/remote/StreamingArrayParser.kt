package com.oh.tv.data.remote

import com.squareup.moshi.JsonReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.BufferedSource

object StreamingArrayParser {
    suspend fun forEachBatchSuspending(
        source: BufferedSource,
        batchSize: Int = 300,
        consume: suspend (List<Map<String, Any?>>) -> Unit,
    ) {
        require(batchSize in 50..2_000)
        val reader = JsonReader.of(source)
        val batch = ArrayList<Map<String, Any?>>(batchSize)
        reader.beginArray()
        while (reader.hasNext()) {
            currentCoroutineContext().ensureActive()
            val item = reader.readJsonValue().asMap()
            if (item.isNotEmpty()) batch += item
            if (batch.size >= batchSize) {
                consume(batch.toList())
                batch.clear()
            }
        }
        reader.endArray()
        if (batch.isNotEmpty()) consume(batch)
    }
}
