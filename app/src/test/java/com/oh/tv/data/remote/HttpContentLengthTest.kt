package com.oh.tv.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpContentLengthTest {
    @Test fun validBodyLength() {
        assertEquals(HttpContentLength.Result.Valid(123), HttpContentLength.parse(listOf("content-length: 123"), true))
    }

    @Test fun postRequiresLength() {
        assertEquals(HttpContentLength.Result.Invalid, HttpContentLength.parse(emptyList(), true))
        assertEquals(HttpContentLength.Result.Valid(0), HttpContentLength.parse(emptyList(), false))
    }

    @Test fun malformedAndDuplicateLengthsAreRejected() {
        for (headers in listOf(
            listOf("Content-Length: abc"),
            listOf("Content-Length: -1"),
            listOf("Content-Length: +1"),
            listOf("Content-Length: 1", "content-length: 2"),
            listOf("Content-Length: 999999999999999999999999"),
            listOf("Content-Length: 1, 2")
        )) assertEquals(HttpContentLength.Result.Invalid, HttpContentLength.parse(headers, true))
    }

    @Test fun oversizedBodyIsRejected() {
        assertEquals(HttpContentLength.Result.TooLarge, HttpContentLength.parse(listOf("Content-Length: 32769"), true))
        assertEquals(HttpContentLength.Result.Valid(32768), HttpContentLength.parse(listOf("Content-Length: 32768"), true))
    }
}
