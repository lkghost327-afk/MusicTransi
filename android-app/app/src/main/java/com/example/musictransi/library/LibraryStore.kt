package com.example.musictransi.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.musictransi.source.Album
import com.example.musictransi.source.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class SavedPlaylist(val id: Long, val name: String, val tracks: List<Track>, val cover: String = "")
data class LibraryState(val likes: List<Track> = emptyList(), val playlists: List<SavedPlaylist> = emptyList(),
    val albums: List<Album> = emptyList(), val loading: Boolean = true, val error: String? = null)

/** Local metadata only. SQLite transactions keep edits durable without another library or server. */
class LibraryStore(context: Context, databaseName: String = "library.db") : AutoCloseable {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, databaseName, null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY, data TEXT NOT NULL)")
            db.execSQL("CREATE TABLE likes (track_id TEXT PRIMARY KEY, added INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, cover TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE entries (playlist_id INTEGER NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id, track_id))")
            db.execSQL("CREATE TABLE albums (id TEXT PRIMARY KEY, data TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion < 2) db.execSQL("ALTER TABLE playlists ADD COLUMN cover TEXT NOT NULL DEFAULT ''") }
    }
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(LibraryState())
    val state = mutable.asStateFlow()
    init { scope.launch { mutex.withLock {
        try { mutable.value = snapshot(helper.readableDatabase) }
        catch (_: Exception) { mutable.value = LibraryState(loading = false, error = "Couldn't open your library. Restart the app to retry.") }
    } } }

    private suspend fun <T> edit(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = helper.writableDatabase
            db.beginTransaction()
            val result = try { block(db).also {
                db.execSQL("DELETE FROM tracks WHERE id NOT IN (SELECT track_id FROM likes) AND id NOT IN (SELECT track_id FROM entries)")
                db.setTransactionSuccessful()
            } } finally { db.endTransaction() }
            mutable.value = snapshot(db)
            result
        }
    }
    suspend fun toggleLike(track: Track) = edit { db ->
        saveTrack(db, track)
        if (db.delete("likes", "track_id=?", arrayOf(track.id)) == 0) db.insertOrThrow("likes", null,
            ContentValues().apply { put("track_id", track.id); put("added", System.currentTimeMillis()) })
        Unit
    }
    suspend fun createPlaylist(name: String, initialTracks: List<Track> = emptyList()): Long = edit { db ->
        val id = db.insertOrThrow("playlists", null, ContentValues().apply { put("name", validName(name)) })
        initialTracks.distinctBy { it.id }.forEachIndexed { position, track ->
            saveTrack(db, track)
            db.insertOrThrow("entries", null, ContentValues().apply { put("playlist_id", id); put("track_id", track.id); put("position", position) })
        }
        id
    }
    suspend fun setCover(id: Long, path: String) = edit { db ->
        check(db.update("playlists", ContentValues().apply { put("cover", path) }, "id=?", arrayOf(id.toString())) == 1)
    }
    suspend fun importCollections(playlists: Map<String, List<Track>>, likes: List<Track>, albums: List<Album>) = edit { db ->
        playlists.forEach { (name, songs) ->
            val existing = db.rawQuery("SELECT id FROM playlists WHERE name=? ORDER BY id LIMIT 1", arrayOf(validName(name))).use { if (it.moveToFirst()) it.getLong(0) else null }
            val id = existing ?: db.insertOrThrow("playlists", null, ContentValues().apply { put("name", validName(name)) })
            var position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM entries WHERE playlist_id=?", arrayOf(id.toString())).use { it.moveToFirst(); it.getInt(0) }
            songs.forEach { t -> saveTrack(db, t); db.insertWithOnConflict("entries", null, ContentValues().apply { put("playlist_id", id); put("track_id", t.id); put("position", position++) }, SQLiteDatabase.CONFLICT_IGNORE) }
        }
        likes.forEach { t -> saveTrack(db, t); db.insertWithOnConflict("likes", null, ContentValues().apply { put("track_id", t.id); put("added", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_IGNORE) }
        albums.forEach { a -> db.insertWithOnConflict("albums", null, ContentValues().apply { put("id", a.id); put("data", JSONObject().put("title", a.title).put("artist", a.artist).put("artwork", a.artwork).put("count", a.trackCount).toString()) }, SQLiteDatabase.CONFLICT_IGNORE) }
    }
    suspend fun removeLike(trackId: String) = edit { db -> db.delete("likes", "track_id=?", arrayOf(trackId)); Unit }
    suspend fun renamePlaylist(id: Long, name: String) = edit { db ->
        db.update("playlists", ContentValues().apply { put("name", validName(name)) }, "id=?", arrayOf(id.toString())); Unit
    }
    suspend fun deletePlaylist(id: Long) = edit { db ->
        db.delete("entries", "playlist_id=?", arrayOf(id.toString()))
        db.delete("playlists", "id=?", arrayOf(id.toString())); Unit
    }
    suspend fun addTracks(id: Long, tracks: List<Track>) = edit { db ->
        check(db.rawQuery("SELECT id FROM playlists WHERE id=?", arrayOf(id.toString())).use { it.moveToFirst() }) { "Playlist no longer exists" }
        var position = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM entries WHERE playlist_id=?", arrayOf(id.toString())).use { it.moveToFirst(); it.getInt(0) }
        tracks.forEach { track ->
            saveTrack(db, track)
            db.insertWithOnConflict("entries", null, ContentValues().apply {
                put("playlist_id", id); put("track_id", track.id); put("position", position++)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }; Unit
    }
    suspend fun removeTrack(id: Long, trackId: String) = edit { db ->
        db.delete("entries", "playlist_id=? AND track_id=?", arrayOf(id.toString(), trackId)); Unit
    }
    suspend fun moveTrack(id: Long, trackId: String, delta: Int) = edit { db ->
        val ids = db.rawQuery("SELECT track_id FROM entries WHERE playlist_id=? ORDER BY position", arrayOf(id.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }.toMutableList()
        }
        val from = ids.indexOf(trackId)
        if (from >= 0) {
            val to = (from + delta).coerceIn(0, ids.lastIndex)
            ids.add(to, ids.removeAt(from))
            ids.forEachIndexed { index, key -> db.update("entries", ContentValues().apply { put("position", index) },
                "playlist_id=? AND track_id=?", arrayOf(id.toString(), key)) }
        }; Unit
    }
    suspend fun toggleAlbum(album: Album) = edit { db ->
        if (db.delete("albums", "id=?", arrayOf(album.id)) == 0) db.insertOrThrow("albums", null,
            ContentValues().apply { put("id", album.id); put("data", JSONObject().put("title", album.title)
                .put("artist", album.artist).put("artwork", album.artwork).put("count", album.trackCount).toString()) })
        Unit
    }
    private fun validName(name: String): String = name.trim().also { require(it.isNotEmpty() && it.length <= 80) { "Use a playlist name between 1 and 80 characters" } }
    private fun saveTrack(db: SQLiteDatabase, t: Track) {
        val json = JSONObject().put("title", t.title).put("artist", t.artist).put("duration", t.durationSeconds)
            .put("local", t.local).put("artwork", t.artwork).put("music", t.music)
        db.insertWithOnConflict("tracks", null, ContentValues().apply { put("id", t.id); put("data", json.toString()) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    private fun snapshot(db: SQLiteDatabase): LibraryState {
        val tracks = db.rawQuery("SELECT id,data FROM tracks", null).use { c -> buildMap {
            while (c.moveToNext()) { val j = JSONObject(c.getString(1)); val id = c.getString(0)
                put(id, Track(id, j.getString("title"), j.getString("artist"), j.optLong("duration"), j.optBoolean("local"), j.optString("artwork"), music = j.optBoolean("music"))) }
        } }
        val likes = db.rawQuery("SELECT track_id FROM likes ORDER BY added DESC, rowid DESC", null).use { c -> buildList {
            while (c.moveToNext()) tracks[c.getString(0)]?.let(::add)
        } }
        val lists = db.rawQuery("SELECT id,name,cover FROM playlists ORDER BY id DESC", null).use { c -> buildList {
            while (c.moveToNext()) { val id = c.getLong(0)
                val items = db.rawQuery("SELECT track_id FROM entries WHERE playlist_id=? ORDER BY position", arrayOf(id.toString())).use { e -> buildList {
                    while (e.moveToNext()) tracks[e.getString(0)]?.let(::add)
                } }
                add(SavedPlaylist(id, c.getString(1), items, c.getString(2)))
            }
        } }
        val albums = db.rawQuery("SELECT id,data FROM albums ORDER BY rowid DESC", null).use { c -> buildList {
            while (c.moveToNext()) { val j = JSONObject(c.getString(1)); add(Album(c.getString(0), j.getString("title"), j.getString("artist"), j.optString("artwork"), j.optLong("count"))) }
        } }
        return LibraryState(likes, lists, albums, loading = false)
    }
    override fun close() { scope.cancel(); helper.close() }
}
