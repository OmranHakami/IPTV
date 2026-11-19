package com.oh.tv.data

import com.oh.tv.data.local.SeriesEpisodeEntity
import com.oh.tv.data.remote.asInt
import com.oh.tv.data.remote.asMap
import com.oh.tv.data.remote.asString

internal fun parseSeriesEpisodes(payload: Any?, accountId: String, seriesId: String): List<SeriesEpisodeEntity> {
    require(payload is Map<*, *>) { "Missing or malformed episodes" }
    val generation = System.currentTimeMillis()
    val episodes = payload.flatMap { (seasonKey, season) ->
        require(season is List<*>) { "Malformed episode season" }
        season.map { value ->
            val raw = value.asMap()
            val providerId = raw["id"]
            require(providerId is Number || providerId is String) { "Missing or malformed episode ID" }
            val id = requireNotNull(providerId.asString()) { "Empty episode ID" }
            val info = raw["info"].asMap()
            val number = raw["episode_num"].asInt() ?: 0
            SeriesEpisodeEntity(
                accountId = accountId,
                seriesId = seriesId,
                providerId = id,
                seasonNumber = raw["season"].asInt() ?: seasonKey.toString().toIntOrNull() ?: 0,
                episodeNumber = number,
                title = raw["title"].asString() ?: "Episode $number",
                containerExtension = raw["container_extension"].asString(),
                overview = info["plot"].asString(),
                duration = info["duration"].asString(),
                posterUrl = info["movie_image"].asString(),
                syncGeneration = generation,
            )
        }
    }
    require(payload.isEmpty() || episodes.isNotEmpty()) { "No episodes in season data" }
    return episodes
}
