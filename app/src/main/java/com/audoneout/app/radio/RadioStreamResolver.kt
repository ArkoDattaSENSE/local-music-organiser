package com.audoneout.app.radio

import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RadioStreamResolver @Inject constructor() {
    suspend fun resolve(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!looksLikePlaylist(url)) return@runCatching url
            if (url.startsWith("http://", ignoreCase = true)) return@runCatching url
            val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "AudOneOut/0.6")
                connection.inputStream.bufferedReader().use { reader ->
                    parsePlaylist(reader.readText()).firstOrNull()
                        ?: error("No playable stream was found in this radio playlist")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun parsePlaylist(text: String): List<String> = text.lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
            when {
                line.startsWith("File", true) && '=' in line -> line.substringAfter('=').trim()
                line.startsWith("http://", true) || line.startsWith("https://", true) -> line
                else -> null
            }
        }
        .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
        .distinct()
        .toList()

    private fun looksLikePlaylist(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u") || path.endsWith(".m3u8") || path.endsWith(".pls")
    }
}
