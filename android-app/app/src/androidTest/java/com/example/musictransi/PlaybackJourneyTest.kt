package com.example.musictransi

import android.content.*
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.musictransi.playback.MixService
import com.example.musictransi.source.Track
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class PlaybackJourneyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun awaitCondition(message: String, timeoutMs: Long = 20000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            var result = false
            main { result = check() }
            if (result) return
            Thread.sleep(50)
        }
        fail(message)
    }

    @Test fun backgroundCrossfadePauseResumeAndQueuePersistence() {
        val context = instrumentation.targetContext
        val fixture = File(context.filesDir, "journey-tone.wav")
        createTone(fixture)
        val ready = CountDownLatch(1)
        lateinit var service: MixService
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as MixService.LocalBinder).service
                ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) { }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            context.bindService(Intent(context, MixService::class.java), connection, Context.BIND_AUTO_CREATE)
            assertTrue("Service did not connect", ready.await(10, TimeUnit.SECONDS))
            main {
                service.clearQueue()
                context.startService(Intent(context, MixService::class.java))
                service.setCrossfade(2)
                service.enqueue(Track(fixture.toURI().toString(), "Test deck A", "Synthetic test audio", 9, true))
                service.enqueue(Track(fixture.toURI().toString(), "Test deck B", "Synthetic test audio", 9, true))
            }
            awaitCondition("First deck never played") { service.state.value.positionMs > 400 }
            scenario.moveToState(Lifecycle.State.CREATED)
            awaitCondition("No overlap while Activity was backgrounded") { service.state.value.blend in 0.1f..0.8f }
            var pausedPosition = 0L
            var pausedBlend = 0f
            main {
                service.pause()
                pausedPosition = service.state.value.positionMs
                pausedBlend = service.state.value.blend
            }
            Thread.sleep(400)
            main {
                assertFalse(service.state.value.playing)
                assertTrue("Position advanced while paused", service.state.value.positionMs - pausedPosition < 200)
                assertEquals(pausedBlend, service.state.value.blend, 0.08f)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            main { service.play() }
            awaitCondition("Incoming deck was not promoted") { service.state.value.index == 1 }
            main {
                assertEquals("Test deck B", service.state.value.current?.title)
                assertTrue(service.state.value.playing)
                // Queue edits must preserve the audible deck and persist the new order.
                service.setCrossfade(0)
                val upcoming = Track(fixture.toURI().toString(), "Queued C", "Synthetic test audio", 9, true)
                service.enqueue(upcoming)
                service.playNext(upcoming.copy(title = "Queued next"))
                assertEquals("Queued next", service.state.value.queue[service.state.value.index + 1].title)
                service.moveQueued(service.state.value.index + 2, service.state.value.index + 1)
                assertEquals("Queued C", service.state.value.queue[service.state.value.index + 1].title)
                service.shuffleUpcoming()
                assertEquals("Test deck B", service.state.value.current?.title)
                service.clearUpcoming()
                assertTrue(service.state.value.playing)
                assertFalse(service.state.value.autoplay)
                assertEquals(2, service.state.value.queue.size)
                service.stopPlayback()
                assertFalse(service.state.value.playing)
                assertEquals(2, service.state.value.queue.size)
                val persisted = context.getSharedPreferences("session", Context.MODE_PRIVATE)
                assertEquals(1, persisted.getInt("index", -1))
                assertTrue(persisted.getString("queue", "")!!.contains("Test deck B"))
            }
        } finally {
            if (ready.count == 0L) main { service.clearQueue() }
            context.unbindService(connection)
            scenario.close()
            fixture.delete()
        }
    }

    private fun createTone(file: File) {
        val rate = 22050
        val samples = rate * 9
        val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { buffer.putShort((sin(it * 2 * Math.PI * 220 / rate) * 200).toInt().toShort()) }
        file.writeBytes(buffer.array())
    }
}
