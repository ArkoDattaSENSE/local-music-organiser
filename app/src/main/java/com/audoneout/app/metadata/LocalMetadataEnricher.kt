package com.audoneout.app.metadata

import com.audoneout.app.data.TrackEntity

data class LocalMetadataInference(
    val key: String,
    val value: String,
    val confidence: Float
)

object LocalMetadataEnricher {
    const val SOURCE = "AudOneOut local rules v1"

    fun infer(track: TrackEntity): List<LocalMetadataInference> {
        val values = mutableListOf<LocalMetadataInference>()
        val tags = linkedSetOf<String>()
        val genre = track.genre.lowercase()

        track.genre.takeIf { it.isNotBlank() }?.let(tags::add)
        track.language.takeIf { it.isNotBlank() }?.let(tags::add)
        track.year?.takeIf { it in 1000..9999 }?.let { year ->
            val era = "${year / 10 * 10}s"
            tags += era
            values += LocalMetadataInference("era", era, 1f)
        }

        val format = track.format.ifBlank { track.fileName.substringAfterLast('.', "") }.uppercase()
        if (format in LOSSLESS_FORMATS) {
            tags += "lossless"
            values += LocalMetadataInference("quality", "lossless", 0.99f)
        }

        val profile = ENERGY_PROFILES.firstOrNull { (keywords, _, _) ->
            keywords.any { it in genre }
        }
        if (profile != null) {
            val mood = profile.second
            val energy = profile.third
            tags += mood
            values += LocalMetadataInference("mood", mood, 0.62f)
            values += LocalMetadataInference("energy", energy.toString(), 0.58f)
        }

        val character = when {
            ELECTRONIC_KEYWORDS.any { it in genre } -> "electronic"
            ACOUSTIC_KEYWORDS.any { it in genre } -> "acoustic"
            else -> null
        }
        character?.let {
            tags += it
            values += LocalMetadataInference("character", it, 0.64f)
        }

        if (tags.isNotEmpty()) {
            values += LocalMetadataInference("discoveryTags", tags.joinToString(", "), 0.7f)
        }
        return values
    }

    private val LOSSLESS_FORMATS = setOf("FLAC", "ALAC", "WAV", "WAVE", "AIFF", "AIF", "APE", "WV")
    private val ELECTRONIC_KEYWORDS = setOf(
        "electronic", "electronica", "edm", "house", "techno", "trance", "synth", "ambient", "drum and bass"
    )
    private val ACOUSTIC_KEYWORDS = setOf(
        "acoustic", "folk", "classical", "singer-songwriter", "unplugged", "bluegrass"
    )
    private val ENERGY_PROFILES = listOf(
        Triple(setOf("metal", "punk", "hardcore", "drum and bass", "techno", "edm"), "intense", 84),
        Triple(setOf("rock", "pop", "hip hop", "rap", "funk", "dance"), "upbeat", 68),
        Triple(setOf("ambient", "classical", "folk", "acoustic", "chill", "downtempo"), "relaxed", 32)
    )
}
