package com.oh.tv.data.remote

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

internal fun validatedPairingChunks(array: JSONArray): List<String>? {
    if (array.length() !in 1..16) return null
    return (0 until array.length()).map { index ->
        val value = array.opt(index)
        if (value !is String || value.length !in 300..400) return null
        value
    }
}

data class LocalPairingSession(
    val code: String,
    val linkUrl: String,
    val expiresAtEpochMs: Long,
)

class LocalPairingServer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val random = SecureRandom()
    private var socket: ServerSocket? = null
    private var activeCode: String? = null
    private var modulus: String = ""
    private var exponent: String = ""
    private var expiresAt: Long = 0
    private var submissions = Channel<List<String>>(1)

    suspend fun start(modulusBase64: String, publicExponent: String): LocalPairingSession = withContext(Dispatchers.IO) {
        stop()
        val address = localIpv4Address() ?: error("Connect the TV to the same Wi-Fi as your phone")
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(LOCAL_PORT))
        }
        val code = buildString(6) { repeat(6) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) } }
        this@LocalPairingServer.socket = server
        this@LocalPairingServer.activeCode = code
        this@LocalPairingServer.modulus = modulusBase64
        this@LocalPairingServer.exponent = publicExponent
        this@LocalPairingServer.expiresAt = System.currentTimeMillis() + SESSION_MS
        this@LocalPairingServer.submissions = Channel(1)
        scope.launch { acceptLoop(server) }
        scope.launch { delay(SESSION_MS); if (socket === server) stop() }
        LocalPairingSession(
            code = "${code.take(3)}-${code.takeLast(3)}",
            linkUrl = "http://${address.hostAddress}:${server.localPort}",
            expiresAtEpochMs = expiresAt,
        )
    }

    suspend fun awaitSubmission(session: LocalPairingSession): List<String>? {
        val remaining = (session.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(1)
        return withTimeoutOrNull(remaining) { submissions.receiveCatching().getOrNull() }
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        activeCode = null
        modulus = ""
        exponent = ""
        expiresAt = 0
        submissions.close()
    }

    private suspend fun acceptLoop(server: ServerSocket) {
        while (scope.isActive && !server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            scope.launch { client.use(::handleClient) }
        }
    }

    private fun handleClient(client: Socket) {
        client.soTimeout = 8_000
        val input = BufferedInputStream(client.getInputStream())
        val output = BufferedOutputStream(client.getOutputStream())
        val headerBytes = readHeaders(input) ?: return respond(output, 400, "text/plain", "Bad request".toByteArray())
        val headerText = headerBytes.toString(Charsets.US_ASCII)
        val lines = headerText.split("\r\n")
        val request = lines.firstOrNull()?.split(' ') ?: return respond(output, 400, "text/plain", byteArrayOf())
        if (request.size < 2) return respond(output, 400, "text/plain", byteArrayOf())
        val method = request[0]
        val rawTarget = request[1]
        val path = rawTarget.substringBefore('?')
        val query = rawTarget.substringAfter('?', "").split('&').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8") else null
        }.toMap()
        val contentLength = lines.firstOrNull { it.startsWith("Content-Length:", true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        if (contentLength !in 0..MAX_BODY_BYTES) return respond(output, 413, "text/plain", byteArrayOf())
        val body = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = input.read(body, read, contentLength - read)
            if (count < 0) break
            read += count
        }
        when {
            method == "GET" && path == "/" -> asset(output, "pairing/index.html", "text/html; charset=utf-8")
            method == "GET" && path == "/link.css" -> asset(output, "pairing/link.css", "text/css; charset=utf-8")
            method == "GET" && path == "/link.js" -> asset(output, "pairing/link.js", "text/javascript; charset=utf-8")
            method == "GET" && path == "/session" -> sessionInfo(output, query["code"].orEmpty())
            method == "POST" && path == "/submit" -> submit(output, body)
            else -> respond(output, 404, "application/json", "{\"error\":\"not_found\"}".toByteArray())
        }
    }

    private fun sessionInfo(output: BufferedOutputStream, code: String) {
        if (!validCode(code)) return respond(output, 404, "application/json", "{\"error\":\"invalid_code\"}".toByteArray())
        val payload = JSONObject()
            .put("modulus", modulus)
            .put("exponent", exponent)
            .put("expiresAt", expiresAt)
            .toString().toByteArray()
        respond(output, 200, "application/json", payload)
    }

    private fun submit(output: BufferedOutputStream, body: ByteArray) {
        val json = runCatching { JSONObject(body.toString(Charsets.UTF_8)) }.getOrNull()
            ?: return respond(output, 400, "application/json", "{\"error\":\"invalid_json\"}".toByteArray())
        if (!validCode(json.optString("code"))) return respond(output, 404, "application/json", "{\"error\":\"invalid_code\"}".toByteArray())
        val array: JSONArray = json.optJSONArray("chunks")
            ?: return respond(output, 400, "application/json", "{\"error\":\"invalid_payload\"}".toByteArray())
        val chunks = validatedPairingChunks(array)
            ?: return respond(output, 400, "application/json", "{\"error\":\"invalid_payload\"}".toByteArray())
        if (!submissions.trySend(chunks).isSuccess) return respond(output, 409, "application/json", "{\"error\":\"already_submitted\"}".toByteArray())
        respond(output, 202, "application/json", "{\"accepted\":true}".toByteArray())
    }

    private fun validCode(value: String): Boolean {
        if (System.currentTimeMillis() >= expiresAt) return false
        val expected = activeCode?.toByteArray() ?: return false
        val actual = value.uppercase().replace("-", "").replace(" ", "").toByteArray()
        return MessageDigest.isEqual(expected, actual)
    }

    private fun asset(output: BufferedOutputStream, name: String, contentType: String) {
        val bytes = appContext.assets.open(name).use { it.readBytes() }
        respond(output, 200, contentType, bytes)
    }

    private fun respond(output: BufferedOutputStream, status: Int, contentType: String, body: ByteArray) {
        val reason = when (status) { 200 -> "OK"; 202 -> "Accepted"; 400 -> "Bad Request"; 404 -> "Not Found"; 409 -> "Conflict"; 413 -> "Payload Too Large"; else -> "Error" }
        val headers = "HTTP/1.1 $status $reason\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'\r\nConnection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun readHeaders(input: BufferedInputStream): ByteArray? {
        val bytes = ArrayList<Byte>()
        while (bytes.size < MAX_HEADER_BYTES) {
            val next = input.read()
            if (next < 0) return null
            bytes += next.toByte()
            val size = bytes.size
            if (size >= 4 && bytes[size - 4] == 13.toByte() && bytes[size - 3] == 10.toByte() && bytes[size - 2] == 13.toByte() && bytes[size - 1] == 10.toByte()) return bytes.toByteArray()
        }
        return null
    }

    private fun localIpv4Address(): Inet4Address? = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }

    private companion object {
        const val SESSION_MS = 5 * 60 * 1_000L
        const val LOCAL_PORT = 8_765
        const val MAX_HEADER_BYTES = 16 * 1_024
        const val MAX_BODY_BYTES = 32 * 1_024
        const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}
