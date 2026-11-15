package com.oh.tv.data.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BoundedSubtitleDownloadTest {
    @Test fun acceptsExactlyAtLimit() {
        val bytes = ByteArray(16) { it.toByte() }
        assertArrayEquals(bytes, readBoundedSubtitle(ByteArrayInputStream(bytes), 16))
    }

    @Test fun rejectsOversizedBodyEvenWithoutContentLength() {
        assertThrows(IllegalStateException::class.java) {
            readBoundedSubtitle(ByteArrayInputStream(ByteArray(17)), 16)
        }
    }

    @Test fun rejectsOversizedStreamWithoutReadingEntireResponse() {
        var readCount = 0
        val stream = object : InputStream() {
            override fun read(): Int {
                readCount++
                return 1
            }
        }
        assertThrows(IllegalStateException::class.java) {
            readBoundedSubtitle(stream, 16)
        }
        org.junit.Assert.assertTrue(readCount <= 8193)
    }

    @Test fun acceptsEmptyBody() {
        assertArrayEquals(byteArrayOf(), readBoundedSubtitle(ByteArrayInputStream(byteArrayOf()), 16))
    }
}
