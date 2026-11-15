package com.oh.tv.data.remote

data class AccountInput(
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val password: String,
)

data class AuthResult(
    val active: Boolean,
    val status: String,
    val expiresAtEpochSeconds: Long?,
    val allowedOutputFormats: List<String>,
)

data class TmdbMetadata(
    val id: Long,
    val title: String,
    val overview: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val rating: Double?,
    val voteCount: Int?,
    val genres: String?,
    val posterPath: String?,
    val backdropPath: String?,
)

sealed interface ProviderFailure {
    data object InvalidCredentials : ProviderFailure
    data object Expired : ProviderFailure
    data class Http(val code: Int) : ProviderFailure
    data class Malformed(val safeReason: String) : ProviderFailure
    data class Network(val safeReason: String) : ProviderFailure
}

