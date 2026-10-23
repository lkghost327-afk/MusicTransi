package com.example.musictransi

import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.example.musictransi.library.*
import com.example.musictransi.lyrics.LyricCards
import com.example.musictransi.source.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ImportAndShareTest {
    @Test fun parsesQuotedExportsAndJsonCollections() {
        val csv = "title,artist,playlist,type\r\n\"Song, One\",Artist,Driving,playlist\r\nSecond,Artist,,likes\r\nAlbum,Artist,,album"
        val rows = LibraryImport.parse(csv)
        assertEquals(3, rows.size); assertEquals("Song, One", rows.first().title); assertEquals("likes", rows[1].kind)
        val json = """{"playlists":[{"name":"Drive","items":[{"track":{"trackName":"Song","artistName":"Artist"}}]}],"likedSongs":[{"title":"Like","artist":"Artist"}],"albums":[{"name":"Album","artist":"Artist"}]}"""
        val parsed = LibraryImport.parse(json)
        assertEquals(listOf("playlist", "likes", "album"), parsed.map { it.kind })
        try { LibraryImport.parse("title,artist\n\"broken,Artist"); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun importsMergeWithoutDuplicatesAndInvalidBatchRollsBack() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = "import-test-${System.nanoTime()}.db"; val store = LibraryStore(context, db)
        val song = Track("id", "Song", "Artist", 180, music = true); val album = Album("album", "Album", "Artist")
        try {
            repeat(2) { store.importCollections(mapOf("Drive" to listOf(song, song)), listOf(song), listOf(album)) }
            assertEquals(1, store.state.value.playlists.size); assertEquals(listOf(song), store.state.value.playlists.single().tracks)
            assertEquals(listOf(song), store.state.value.likes); assertEquals(listOf(album), store.state.value.albums)
            try { store.importCollections(linkedMapOf("New" to listOf(song), "" to listOf(song)), emptyList(), emptyList()); fail() } catch (_: IllegalArgumentException) { }
            store.renamePlaylist(store.state.value.playlists.single().id, "Drive")
            assertEquals(1, store.state.value.playlists.size)
        } finally { store.close(); context.deleteDatabase(db) }
    }
    @Test fun selectedSearchHistoryIsSeparateBoundedAndDurable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "search-test-${System.nanoTime()}"; val history = ListeningHistory(context, name, 30)
        try {
            (0..32).forEach { history.record(Track("id$it", "Song $it", "Artist")) }
            history.record(Track("id10", "Song 10", "Artist"))
            assertEquals(30, history.tracks.value.size); assertEquals("id10", history.tracks.value.first().id)
            val reopened = ListeningHistory(context, name, 30)
            assertEquals(history.tracks.value, reopened.tracks.value)
            reopened.remove("id10"); assertEquals(29, reopened.tracks.value.size)
            reopened.clear(); assertTrue(ListeningHistory(context, name, 30).tracks.value.isEmpty())
        } finally { context.deleteSharedPreferences(name) }
    }
    @Test fun shareCardIsBoundedReadableAndOnlySharesCacheSubdirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(LyricCards.canSelect(List(5) { "Synthetic line" }))
        assertFalse(LyricCards.canSelect(listOf("x".repeat(281))))
        val art = android.graphics.Bitmap.createBitmap(300, 200, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) }
        val file = LyricCards.render(context, Track("id", "Synthetic song", "Test artist"), listOf("A synthetic line", "Another synthetic line"), art)
        art.recycle()
        file.copyTo(java.io.File(context.getExternalFilesDir(null), "lyric-card-preview.png"), overwrite = true)
        try {
            val bitmap = BitmapFactory.decodeFile(file.path); assertEquals(1080, bitmap.width); assertEquals(1350, bitmap.height)
            assertEquals(android.graphics.Color.CYAN, bitmap.getPixel(200, 200)); bitmap.recycle()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
            assertEquals("content", uri.scheme); assertTrue(uri.path!!.contains("lyrics"))
        } finally { file.delete() }
    }
    @Test fun coverIsResizedPrivateAndPlaylistMetadataSurvivesReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cover-test-${System.nanoTime()}.db"; var store = LibraryStore(context, name)
        val image = java.io.File(context.cacheDir, "test-cover-${System.nanoTime()}.png")
        var saved: java.io.File? = null
        try {
            val bitmap = android.graphics.Bitmap.createBitmap(900, 600, android.graphics.Bitmap.Config.ARGB_8888)
            image.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val path = PlaylistCovers.save(context, android.net.Uri.fromFile(image)); saved = java.io.File(java.net.URI(path))
            val cover = BitmapFactory.decodeFile(saved.path); assertEquals(512, cover.width); assertEquals(512, cover.height); cover.recycle()
            val id = store.createPlaylist("Cover"); store.setCover(id, path); store.close(); store = LibraryStore(context, name)
            store.renamePlaylist(id, "Cover"); assertEquals(path, store.state.value.playlists.single().cover)
        } finally { store.close(); context.deleteDatabase(name); image.delete(); saved?.delete() }
    }

    @Test fun upgradesExistingLibraryWithoutLosingPlaylist() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${System.nanoTime()}.db"
        val old = object : android.database.sqlite.SQLiteOpenHelper(context, name, null, 1) {
            override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
                db.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY, data TEXT NOT NULL)")
                db.execSQL("CREATE TABLE likes (track_id TEXT PRIMARY KEY, added INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
                db.execSQL("CREATE TABLE entries (playlist_id INTEGER NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id,track_id))")
                db.execSQL("CREATE TABLE albums (id TEXT PRIMARY KEY, data TEXT NOT NULL)")
                db.execSQL("INSERT INTO playlists(id,name) VALUES(1,'Existing')")
            }
            override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        old.writableDatabase; old.close()
        val store = LibraryStore(context, name)
        try { store.renamePlaylist(1, "Existing"); assertEquals("", store.state.value.playlists.single().cover); assertEquals("Existing", store.state.value.playlists.single().name) }
        finally { store.close(); context.deleteDatabase(name) }
    }
}
