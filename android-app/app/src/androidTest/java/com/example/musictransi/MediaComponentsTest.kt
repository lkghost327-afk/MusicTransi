package com.example.musictransi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.musictransi.playback.AudioProbe
import com.example.musictransi.source.ArtworkPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MediaComponentsTest {
    @Test fun coverActuallyDecodesWithComputedSampleSize() {
        val original = Bitmap.createBitmap(1600, 1600, Bitmap.Config.ARGB_8888)
        original.eraseColor(android.graphics.Color.MAGENTA)
        val output = ByteArrayOutputStream()
        original.compress(Bitmap.CompressFormat.PNG, 100, output)
        val bytes = output.toByteArray()
        val options = BitmapFactory.Options().apply { inSampleSize = ArtworkPolicy.sampleSize(1600, 1600) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        assertNotNull(decoded)
        assertEquals(800, decoded.width)
        original.recycle(); decoded.recycle()
    }
    @Test fun audioAnalysisPreservesPcmAndAlignsPulsesWithPlayback() {
        val probe = AudioProbe()
        probe.configure(AudioFormat(8000, 1, C.ENCODING_PCM_16BIT)); probe.flush()
        probe.queueInput(androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER)
        assertEquals(0, probe.output.remaining())
        fun audio(): ByteBuffer = ByteBuffer.allocateDirect(16000).order(ByteOrder.nativeOrder()).apply {
            repeat(8000) { putShort((sin(it * 2 * Math.PI * 120 / 8000) * 26000).toInt().toShort()) }
            flip()
        }
        val input = audio()
        val expected = ByteArray(input.remaining()); input.duplicate().get(expected)
        probe.queueInput(input)
        val output = probe.output
        val actual = ByteArray(output.remaining()); output.get(actual)
        assertArrayEquals(expected, actual)
        assertEquals(0f, probe.pulseAt(0))
        assertTrue(probe.pulseAt(20) > 0f)
        assertEquals(0f, probe.pulseAt(40))
        assertTrue(probe.normalization < 1f)
        probe.originMs = 5000; probe.flush(); probe.queueInput(audio())
        assertEquals(0f, probe.pulseAt(20))
        assertTrue(probe.pulseAt(5020) > 0f)
        probe.reset()
    }
}
