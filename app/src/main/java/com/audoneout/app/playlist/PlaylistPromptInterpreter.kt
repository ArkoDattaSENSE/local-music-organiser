package com.audoneout.app.playlist

import com.audoneout.app.domain.PlaylistRules
import javax.inject.Inject

data class LibrarySummary(
    val genres: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val folders: List<String> = emptyList(),
    val formats: List<String> = emptyList(),
    val trackCount: Int = 0
)

interface PlaylistPromptInterpreter {
    suspend fun interpret(prompt: String, librarySummary: LibrarySummary): Result<PlaylistRules>
}

class LocalRuleBasedPromptInterpreter @Inject constructor() : PlaylistPromptInterpreter {
    override suspend fun interpret(prompt: String, librarySummary: LibrarySummary): Result<PlaylistRules> =
        Result.success(parse(prompt, librarySummary))

    fun parse(prompt: String, librarySummary: LibrarySummary = LibrarySummary()): PlaylistRules {
        val lower = prompt.lowercase()
        val duration = Regex("(\\d+)\\s*[- ]?\\s*(minute|min)").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val formats = librarySummary.formats.filter { lower.contains(it.lowercase()) } +
            listOf("flac", "wav", "alac", "mp3", "aac", "ogg").filter { lower.contains(it) }
        val genres = librarySummary.genres.filter { lower.contains(it.lowercase()) } +
            listOf("rock", "pop", "jazz", "metal", "classical", "bengali", "progressive").filter { lower.contains(it) }
        val languages = librarySummary.languages.filter { lower.contains(it.lowercase()) } +
            listOf("bengali", "hindi", "english").filter { lower.contains(it) }
        val folders = librarySummary.folders.filter { lower.contains(it.lowercase()) }
        val avoidRepeatArtists = lower.contains("without repeating artists") ||
            lower.contains("do not repeat artists") ||
            lower.contains("avoid artist")
        val preferUnplayed = lower.contains("ignored recently") ||
            lower.contains("avoid recently played") ||
            lower.contains("discovery")
        val energy = when {
            lower.contains("walking") || lower.contains("energetic") -> 55..85
            lower.contains("rainy") || lower.contains("relaxed") -> 15..45
            else -> null
        }
        val familiarity = when {
            lower.contains("nostalgia") -> "familiar"
            lower.contains("newer discoveries") || lower.contains("ignored") -> "discovery"
            else -> null
        }
        return PlaylistRules(
            prompt = prompt,
            targetDurationMinutes = duration,
            includedGenres = genres.distinct(),
            languages = languages.distinct(),
            folders = folders.distinct(),
            fileFormats = formats.distinct().map { it.uppercase() },
            preferUnplayed = preferUnplayed,
            avoidArtistRepetitions = avoidRepeatArtists,
            energyRange = energy,
            familiarityLevel = familiarity
        )
    }
}
