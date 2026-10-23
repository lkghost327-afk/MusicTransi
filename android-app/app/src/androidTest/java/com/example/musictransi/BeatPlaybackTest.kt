package com.example.musictransi

import android.content.*
import android.os.IBinder
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musictransi.playback.MixService
import com.example.musictransi.source.Track
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sin

class BeatPlaybackTest {
    @Test fun twoTempoTracksUseBeatSyncAndPromote() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val observed = linkedSetOf<String>()
        fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
        fun await(message: String, seconds: Int, check: () -> Boolean) {
            val end = System.currentTimeMillis() + seconds * 1000
            while (System.currentTimeMillis() < end) {
                var ok = false; main { ok = check() }; if (ok) return
                Thread.sleep(100)
            }
            fail("$message; observed: $observed")
        }
        val files = listOf(File(context.filesDir, "beat-a.wav"), File(context.filesDir, "beat-b.wav"))
        files.forEachIndexed { i, file -> tone(file, if (i == 0) 120 else 118) }
        val ready = CountDownLatch(1)
        lateinit var service: MixService
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
            main {
                service.clearQueue(); service.setAutoplay(false); service.setCrossfade(8)
                context.startService(Intent(context, MixService::class.java))
                files.forEachIndexed { i, file -> service.enqueue(Track(file.toURI().toString(), "Beat $i", "Fixture", 35, true)) }
            }
            await("Muted analysis did not complete", 27) { service.state.value.mixStatus.startsWith("Next ·") }
            main { assertEquals(0, service.state.value.index); assertEquals(0f, service.state.value.blend) }
            await("Beat sync never started: ${service.state.value.mixStatus}", 20) {
                observed.add("${service.state.value.mixStatus}, index=${service.state.value.index}, position=${service.state.value.positionMs / 1000}, blend=${service.state.value.blend}")
                service.state.value.blend > .05f && service.state.value.mixStatus.startsWith("Beat sync")
            }
            main { service.pause() }
            val position = service.state.value.positionMs
            Thread.sleep(400)
            main { assertTrue(service.state.value.positionMs - position < 200); service.play() }
            await("Beat-matched deck was not promoted", 12) { service.state.value.index == 1 }
            main { assertTrue(service.state.value.playing) }
        } finally {
            if (ready.count == 0L) main { service.clearQueue(); service.setAutoplay(true) }
            context.unbindService(connection); activity.close(); files.forEach { it.delete() }
        }
    }
    private fun tone(file: File, bpm: Int) {
        val rate = 8000; val samples = rate * 35
        val b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        b.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) {
            val phase = (it.toDouble() / rate) % (60.0 / bpm)
            val amplitude = if (phase < .06) 14000 else 0
            b.putShort((sin(it * 2 * Math.PI * 120 / rate) * amplitude).toInt().toShort())
        }
        file.writeBytes(b.array())
    }
}
