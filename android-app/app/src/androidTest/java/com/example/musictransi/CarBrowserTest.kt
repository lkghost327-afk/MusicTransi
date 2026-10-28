package com.example.musictransi

import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musictransi.playback.MixService
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CarBrowserTest {
    @Test fun browserConnectsWithoutActivityAndExposesLibraryAndSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val main = Handler(Looper.getMainLooper())
        val connected = CountDownLatch(1)
        val loaded = CountDownLatch(1)
        var failure: String? = null
        var children: List<MediaBrowser.MediaItem> = emptyList()
        lateinit var browser: MediaBrowser
        main.post {
            browser = MediaBrowser(context, ComponentName(context, MixService::class.java), object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() { connected.countDown() }
                override fun onConnectionFailed() { failure = "Connection failed"; connected.countDown() }
            }, null)
            browser.connect()
        }
        try {
            assertTrue("Browser timed out", connected.await(10, TimeUnit.SECONDS))
            assertNull(failure)
            main.post {
                assertNotNull(MediaController(context, browser.sessionToken))
                browser.subscribe(browser.root, object : MediaBrowser.SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String, result: MutableList<MediaBrowser.MediaItem>) { children = result; loaded.countDown() }
                    override fun onError(parentId: String) { failure = "Root failed"; loaded.countDown() }
                })
            }
            assertTrue("Root timed out", loaded.await(10, TimeUnit.SECONDS))
            assertNull(failure)
            assertEquals(listOf("likes", "recent", "playlists", "queue"), children.map { it.mediaId })
            assertTrue(children.all { it.isBrowsable })
        } finally { main.post { browser.disconnect() } }
    }
}
