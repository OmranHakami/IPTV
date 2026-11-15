package com.oh.tv.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamGatewayTest {
    @Test fun authenticatesLegacyStringFieldsAndEncodesCredentials() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":"1","status":"Active","exp_date":"1800000000","allowed_output_formats":["m3u8"]}}"""))
        server.start()
        try {
            val result = XtreamGateway(OkHttpClient()).authenticate(server.url("/").toString(), "name@example.com", "p/a ss").getOrThrow()
            assertTrue(result.active)
            assertEquals(1_800_000_000L, result.expiresAtEpochSeconds)
            val request = server.takeRequest()
            assertEquals("name@example.com", request.requestUrl?.queryParameter("username"))
            assertEquals("p/a ss", request.requestUrl?.queryParameter("password"))
        } finally { server.shutdown() }
    }

    @Test fun requestsSeriesDetailsWithSeriesId() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"info":{"name":"Example"},"episodes":{}}"""))
        server.start()
        try {
            val root = XtreamGateway(OkHttpClient()).seriesInfo(server.url("/").toString(), "u", "p", "42")
            assertEquals("Example", root["info"].asMap()["name"])
            val url = server.takeRequest().requestUrl
            assertEquals("get_series_info", url?.queryParameter("action"))
            assertEquals("42", url?.queryParameter("series_id"))
        } finally { server.shutdown() }
    }
}
