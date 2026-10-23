package com.example.musictransi.source

/** Only music-catalog candidates; identify the song independently of its upload/channel. */
object RadioPolicy {
    private val alteredUpload = Regex("(?i)\\b(bass\\s*boost(?:ed)?|speed\\s*up|sped\\s*up|slow(?:ed)?\\s*(?:and|[+&])\\s*reverb|tiktok\\s*(?:version|edit)|8d\\s*audio|16d\\s*audio|fan\\s*edit|concert|live\\s+at)\\b")
    private val unwanted = Regex("(?i)\\b(trailer|teaser|interview|reaction|podcast|review|karaoke|cover|remix|instrumental|acapella|a cappella|bootleg|tribute|nightcore|slowed|reverb|sped[ -]?up|speed[ -]?up|bass[ -]?boost(?:ed)?|8d|16d|tutorial|behind the scenes|live performance)\\b")
    private val decoration = Regex("(?i)\\b(official|music video|audio|video|lyrics?|lyrical|visuali[sz]er|hd|hq|4k|remaster(?:ed)?(?: \\d{4})?)\\b")
    private fun plain(value: String) = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")
    fun artistMatches(a: String, b: String): Boolean = plain(a.replace(Regex("(?i)\\s*-\\s*topic$|vevo$"), "")) ==
        plain(b.replace(Regex("(?i)\\s*-\\s*topic$|vevo$"), ""))
    fun songKey(track: Track): String {
        var title = track.title
        // Video-style titles usually prefix the artist; radio catalog titles do not.
        val parts = title.split(Regex("\\s+[-–—|]\\s+"), limit = 2)
        if (parts.size == 2) {
            val suffix = plain(parts[1].replace(decoration, "").replace(unwanted, ""))
            title = if (suffix.isEmpty()) parts[0]
                else if (artistMatches(parts[0], track.artist) || parts[1].contains(decoration) || parts[1].contains(unwanted)) parts[1]
                else title
        }
        title = title.replace(Regex("(?i)[(\\[][^)\\]]*(?:official|audio|video|lyric|remaster|bass|slowed|reverb|sped|hd|4k)[^)\\]]*[)\\]]"), "")
        title = title.replace(Regex("(?i)\\b(?:feat\\.?|ft\\.?)\\s+.*$"), "")
        return plain(title.replace(unwanted, "").replace(decoration, ""))
    }
    fun eligible(track: Track) = track.music && !track.local && track.durationSeconds in 60..900 &&
        track.artist.isNotBlank() && !unwanted.containsMatchIn(track.title) &&
        !alteredUpload.containsMatchIn(track.title + " " + track.artist) &&
        !Regex("(?i)(karaoke|tribute to|bass boosted|nightcore|slowed.*reverb)").containsMatchIn(track.artist) && songKey(track).length >= 2
    fun sameRecording(a: Track, b: Track): Boolean {
        if (a.id == b.id) return true
        if (songKey(a) != songKey(b)) return false
        if (artistMatches(a.artist, b.artist)) return true
        // Different genuine artists can have the same song title. Decorated uploads are
        // suspect only when their lengths also agree (or one length is unavailable).
        val upload = decoration.containsMatchIn(a.title) || decoration.containsMatchIn(b.title) ||
            unwanted.containsMatchIn(a.title) || unwanted.containsMatchIn(b.title)
        val close = a.durationSeconds <= 0 || b.durationSeconds <= 0 ||
            kotlin.math.abs(a.durationSeconds - b.durationSeconds) <= maxOf(8L, (a.durationSeconds * .06).toLong())
        return upload && close
    }
    fun select(candidates: List<Track>, excluded: List<Track>, limit: Int = 3): List<Track> {
        if (limit <= 0) return emptyList()
        val chosen = mutableListOf<Track>()
        val pool = candidates.filter { eligible(it) && excluded.none { old -> sameRecording(it, old) } }.toMutableList()
        while (chosen.size < limit && pool.isNotEmpty()) {
            val recent = (excluded + chosen).takeLast(2)
            val avoid = recent.takeIf { it.size == 2 && artistMatches(it[0].artist, it[1].artist) }?.last()?.artist
            val different = if (avoid != null) pool.indexOfFirst { !artistMatches(it.artist, avoid) } else -1
            val track = pool.removeAt(if (different >= 0) different else 0)
            if (chosen.none { sameRecording(it, track) }) chosen += track.copy(automatic = true)
        }
        return chosen
    }

    /** Preserve radio relevance while gently promoting artists supported by actual listening/likes. */
    fun personalized(candidates: List<Track>, history: List<Track>, likes: List<Track>): List<Track> =
        candidates.withIndex().sortedByDescending { (index, track) ->
            val familiar = history.take(30).count { artistMatches(it.artist, track.artist) }.coerceAtMost(4)
            val liked = if (likes.any { artistMatches(it.artist, track.artist) }) 3 else 0
            familiar + liked - index / 4
        }.map { it.value }
}
