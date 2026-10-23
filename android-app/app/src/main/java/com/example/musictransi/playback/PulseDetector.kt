package com.example.musictransi.playback

import kotlin.math.sqrt

/** Detect amplitude onsets, not a fixed BPM timer. Silence never produces pulses. */
class PulseDetector(private val minimumGapMs: Long = 160, private val thresholdRatio: Double = 1.55) {
    private var average = 0.0
    private var previous = 0.0
    private var lastPulse = -1000L
    fun observe(rms: Double, timeMs: Long): Float {
        val threshold = maxOf(.018, average * thresholdRatio)
        val hit = rms > threshold && rms > previous * 1.15 && timeMs - lastPulse >= minimumGapMs
        average = average * .94 + rms * .06
        previous = rms
        if (!hit) return 0f
        lastPulse = timeMs
        return (sqrt(rms) * 2).toFloat().coerceIn(.15f, 1f)
    }
}
