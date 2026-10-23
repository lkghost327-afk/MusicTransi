package com.example.musictransi
import com.example.musictransi.playback.PulseDetector
import org.junit.Assert.*
import org.junit.Test
class PulseDetectorTest {
    @Test fun silenceAndSteadyToneDoNotBecomeAMetronome() {
        val d = PulseDetector()
        repeat(100) { assertEquals(0f, d.observe(0.0, it * 20L)) }
        var hits = 0
        repeat(100) { if (d.observe(.15, 2000 + it * 20L) > 0) hits++ }
        assertTrue(hits <= 1)
    }
    @Test fun separatedAudioAttacksProducePulsesButRapidRepeatsAreLimited() {
        val d = PulseDetector()
        assertTrue(d.observe(.3, 1000) > 0)
        d.observe(.001, 1020)
        assertEquals(0f, d.observe(.4, 1040))
        d.observe(.001, 1200)
        assertTrue(d.observe(.4, 1400) > 0)
    }

    @Test fun hapticDetectorKeepsFastAttacksWithoutChangingBeatDetector() {
        val haptic = PulseDetector(90, 1.35); val beat = PulseDetector()
        assertTrue(haptic.observe(.3, 1000) > 0); assertTrue(beat.observe(.3, 1000) > 0)
        haptic.observe(.001, 1020); beat.observe(.001, 1020)
        assertTrue(haptic.observe(.3, 1120) > 0)
        assertEquals(0f, beat.observe(.3, 1120))
        haptic.observe(.001, 1140); assertEquals(0f, haptic.observe(.3, 1160))
    }
}
