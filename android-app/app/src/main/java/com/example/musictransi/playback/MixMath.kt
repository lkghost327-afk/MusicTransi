package com.example.musictransi.playback

import kotlin.math.cos
import kotlin.math.sin

object MixMath {
    /** Equal-power gain curves, with a little output headroom for overlapping peaks. */
    fun gains(progress: Float): Pair<Float, Float> {
        val angle = progress.coerceIn(0f, 1f) * Math.PI / 2
        return (cos(angle).toFloat() * HEADROOM) to (sin(angle).toFloat() * HEADROOM)
    }
    const val HEADROOM = 0.70f
    fun duration(requestedMs: Long, outgoingMs: Long, incomingMs: Long): Long =
        minOf(requestedMs, outgoingMs.coerceAtLeast(0) / 3, incomingMs.coerceAtLeast(0) / 3)
}
