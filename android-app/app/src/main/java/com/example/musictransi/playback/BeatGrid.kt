package com.example.musictransi.playback

import kotlin.math.*

/** Small onset-based tempo estimator. No model, FFT buffer or song download is retained. */
class BeatGrid {
    data class Profile(val bpm: Double, val anchorMs: Double, val confidence: Double) {
        val periodMs get() = 60000.0 / bpm
        fun nextBeat(position: Long): Long = (anchorMs + ceil((position - anchorMs) / periodMs) * periodMs).roundToLong()
    }
    private val events = ArrayDeque<Long>()
    private var cachedEvents: List<Long> = emptyList()
    private var cached: Profile? = null
    fun clear() { events.clear(); cachedEvents = emptyList(); cached = null }
    fun add(time: Long) {
        if (events.isNotEmpty() && time <= events.last()) return
        events.addLast(time)
        while (events.size > 96 || (events.isNotEmpty() && time - events.first() > 24000)) events.removeFirst()
    }
    fun estimate(positionMs: Long? = null): Profile? {
        // Decoders run ahead of the speakers. Only heard onsets may anchor the outgoing grid.
        val heard = events.filter { positionMs == null || it <= positionMs }
        if (heard.size < 10 || heard.last() - heard.first() < 6000) return null
        if (positionMs != null && positionMs - heard.last() > 3000) return null
        if (heard == cachedEvents) return cached
        cachedEvents = heard
        var best: Profile? = null
        // Circular phase concentration rejects irregular onsets; occupancy rejects sparse coincidences.
        for (step in 140..340) {
            val bpm = step / 2.0
            val period = 60000.0 / bpm
            val base = heard.first().toDouble()
            var x = 0.0; var y = 0.0
            heard.forEach { val phase = 2 * PI * (it - base) / period; x += cos(phase); y += sin(phase) }
            val concentration = hypot(x, y) / heard.size
            val occupancy = (heard.size * period / (heard.last() - base + period)).coerceAtMost(1.0)
            val confidence = concentration * occupancy
            if (confidence > (best?.confidence ?: 0.0)) {
                val phase = (atan2(y, x) + 2 * PI) % (2 * PI)
                best = Profile(bpm, base + phase * period / (2 * PI), confidence)
            }
        }
        cached = best?.takeIf { it.confidence >= .72 }
        return cached
    }
    companion object {
        fun speed(outgoing: Profile?, incoming: Profile?): Float? {
            if (outgoing == null || incoming == null) return null
            if (outgoing.confidence < .88 || incoming.confidence < .88) return null
            val ratio = outgoing.bpm / incoming.bpm
            return ratio.takeIf { it in .97..1.03 }?.toFloat()
        }
    }
}
