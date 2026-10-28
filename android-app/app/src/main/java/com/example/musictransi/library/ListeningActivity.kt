package com.example.musictransi.library

import android.content.Context
import com.example.musictransi.source.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

/** Monotonic audible time; seeks, pauses, buffering and process downtime never add minutes. */
class AudibleClock {
    private var previous = 0L
    private var id: String? = null
    fun sample(trackId: String?, playing: Boolean, now: Long): Long {
        val delta = if (playing && trackId != null && id == trackId) (now - previous).coerceIn(0, 1000) else 0
        previous = now
        id = trackId.takeIf { playing }
        return delta
    }
}

data class ListeningTotal(val month: String, val id: String, val title: String, val artist: String, val milliseconds: Long,
    val artwork: String = "")

/** Aggregated locally, batched every 15 seconds; no listening data leaves the device. */
class ListeningActivity(context: Context) {
    private val prefs = context.getSharedPreferences("listening-activity", Context.MODE_PRIVATE)
    private val clock = AudibleClock()
    private val totals = linkedMapOf<String, ListeningTotal>()
    private val mutable = MutableStateFlow<List<ListeningTotal>>(emptyList())
    val state = mutable.asStateFlow()
    private var dirty = false
    private var lastFlush = 0L
    init {
        runCatching {
            val array = JSONArray(prefs.getString("totals", "[]"))
            repeat(minOf(array.length(), 10000)) { i ->
                val row = array.getJSONObject(i)
                val total = ListeningTotal(row.getString("month"), row.getString("id"), row.getString("title"),
                    row.getString("artist"), row.getLong("ms").coerceAtLeast(0), row.optString("artwork", ""))
                totals["${total.month}:${total.id}"] = total
            }
        }
        mutable.value = totals.values.toList()
    }
    fun observe(track: Track?, playing: Boolean, elapsed: Long) {
        val delta = clock.sample(track?.id, playing, elapsed)
        if (track != null && delta > 0) {
            val month = YearMonth.now().toString()
            val key = "$month:${track.id}"
            val old = totals[key]
            totals[key] = ListeningTotal(month, track.id, track.title, track.artist, (old?.milliseconds ?: 0) + delta,
                track.artwork.ifBlank { old?.artwork.orEmpty() })
            dirty = true
        }
        if (elapsed - lastFlush >= 15000 || !playing) { flush(); lastFlush = elapsed }
    }
    fun flush() {
        if (!dirty) return
        val cutoff = YearMonth.now().minusMonths(23).toString()
        totals.entries.removeAll { it.value.month < cutoff }
        while (totals.size > 10000) totals.remove(totals.keys.first())
        val array = JSONArray()
        totals.values.forEach { row -> array.put(JSONObject().put("month", row.month).put("id", row.id)
            .put("title", row.title).put("artist", row.artist).put("ms", row.milliseconds).put("artwork", row.artwork)) }
        prefs.edit().putString("totals", array.toString()).apply()
        mutable.value = totals.values.toList()
        dirty = false
    }
    fun clear() { totals.clear(); dirty = true; flush() }
}
