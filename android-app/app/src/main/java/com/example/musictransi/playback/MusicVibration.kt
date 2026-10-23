package com.example.musictransi.playback

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator

/** Android 8+ fallback. Device policy may suppress media vibrations in the background. */
@Suppress("DEPRECATION")
class MusicVibration(context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    val available get() = vibrator?.hasVibrator() == true
    val amplitudeControl get() = vibrator?.hasAmplitudeControl() == true
    private var lastPulse = 0L
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    fun pulse(strength: Float, intensity: Float, test: Boolean = false): Boolean {
        if (!available || strength <= 0f) return false
        val now = SystemClock.elapsedRealtime()
        if (!test && (now - lastPulse < 70 || strength < .15f)) return false
        lastPulse = now
        return runCatching {
            val amplitude = if (amplitudeControl) (strength * intensity * 230).toInt().coerceIn(30, 230)
                else VibrationEffect.DEFAULT_AMPLITUDE
            val duration = if (test) 180L else (18 + 32 * strength * intensity).toLong().coerceIn(18, 50)
            val effect = if (!test && amplitudeControl) VibrationEffect.createWaveform(
                longArrayOf((duration - 8).coerceAtLeast(10), 8),
                intArrayOf(amplitude, (amplitude * .3f).toInt()), -1)
                else VibrationEffect.createOneShot(duration, amplitude)
            vibrator.vibrate(effect, attributes)
            true
        }.getOrDefault(false)
    }
    fun cancel() { vibrator?.cancel(); lastPulse = 0L }
}
