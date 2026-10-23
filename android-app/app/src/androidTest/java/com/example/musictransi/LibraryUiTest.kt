package com.example.musictransi

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musictransi.library.LibraryStore
import com.example.musictransi.source.Track
import com.example.musictransi.ui.LibraryScreen
import com.example.musictransi.ui.PlaylistPicker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun createRenameAndDeletePlaylistThroughUi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "ui-library-${System.nanoTime()}.db"
        val store = LibraryStore(context, name)
        try {
            compose.setContent { val state by store.state.collectAsState(); MaterialTheme {
                LibraryScreen(state, store, Modifier.fillMaxSize(), { _, _, _ -> }, {}, {}, {})
            } }
            compose.waitUntil(10000) { !store.state.value.loading }
            compose.onNodeWithText("＋ New playlist").performClick()
            compose.onNode(hasSetTextAction()).performClick().assertIsFocused().performTextInput("Road trip")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10000) { store.state.value.playlists.any { it.name == "Road trip" } }
            compose.onNodeWithText("‹ Library").performClick()
            compose.onNodeWithContentDescription("Playlist options for Road trip").performClick()
            compose.onNodeWithText("Rename").performClick()
            compose.onNode(hasSetTextAction()).performClick().assertIsFocused().performTextReplacement("Night drive")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10000) { store.state.value.playlists.any { it.name == "Night drive" } }
            compose.onNodeWithContentDescription("Playlist options for Night drive").performClick()
            compose.onNodeWithText("Delete playlist").performClick()
            compose.onNodeWithText("Delete").performClick()
            compose.waitUntil(10000) { store.state.value.playlists.isEmpty() }
        } finally { store.close(); context.deleteDatabase(name) }
    }

    @Test fun songPickerCreatesPlaylistAndAddsSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "ui-picker-${System.nanoTime()}.db"
        val store = LibraryStore(context, name)
        val song = Track("test:song", "Song", "Artist")
        var dismissed = false
        try {
            compose.setContent { val state by store.state.collectAsState(); MaterialTheme {
                PlaylistPicker(listOf(song), state, store) { dismissed = true }
            } }
            compose.onNodeWithText("＋ New playlist").performClick()
            compose.onNode(hasSetTextAction()).performClick().assertIsFocused().performTextInput("Favourites")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10000) { dismissed }
            assertEquals(listOf(song), store.state.value.playlists.single().tracks)
            runBlocking { store.addTracks(store.state.value.playlists.single().id, listOf(song)) }
            assertEquals(1, store.state.value.playlists.single().tracks.size)
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
