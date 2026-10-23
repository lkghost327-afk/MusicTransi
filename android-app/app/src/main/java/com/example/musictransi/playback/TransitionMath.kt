package com.example.musictransi.playback

object TransitionMath {
    fun phaseError(outgoing: BeatGrid.Profile, incoming: BeatGrid.Profile, outMs: Long, inMs: Long): Double {
        val cycles = (inMs - incoming.anchorMs) / incoming.periodMs - (outMs - outgoing.anchorMs) / outgoing.periodMs
        return (cycles - kotlin.math.round(cycles)) * outgoing.periodMs
    }
    fun correctedSpeed(base: Float, errorMs: Double): Float =
        (base * (1.0 - (errorMs / 2000.0).coerceIn(-.012, .012))).toFloat().coerceIn(.97f, 1.03f)
    fun progress(position: Long, start: Long, duration: Long): Float =
        ((position - start).toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
    /** A shallow, complementary bass handoff avoids the previous mid-transition hole. */
    fun bass(progress: Float): Pair<Float, Float> {
        val p = progress.coerceIn(0f, 1f)
        val smooth = p * p * (3f - 2f * p)
        return -6f * smooth to -6f * (1f - smooth)
    }
}
