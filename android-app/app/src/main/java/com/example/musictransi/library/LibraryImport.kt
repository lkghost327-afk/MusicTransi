package com.example.musictransi.library

import com.example.musictransi.source.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream

data class ImportRow(val title: String, val artist: String, val collection: String = "Imported playlist", val kind: String = "playlist")
data class ImportMatch(val row: ImportRow, val track: Track? = null, val album: Album? = null)
fun readBounded(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= limit) { "File is too large." }; output.write(buffer, 0, n) }
    return output.toByteArray()
}
object LibraryImport {
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    fun csv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>(); val row = mutableListOf<String>(); val cell = StringBuilder()
        var quoted = false; var i = 0
        while (i < text.length) { val c = text[i++]
            when {
                c == '"' && quoted && i < text.length && text[i] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row += cell.toString(); cell.clear() }
                (c == '\n' || c == '\r') && !quoted -> { if (c == '\r' && i < text.length && text[i] == '\n') i++; row += cell.toString(); cell.clear(); if (row.any { it.isNotBlank() }) rows += row.toList(); row.clear() }
                else -> cell.append(c)
            }
        }
        require(!quoted) { "CSV contains an unclosed quote." }
        row += cell.toString(); if (row.any { it.isNotBlank() }) rows += row
        return rows
    }
    fun parse(text: String): List<ImportRow> {
        val clean = text.removePrefix("\uFEFF").trim()
        val result = mutableListOf<ImportRow>()
        fun add(title: String, artist: String, collection: String, kind: String) {
            require(title.length <= 500 && artist.length <= 500 && collection.length <= 80) { "A name in this file is too long." }
            if (title.isNotBlank() && artist.isNotBlank()) result += ImportRow(title.trim(), artist.trim(), collection.ifBlank { "Imported playlist" }, kind)
        }
        if (clean.startsWith("{") || clean.startsWith("[")) {
            fun items(a: JSONArray, collection: String, kind: String) {
                for (i in 0 until a.length()) {
                    val original = a.optJSONObject(i) ?: continue
                    val j = original.optJSONObject("track") ?: original
                    val artist = j.optString("artist", j.optString("artistName", "")).ifBlank {
                        val artists = j.optJSONArray("artists"); artists?.optJSONObject(0)?.optString("name") ?: artists?.optString(0).orEmpty()
                    }
                    add(j.optString("title", j.optString("trackName", j.optString("name", j.optString("track", j.optString("album", ""))))), artist, collection, kind)
                }
            }
            if (clean.startsWith("[")) items(JSONArray(clean), "Imported playlist", "playlist") else {
                val root = JSONObject(clean)
                val playlists = root.optJSONArray("playlists") ?: JSONArray()
                for (i in 0 until playlists.length()) { val p = playlists.getJSONObject(i); items(p.optJSONArray("tracks") ?: p.optJSONArray("items") ?: JSONArray(), p.optString("name", "Imported playlist"), "playlist") }
                items(root.optJSONArray("likedSongs") ?: root.optJSONArray("likes") ?: JSONArray(), "Liked songs", "likes")
                items(root.optJSONArray("albums") ?: JSONArray(), "Saved albums", "album")
                items(root.optJSONArray("tracks") ?: root.optJSONArray("items") ?: JSONArray(), root.optString("name", "Imported playlist"), if (root.has("albums") && !root.has("name")) "likes" else "playlist")
            }
        } else {
            val rows = csv(clean); require(rows.isNotEmpty()) { "The file is empty." }
            val headers = rows.first().map(::norm)
            fun List<String>.field(vararg names: String): String { val index = headers.indexOfFirst { it in names }; return getOrNull(index)?.trim().orEmpty() }
            for (row in rows.drop(1)) {
                val kind = norm(row.field("type", "kind")).let { if (it in listOf("album", "albums")) "album" else if (it in listOf("like", "likes", "likedsongs")) "likes" else "playlist" }
                add(row.field("title", "trackname", "song", "name", "albumname"), row.field("artist", "artists", "artistnames", "artistnames", "artistname"), row.field("playlist", "playlistname", "collection"), kind)
            }
        }
        require(result.isNotEmpty()) { "No title/artist rows found. Use the supported CSV or JSON format." }
        require(result.size <= 500) { "Import up to 500 entries per file." }
        return result.distinct()
    }
    suspend fun match(row: ImportRow): ImportMatch {
        val artist = row.artist.split(';').first().trim()
        if (row.kind == "album") {
            val album = YouTubeSource.searchAlbums("${row.title} $artist").firstOrNull { norm(it.title) == norm(row.title) && RadioPolicy.artistMatches(it.artist, artist) }
            return ImportMatch(row, album = album)
        }
        val target = Track("import", row.title, artist)
        val song = YouTubeSource.search("${row.title} $artist").firstOrNull {
            RadioPolicy.songKey(it) == RadioPolicy.songKey(target) && RadioPolicy.artistMatches(it.artist, artist)
        }
        return ImportMatch(row, track = song)
    }
}
