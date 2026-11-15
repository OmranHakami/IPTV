package com.oh.tv.data.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class PairingRequestBodyTest {
    @Test fun exactBodyIsAccepted() {
        val bytes = "hello".toByteArray()
        assertArrayEquals(bytes, readExactBody(ByteArrayInputStream(bytes), 5))
    }

    @Test fun truncatedBodyIsRejected() {
        assertNull(readExactBody(ByteArrayInputStream("abc".toByteArray()), 5))
    }

    @Test fun emptyBodyIsAccepted() {
        assertArrayEquals(byteArrayOf(), readExactBody(ByteArrayInputStream(byteArrayOf()), 0))
    }

    @Test fun partialReadsAreAssembled() {
        val input = object : InputStream() {
            private val source = ByteArrayInputStream("abcdef".toByteArray())
            override fun read(): Int = source.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                source.read(b, off, minOf(len, 2))
        }
        assertArrayEquals("abcdef".toByteArray(), readExactBody(input, 6))
    }
}
