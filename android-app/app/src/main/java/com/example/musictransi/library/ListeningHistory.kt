package com.example.musictransi.library

import android.content.Context
import com.example.musictransi.source.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Counts elapsed audible time, independent of seek position. One event per play session. */
class ListeningProgress {
    private var last: Long? = null
    private var wasPlaying = false
    private var heard = 0L
    private var recorded = false
    fun reset() { last = null; wasPlaying = false; heard = 0; recorded = false }
    fun observe(playing: Boolean, now: Long): Boolean {
        val previous = last
        if (playing && wasPlaying && previous != null) heard += (now - previous).coerceIn(0, 1000)
        last = now; wasPlaying = playing
        if (!recorded && heard >= 30000) { recorded = true; return true }
        return false
    }
}

/** Only the last 80 distinct recordings, stored locally; no analytics account. */
class ListeningHistory(context: Context, name: String = "listening-history", private val limit: Int = 80) {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(read())
    val tracks = mutable.asStateFlow()
    private fun read(): List<Track> = runCatching {
        val a = JSONArray(prefs.getString("tracks", "[]"))
        (0 until minOf(a.length(), limit)).map { i -> val j = a.getJSONObject(i)
            Track(j.getString("id"), j.getString("title"), j.getString("artist"), j.optLong("duration"), j.optBoolean("local"), j.optString("artwork"), music = j.optBoolean("music")) }
    }.getOrDefault(emptyList())
    suspend fun record(track: Track) = update { (listOf(track.copy(automatic = false)) + it.filter { old -> old.id != track.id }).take(limit) }
    suspend fun clear() = update { emptyList() }
    suspend fun remove(id: String) = update { it.filterNot { track -> track.id == id } }
    @android.annotation.SuppressLint("UseKtx") // commit's boolean is required before publishing durable state.
    private suspend fun update(change: (List<Track>) -> List<Track>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = change(mutable.value)
            val a = JSONArray()
            next.forEach { t -> a.put(JSONObject().put("id", t.id).put("title", t.title).put("artist", t.artist)
                .put("duration", t.durationSeconds).put("local", t.local).put("artwork", t.artwork).put("music", t.music)) }
            check(prefs.edit().putString("tracks", a.toString()).commit())
            mutable.value = next
        }
    }
}
