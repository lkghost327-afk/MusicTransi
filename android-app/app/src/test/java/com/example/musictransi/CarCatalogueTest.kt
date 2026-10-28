package com.example.musictransi

import com.example.musictransi.library.LibraryState
import com.example.musictransi.library.SavedPlaylist
import com.example.musictransi.playback.CarCatalogue
import com.example.musictransi.source.Track
import org.junit.Assert.*
import org.junit.Test

class CarCatalogueTest {
    @Test fun savedIdResolvesWithoutAnyBrowseCacheAndTracksLibraryChanges() {
        val song = Track("https://example.com/गीत?x=1", "Song", "Artist")
        val id = CarCatalogue.mediaId("playlist:42", song)
        val decoded = CarCatalogue.decode(id)!!
        assertEquals("playlist:42" to song.id, decoded)
        val saved = LibraryState(playlists = listOf(SavedPlaylist(42, "Trip", listOf(song))), loading = false)
        assertEquals(listOf(song), CarCatalogue.collection(decoded.first, saved, emptyList(), emptyList()))
        assertTrue(CarCatalogue.collection(decoded.first, saved.copy(playlists = emptyList()), emptyList(), emptyList()).isEmpty())
    }

    @Test fun invalidIdsCannotSelectArbitraryUrlsOrCollections() {
        for (id in listOf(null, "", "https://example.com", "root/item/YQ==", "playlist:-1/item/YQ==",
            "likes/item/!!", "likes/item/", "likes/item/" + "a".repeat(17000))) assertNull(CarCatalogue.decode(id))
    }
}
