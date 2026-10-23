package com.example.musictransi

import com.example.musictransi.source.YouTubeSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class AlbumSourceTest {
    @Test fun liveAlbumSearchAndOrderedTracks() = runBlocking {
        assumeTrue(System.getProperty("musictransi.liveSourceTest") == "true")
        val albums = YouTubeSource.searchAlbums("The Weeknd After Hours")
        assertTrue("No albums returned", albums.isNotEmpty())
        val album = albums.first { it.title.equals("After Hours", true) }
        assertTrue(album.artwork.startsWith("https://"))
        var page = YouTubeSource.albumTracks(album)
        val songs = page.tracks.toMutableList()
        var pages = 1
        while (page.next != null && pages++ < 5) { page = YouTubeSource.albumTracks(album, page.next); songs += page.tracks }
        assertTrue("Album unexpectedly empty", songs.size >= 10)
        assertTrue("Album did not contain Blinding Lights", songs.any { it.title.contains("Blinding Lights", true) })
        assertTrue(songs.all { it.id.startsWith("https://") && it.music })
        assertEquals(songs.size, songs.map { it.id }.distinct().size)
        println("Album ${album.title}: ${songs.size} songs; first=${songs.first().title}")
    }
}
