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
        val knownFormats = (librarySummary.formats + listOf("flac", "wav", "alac", "mp3", "aac", "ogg", "opus"))
            .distinctBy { it.lowercase() }
        val formats = buildList {
            addAll(knownFormats.filter { lower.contains(it.lowercase()) })
            if ("lossless" in lower) addAll(LOSSLESS_FORMATS)
        }
        val knownGenres = (librarySummary.genres +
            listOf("rock", "pop", "jazz", "metal", "classical", "progressive", "folk", "electronic", "ambient", "hip hop"))
            .distinctBy { it.lowercase() }
        val excludedGenres = knownGenres.filter { genre ->
            val value = genre.lowercase()
            lower.contains("avoid $value") ||
                lower.contains("no $value") ||
                lower.contains("without $value")
        }
        val genres = knownGenres.filter { genre ->
            lower.contains(genre.lowercase()) &&
                excludedGenres.none { it.equals(genre, ignoreCase = true) }
        }
        val knownLanguages = (librarySummary.languages + listOf("bengali", "bangla", "hindi", "english"))
            .distinctBy { it.lowercase() }
        val languages = knownLanguages.filter { lower.contains(it.lowercase()) }
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
        val addedWithinDays = when {
            lower.contains("added this week") || lower.contains("new this week") -> 7
            lower.contains("added this month") || lower.contains("new this month") -> 31
            lower.contains("newer discoveries") || lower.contains("recently added") -> 90
            else -> null
        }
        return PlaylistRules(
            prompt = prompt,
            targetDurationMinutes = duration,
            includedGenres = genres.distinct(),
            excludedGenres = excludedGenres.distinct(),
            languages = languages.distinct(),
            folders = folders.distinct(),
            fileFormats = formats.map { it.uppercase() }.distinct(),
            preferUnplayed = preferUnplayed,
            avoidArtistRepetitions = avoidRepeatArtists,
            energyRange = energy,
            familiarityLevel = familiarity,
            addedWithinDays = addedWithinDays
        )
    }

    private companion object {
        val LOSSLESS_FORMATS = listOf("FLAC", "ALAC", "WAV", "WAVE", "AIFF", "AIF", "APE", "WV")
    }
}
