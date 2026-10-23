package com.example.musictransi

import com.example.musictransi.source.YouTubeSource
import com.example.musictransi.source.MusicRadio
import com.example.musictransi.source.RadioPolicy
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in: contacts YouTube; reads only a small prefix to verify a playable URL. */
class SourceSmokeTest {
    @Test fun livePopularSongPlaybackRequest() = runBlocking {
        assumeTrue(System.getProperty("musictransi.liveSourceTest") == "true")
        val track = YouTubeSource.search("The Weeknd Blinding Lights").first()
        val audio = YouTubeSource.resolve(track)
        for (range in listOf<String?>(null, "bytes=0-", "bytes=0-1023")) {
            val request = Request.Builder().url(audio.url).header("User-Agent", "Mozilla/5.0")
            if (range != null) request.header("Range", range)
            YouTubeSource.http.newCall(request.build()).execute().use {
                println("Popular song: range=$range HTTP ${it.code}")
                assertTrue("Playback request returned HTTP ${it.code}", it.isSuccessful)
                assertTrue(it.body!!.byteStream().read() >= 0)
            }
        }
    }
    @Test fun liveSearchAndResolve() = runBlocking {
        assumeTrue(System.getProperty("musictransi.liveSourceTest") == "true")
        val results = YouTubeSource.search("Kevin MacLeod Carefree")
        assertTrue("Music search returned no tracks", results.isNotEmpty())
        assertTrue("Search did not supply artwork", results.first().artwork.startsWith("https://"))
        YouTubeSource.http.newCall(Request.Builder().url(results.first().artwork).build()).execute().use {
            assertTrue("Cover HTTP failed", it.isSuccessful)
            val image = javax.imageio.ImageIO.read(it.body!!.byteStream())
            assertNotNull("Cover did not decode", image)
            assertTrue(image.width > 0)
        }
        val resolved = YouTubeSource.resolve(results.first())
        assertTrue(resolved.url.startsWith("https://"))
        val request = Request.Builder().url(resolved.url).header("Range", "bytes=0-1023")
        resolved.headers.forEach { (name, value) -> request.header(name, value) }
        YouTubeSource.http.newCall(request.build()).execute().use { response ->
            assertTrue("Stream server returned HTTP ${response.code}", response.isSuccessful)
            assertTrue("Stream server returned no audio", response.body!!.byteStream().read() >= 0)
        }
        val related = YouTubeSource.recommendations(results.first(), listOf(results.first()))
        assertTrue("Autoplay returned no songs", related.isNotEmpty())
        assertTrue(related.all { it.automatic && it.id != results.first().id })
        assertTrue(related.all { RadioPolicy.eligible(it) && RadioPolicy.songKey(it) != RadioPolicy.songKey(results.first()) })
        val next = YouTubeSource.resolve(related.first())
        assertTrue("Recommended song has no audio URL", next.url.startsWith("https://"))
    }
    @Test fun liveMusicRadioUsesAlbumTracksAcrossArtists() = runBlocking {
        assumeTrue(System.getProperty("musictransi.liveSourceTest") == "true")
        for (query in listOf("The Weeknd Blinding Lights", "Arijit Singh Tum Hi Ho")) {
            val seed = YouTubeSource.search(query).first()
            val radio = MusicRadio.fetch(seed)
            assertTrue("No music-radio album tracks for $query", radio.isNotEmpty())
            val eligible = RadioPolicy.select(radio, listOf(seed))
            assertTrue("No new songs after filtering $query", eligible.isNotEmpty())
            println("Radio for ${seed.title}: " + eligible.joinToString { "${it.artist} — ${it.title}" })
        }
    }
}
