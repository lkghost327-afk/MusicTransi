package com.example.musictransi.playback

import com.example.musictransi.library.LibraryState
import com.example.musictransi.source.Track
import java.util.Base64

/** Stable IDs carry identity only; playback metadata is always read from the current library. */
internal object CarCatalogue {
    fun collection(parent: String, saved: LibraryState, recent: List<Track>, queue: List<Track>): List<Track> = when (parent) {
        "likes" -> saved.likes
        "recent" -> recent
        "queue" -> queue
        else -> saved.playlists.firstOrNull { parent == "playlist:${it.id}" }?.tracks.orEmpty()
    }

    fun mediaId(parent: String, track: Track): String = "$parent/item/" +
        Base64.getUrlEncoder().encodeToString(track.id.toByteArray(Charsets.UTF_8))

    fun decode(mediaId: String?): Pair<String, String>? {
        if (mediaId == null || mediaId.length > 16384) return null
        val parts = mediaId.split("/item/", limit = 2)
        if (parts.size != 2) return null
        val parent = parts[0]
        if (parent !in setOf("likes", "recent", "queue") && !parent.matches(Regex("playlist:[1-9][0-9]*"))) return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(parts[1])
            val id = bytes.toString(Charsets.UTF_8)
            require(id.isNotBlank() && id.toByteArray(Charsets.UTF_8).contentEquals(bytes))
            parent to id
        }.getOrNull()
    }
}
