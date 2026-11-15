package com.oh.tv.data.remote

import com.squareup.moshi.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class XtreamGateway(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        // Xtream catalog endpoints can return tens of thousands of records. A whole-call
        // timeout also counts the time spent streaming and committing Room batches, so it
        // can terminate a healthy large import. Keep an idle-read timeout, but do not cap
        // the complete streamed response.
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    suspend fun authenticate(baseUrl: String, username: String, password: String): Result<AuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            val response = execute(baseUrl, username, password, null)
            response.use {
                if (!it.isSuccessful) throw ProviderException(ProviderFailure.Http(it.code))
                val root = JsonReader.of(requireNotNull(it.body).source()).readJsonValue().asMap()
                val user = root["user_info"].asMap()
                val auth = user["auth"].asBoolean() ?: false
                val status = user["status"].asString().orEmpty()
                if (!auth) throw ProviderException(ProviderFailure.InvalidCredentials)
                if (!status.equals("Active", ignoreCase = true)) {
                    val failure = if (status.equals("Expired", true)) ProviderFailure.Expired else ProviderFailure.Malformed("Account is $status")
                    throw ProviderException(failure)
                }
                AuthResult(
                    active = true,
                    status = status,
                    expiresAtEpochSeconds = user["exp_date"].asLong(),
                    allowedOutputFormats = user["allowed_output_formats"].asList().mapNotNull { item -> item.asString() },
                )
            }
        }.recoverCatching { error ->
            if (error is ProviderException) throw error
            throw ProviderException(ProviderFailure.Network(error.message.safeNetworkMessage()))
        }
    }

    suspend fun openArray(baseUrl: String, username: String, password: String, action: String): ResponseBody = withContext(Dispatchers.IO) {
        val response = execute(baseUrl, username, password, action)
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw ProviderException(ProviderFailure.Http(code))
        }
        requireNotNull(response.body)
    }

    suspend fun seriesInfo(baseUrl: String, username: String, password: String, seriesId: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        val response = execute(baseUrl, username, password, "get_series_info", mapOf("series_id" to seriesId))
        response.use {
            if (!it.isSuccessful) throw ProviderException(ProviderFailure.Http(it.code))
            JsonReader.of(requireNotNull(it.body).source()).readJsonValue().asMap()
        }
    }

    private fun execute(
        base: String,
        username: String,
        password: String,
        action: String?,
        parameters: Map<String, String> = emptyMap(),
    ) =
        client.newCall(
            okhttp3.Request.Builder()
                .url(base.toHttpUrl().newBuilder().addPathSegment("player_api.php")
                    .addQueryParameter("username", username)
                    .addQueryParameter("password", password)
                    .apply { if (action != null) addQueryParameter("action", action) }
                    .apply { parameters.forEach { (key, value) -> addQueryParameter(key, value) } }
                    .build())
                .header("Accept", "application/json")
                .header("User-Agent", "OH-TV/0.3.1")
                .build(),
        ).execute()
}

class ProviderException(val failure: ProviderFailure) : IOException(failure.toString())

@Suppress("UNCHECKED_CAST")
internal fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()
internal fun Any?.asList(): List<Any?> = this as? List<Any?> ?: emptyList()
internal fun Any?.asString(): String? = when (this) {
    is String -> takeIf { it.isNotBlank() }
    is Number, is Boolean -> toString()
    else -> null
}
internal fun Any?.asLong(): Long? = when (this) {
    is Number -> toLong()
    is String -> toLongOrNull()
    else -> null
}
internal fun Any?.asInt(): Int? = asLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
internal fun Any?.asDouble(): Double? = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull()
    else -> null
}
internal fun Any?.asBoolean(): Boolean? = when (this) {
    is Boolean -> this
    is Number -> toInt() != 0
    is String -> when (lowercase()) { "1", "true", "yes" -> true; "0", "false", "no" -> false; else -> null }
    else -> null
}
private fun String?.safeNetworkMessage() = when {
    this == null -> "Network request failed"
    contains("timeout", true) -> "The server timed out"
    contains("resolve", true) -> "The server address could not be resolved"
    else -> "The server could not be reached"
}
