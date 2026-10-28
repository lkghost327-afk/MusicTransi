package com.example.musictransi.lyrics

import com.example.musictransi.source.Track
import com.grack.nanojson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class LyricWord(val timeMs: Long, val start: Int, val end: Int)
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())
data class Lyrics(val lines: List<LyricLine> = emptyList(), val plain: String = "", val instrumental: Boolean = false)
object LrcParser {
    private val wordStamp = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>")
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    fun parse(text: String): List<LyricLine> {
        val offset = Regex("(?i)\\[offset:([+-]?\\d+)]").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return text.lineSequence().take(2000).flatMap { line ->
            val raw = line.replace(stamp, "").trim()
            val marks = wordStamp.findAll(raw).toList()
            val clean = StringBuilder()
            val timed = mutableListOf<LyricWord>()
            if (marks.isNotEmpty()) {
                clean.append(raw.substring(0, marks.first().range.first))
                marks.forEachIndexed { index, mark ->
                    val segment = raw.substring(mark.range.last + 1, marks.getOrNull(index + 1)?.range?.first ?: raw.length)
                    val start = clean.length
                    clean.append(segment)
                    val seconds = mark.groupValues[2].toLong()
                    if (segment.isNotBlank() && seconds < 60) timed += LyricWord(
                        (mark.groupValues[1].toLong() * 60000 + seconds * 1000 +
                            (mark.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0) + offset).coerceAtLeast(0), start, clean.length)
                }
            } else clean.append(raw)
            val words = clean.toString()
            stamp.findAll(line).mapNotNull { match ->
                val seconds = match.groupValues[2].toLong()
                if (seconds >= 60) null else {
                    val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
                    LyricLine((match.groupValues[1].toLong() * 60000 + seconds * 1000 + fraction + offset).coerceAtLeast(0), words,
                        timed.takeIf { list -> list.zipWithNext().all { it.first.timeMs <= it.second.timeMs } }.orEmpty())
                }
            }
        }.sortedBy { it.timeMs }.distinct().take(2000).toList()
    }
}

object WordFollow {
    fun highlightedEnd(line: LyricLine, position: Long, endMs: Long, estimate: Boolean): Int {
        if (position < line.timeMs) return 0
        if (line.words.isNotEmpty()) return line.words.lastOrNull { it.timeMs <= position }?.end ?: 0
        if (!estimate || endMs <= line.timeMs) return 0
        val words = Regex("\\S+").findAll(line.text).toList()
        if (words.isEmpty()) return 0
        val fraction = ((position - line.timeMs).toDouble() / (endMs - line.timeMs)).coerceIn(0.0, .999999)
        return words[(fraction * words.size).toInt()].range.last + 1
    }
}
object LyricsClient {
    private val http = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val cache = object : LinkedHashMap<String, Lyrics>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?) = size > 16
    }
    private fun clean(value: String) = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
    suspend fun fetch(track: Track, duration: Long): Lyrics = withContext(Dispatchers.IO) {
        val key = "${track.id}:$duration"
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        if (track.local) return@withContext Lyrics()
        val artist = track.artist.replace(Regex("(?i)\\s*-\\s*Topic$"), "").trim()
        val title = track.title.replace(Regex("(?i)\\s*[\\[(](?:official\\s+(?:audio|video)|lyrics?|visuali[sz]er)[\\])]"), "").trim()
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist)
            .apply { if (duration in 1..3600) addQueryParameter("duration", duration.toString()) }.build()
        val result = http.newCall(Request.Builder().url(url).header("User-Agent", "MusicTransi/0.6 (personal music player)").build()).execute().use { response ->
            if (response.code == 404) return@use Lyrics()
            check(response.isSuccessful) { "Lyrics provider unavailable" }
            val source = response.body?.source() ?: error("Empty lyrics response")
            check(!source.request(524289)) { "Lyrics response too large" }
            val json = JsonParser.`object`().from(source.readUtf8())
            if (clean(json.getString("trackName", "")) != clean(title) || clean(json.getString("artistName", "")) != clean(artist)) return@use Lyrics()
            Lyrics(LrcParser.parse(json.getString("syncedLyrics", "")), json.getString("plainLyrics", ""), json.getBoolean("instrumental", false))
        }
        synchronized(cache) { cache[key] = result }; result
    }
}
