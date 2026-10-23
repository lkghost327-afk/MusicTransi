package com.example.musictransi

import android.content.*
import android.os.IBinder
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musictransi.playback.MixService
import com.example.musictransi.source.YouTubeSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OnlinePlaybackTest {
    @Test fun popularSongActuallyPlaysOnAndroid() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSourceTest") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val track = YouTubeSource.search("The Weeknd Blinding Lights").first()
        lateinit var service: MixService
        val ready = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as MixService.LocalBinder).service; ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            context.bindService(Intent(context, MixService::class.java), connection, Context.BIND_AUTO_CREATE)
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                service.clearQueue(); service.setAutoplay(false)
                context.startService(Intent(context, MixService::class.java))
                service.enqueue(track)
                if (service.state.value.hapticsSupported) {
                    service.setHaptics(true)
                    assertTrue(service.state.value.haptics)
                    assertTrue(context.getSharedPreferences("session", Context.MODE_PRIVATE).getBoolean("haptics", false))
                    service.setHaptics(false)
                    assertFalse(service.state.value.haptics)
                }
            }
            val deadline = System.currentTimeMillis() + 60000
            while (System.currentTimeMillis() < deadline && service.state.value.positionMs < 1500) Thread.sleep(100)
            instrumentation.runOnMainSync {
                assertTrue("Live playback failed: ${service.state.value.message}", service.state.value.positionMs >= 1500)
                assertTrue(service.state.value.durationMs > 60000)
                assertTrue(service.state.value.current!!.artwork.isNotBlank())
            }
            val notifications = context.getSystemService(android.app.NotificationManager::class.java)
            val artDeadline = System.currentTimeMillis() + 30000
            while (System.currentTimeMillis() < artDeadline && notifications.activeNotifications
                    .firstOrNull { it.id == MixService.NOTIFICATION }?.notification?.getLargeIcon() == null) Thread.sleep(100)
            val notification = notifications.activeNotifications.first { it.id == MixService.NOTIFICATION }.notification
            assertNotNull("Notification cover missing", notification.getLargeIcon())
            assertEquals(listOf("Previous", "Pause", "Next"), notification.actions.take(3).map { it.title.toString() })
            assertTrue(notification.actions.all { it.getIcon() != null })
            val likedBefore = service.library.state.value.likes.any { it.id == track.id }
            notification.actions[3].actionIntent.send()
            val likeDeadline = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < likeDeadline && service.library.state.value.likes.any { it.id == track.id } == likedBefore) Thread.sleep(100)
            assertEquals(!likedBefore, service.library.state.value.likes.any { it.id == track.id })
            service.library.toggleLike(track) // Restore the pre-test library state.
        } finally {
            if (ready.count == 0L) instrumentation.runOnMainSync { service.clearQueue(); service.setAutoplay(true) }
            context.unbindService(connection); activity.close()
        }
    }
}
