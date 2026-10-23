package com.example.musictransi

import androidx.test.platform.app.InstrumentationRegistry
import com.example.musictransi.library.*
import com.example.musictransi.source.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PersonalMixStorageTest {
    @Test fun historyIsBoundedDurableAndClearableAndMixSavesAtomically() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "mix-history-${System.nanoTime()}"
        val db = "$name.db"
        val history = ListeningHistory(context, name)
        val store = LibraryStore(context, db)
        val songs = (0..82).map { Track("id$it", "Song $it", "Artist", 180, music = true) }
        try {
            songs.forEach { history.record(it) }
            assertEquals(80, history.tracks.value.size)
            history.record(songs.last())
            assertEquals(80, history.tracks.value.size)
            val reopened = ListeningHistory(context, name)
            assertEquals(songs.last(), reopened.tracks.value.first())
            val id = store.createPlaylist("Suggested mix", listOf(songs[0], songs[1], songs[0]))
            assertEquals(listOf(songs[0], songs[1]), store.state.value.playlists.single { it.id == id }.tracks)
            reopened.clear()
            assertTrue(ListeningHistory(context, name).tracks.value.isEmpty())
        } finally { store.close(); context.deleteDatabase(db); context.deleteSharedPreferences(name) }
    }
}
