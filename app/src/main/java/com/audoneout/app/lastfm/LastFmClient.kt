package com.audoneout.app.lastfm

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LastFmProfile(
    val username: String,
    val displayName: String,
    val profileUrl: String,
    val imageUrl: String,
    val playCount: Long
)

data class LastFmTrack(
    val name: String,
    val artist: String,
    val url: String,
    val imageUrl: String,
    val mbid: String,
    val playCount: Long = 0,
    val match: Double = 0.0
)

data class LastFmTrackInfo(
    val name: String,
    val artist: String,
    val album: String,
    val url: String,
    val imageUrl: String,
    val mbid: String,
    val tags: List<String>
)

@Singleton
class LastFmClient @Inject constructor() {
    suspend fun getProfile(username: String, apiKey: String): LastFmProfile {
        val user = request("user.getInfo", mapOf("user" to username, "api_key" to apiKey)).getJSONObject("user")
        return LastFmProfile(
            username = user.cleanString("name").ifBlank { username },
            displayName = user.cleanString("realname").ifBlank { user.cleanString("name") },
            profileUrl = user.cleanString("url"),
            imageUrl = user.optJSONArray("image").largestImage(),
            playCount = user.cleanString("playcount").toLongOrNull() ?: 0
        )
    }

    suspend fun getTopTracks(
        username: String,
        apiKey: String,
        period: String = "6month",
        limit: Int = 30
    ): List<LastFmTrack> {
        val root = request(
            "user.getTopTracks",
            mapOf(
                "user" to username,
                "api_key" to apiKey,
                "period" to period,
                "limit" to limit.coerceIn(1, 100).toString()
            )
        )
        return root.getJSONObject("toptracks").optJSONArray("track").toTracks()
    }

    suspend fun getLovedTracks(username: String, apiKey: String, limit: Int = 30): List<LastFmTrack> {
        val root = request(
            "user.getLovedTracks",
            mapOf(
                "user" to username,
                "api_key" to apiKey,
                "limit" to limit.coerceIn(1, 100).toString()
            )
        )
        return root.getJSONObject("lovedtracks").optJSONArray("track").toTracks()
    }

    suspend fun getRecentTracks(username: String, apiKey: String, limit: Int = 40): List<LastFmTrack> {
        val root = request(
            "user.getRecentTracks",
            mapOf(
                "user" to username,
                "api_key" to apiKey,
                "extended" to "0",
                "limit" to limit.coerceIn(1, 200).toString()
            )
        )
        return root.getJSONObject("recenttracks").optJSONArray("track").toTracks()
    }

    suspend fun getSimilarTracks(artist: String, track: String, apiKey: String, limit: Int = 20): List<LastFmTrack> {
        val root = request(
            "track.getSimilar",
            mapOf(
                "artist" to artist,
                "track" to track,
                "api_key" to apiKey,
                "autocorrect" to "1",
                "limit" to limit.coerceIn(1, 100).toString()
            )
        )
        return root.getJSONObject("similartracks").optJSONArray("track").toTracks()
    }

    suspend fun getTrackInfo(artist: String, track: String, username: String, apiKey: String): LastFmTrackInfo {
        val root = request(
            "track.getInfo",
            mapOf(
                "artist" to artist,
                "track" to track,
                "username" to username,
                "api_key" to apiKey,
                "autocorrect" to "1"
            )
        ).getJSONObject("track")
        val album = root.optJSONObject("album")
        val tags = root.optJSONObject("toptags")
            ?.optJSONArray("tag")
            .objects()
            .map { it.cleanString("name") }
            .filter { it.isNotBlank() }
        return LastFmTrackInfo(
            name = root.cleanString("name"),
            artist = root.optJSONObject("artist")?.cleanString("name").orEmpty(),
            album = album?.cleanString("title").orEmpty(),
            url = root.cleanString("url"),
            imageUrl = album?.optJSONArray("image").largestImage(),
            mbid = root.cleanString("mbid"),
            tags = tags
        )
    }

    private suspend fun request(method: String, parameters: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val query = (parameters + mapOf("method" to method, "format" to "json"))
            .entries
            .sortedBy { it.key }
            .joinToString("&") { (key, value) -> "${key.urlEncode()}=${value.urlEncode()}" }
        val connection = (URI.create("$API_ENDPOINT?$query").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AudOneOut/0.6 (Android companion app)")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (body.isBlank()) throw IOException("Last.fm returned an empty response")
            val json = JSONObject(body)
            if (json.has("error")) throw IOException(json.cleanString("message").ifBlank { "Last.fm request failed" })
            if (status !in 200..299) throw IOException("Last.fm returned HTTP $status")
            json
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val API_ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
    }
}

private fun JSONArray?.toTracks(): List<LastFmTrack> = objects().map { track ->
    val artistValue = track.opt("artist")
    val artist = when (artistValue) {
        is JSONObject -> artistValue.cleanString("name").ifBlank { artistValue.cleanString("#text") }
        is String -> artistValue
        else -> ""
    }
    LastFmTrack(
        name = track.cleanString("name"),
        artist = artist,
        url = track.cleanString("url"),
        imageUrl = track.optJSONArray("image").largestImage(),
        mbid = track.cleanString("mbid"),
        playCount = track.cleanString("playcount").toLongOrNull() ?: 0,
        match = track.cleanString("match").toDoubleOrNull() ?: 0.0
    )
}.filter { it.name.isNotBlank() && it.artist.isNotBlank() }

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }
}

private fun JSONArray?.largestImage(): String = objects()
    .map { it.cleanString("#text") }
    .lastOrNull { it.isNotBlank() }
    .orEmpty()

private fun JSONObject.cleanString(key: String): String = optString(key, "").takeUnless { it == "null" }.orEmpty()

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
