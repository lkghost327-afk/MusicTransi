package com.example.musictransi.source

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long = 0,
    val local: Boolean = false,
    val artwork: String = "",
    val automatic: Boolean = false,
    val music: Boolean = false,
)

data class ResolvedAudio(val url: String, val headers: Map<String, String> = emptyMap(), val artwork: String = "")

data class Album(val id: String, val title: String, val artist: String, val artwork: String = "", val trackCount: Long = 0)
data class AlbumPage(val tracks: List<Track>, val next: org.schabi.newpipe.extractor.Page? = null)

/** Source boundary: no provider-specific code belongs in the mixing engine. */
interface MusicSource {
    suspend fun search(query: String): List<Track>
    suspend fun resolve(track: Track): ResolvedAudio
}
