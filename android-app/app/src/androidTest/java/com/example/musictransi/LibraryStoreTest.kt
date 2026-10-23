package com.example.musictransi

import androidx.test.platform.app.InstrumentationRegistry
import com.example.musictransi.library.LibraryStore
import com.example.musictransi.source.Album
import com.example.musictransi.source.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryStoreTest {
    @Test fun librarySurvivesRestartAndKeepsCollectionsIndependent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "library-test-${System.nanoTime()}.db"
        var store = LibraryStore(context, name)
        val a = Track("content://test/a", "First song", "Artist", 180, true, "https://example.com/a.jpg")
        val b = Track("https://example.com/b", "Second song", "Artist", 200, music = true)
        val album = Album("https://example.com/album", "Album", "Artist", "https://example.com/cover", 12)
        try {
            store.toggleLike(a)
            val id = store.createPlaylist("  Road trip  ")
            store.addTracks(id, listOf(a, b, a))
            assertEquals(listOf(a.id, b.id), store.state.value.playlists.single().tracks.map { it.id })
            store.moveTrack(id, b.id, -1)
            store.renamePlaylist(id, "Night drive")
            store.toggleAlbum(album)
            store.close(); store = LibraryStore(context, name)
            // A transaction also waits for the reopened DB; no sleep-based startup assumption.
            store.renamePlaylist(id, "Night drive")
            assertEquals(a, store.state.value.likes.single())
            assertEquals(album, store.state.value.albums.single())
            assertEquals("Night drive", store.state.value.playlists.single().name)
            assertEquals(listOf(b.id, a.id), store.state.value.playlists.single().tracks.map { it.id })
            store.removeTrack(id, a.id)
            assertEquals(listOf(a), store.state.value.likes)
            store.deletePlaylist(id)
            assertTrue(store.state.value.playlists.isEmpty())
            assertEquals(listOf(a), store.state.value.likes)
            store.removeLike(a.id); store.removeLike(a.id); store.toggleAlbum(album)
            assertTrue(store.state.value.likes.isEmpty())
            assertTrue(store.state.value.albums.isEmpty())
        } finally { store.close(); context.deleteDatabase(name) }
    }
    @Test fun rejectsBlankNamesAndAddingToDeletedPlaylists() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "library-invalid-${System.nanoTime()}.db"
        val store = LibraryStore(context, name)
        try {
            try { store.createPlaylist("   "); fail("Blank name accepted") } catch (_: IllegalArgumentException) { }
            val id = store.createPlaylist("Keep")
            store.deletePlaylist(id)
            try { store.addTracks(id, listOf(Track("x", "Song", "Artist"))); fail("Deleted playlist accepted") } catch (_: IllegalStateException) { }
            assertTrue(store.state.value.playlists.isEmpty())
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
