package com.example.musictransi

import androidx.test.platform.app.InstrumentationRegistry
import com.example.musictransi.downloads.OfflineDownloads
import com.example.musictransi.source.YouTubeSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class OfflineDownloadsTest {
    @Test fun downloadsSurviveStoreReopenAndResolveToLocalAudio() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSourceTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val track = YouTubeSource.search("The Weeknd Blinding Lights").first()
        val store = OfflineDownloads(context)
        assumeTrue(store.entries().none { it.track.id == track.id })
        try {
            store.enqueue(listOf(track, track), false)
            assertEquals(1, store.entries().count { it.track.id == track.id })
            store.queued().firstOrNull { it.getString("id") == track.id }?.let { store.prepare(it) }
            val deadline = System.currentTimeMillis() + 180000
            while (System.currentTimeMillis() < deadline && store.localAudio(track) == null) delay(500)
            val reopened = OfflineDownloads(context)
            val audio = reopened.localAudio(track)
            assertNotNull("Offline file not ready: ${reopened.entries()}", audio)
            assertTrue(audio!!.url.startsWith("file:"))
            assertTrue(java.io.File(java.net.URI(audio.url)).length() > 1000)
            store.enqueue(listOf(track), false)
            assertEquals(audio, store.localAudio(track))
        } finally { store.remove(track.id) }
        assertNull(store.localAudio(track))
    }
}
