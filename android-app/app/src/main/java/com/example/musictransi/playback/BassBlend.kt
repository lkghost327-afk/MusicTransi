package com.example.musictransi.playback

import android.media.audiofx.Equalizer

/** Per-deck bass attenuation; unsupported devices retain the ordinary crossfade. */
class BassBlend {
    private var effect: Equalizer? = null
    private var session = 0
    fun attach(id: Int) {
        if (id == session) return
        release()
        if (id <= 0) return
        session = id
        try {
            effect = Equalizer(0, id)
            effect!!.enabled = true
        } catch (_: Exception) { release() }
    }
    fun gain(db: Float) {
        val eq = effect ?: return
        runCatching {
            val range = eq.bandLevelRange
            for (i in 0 until eq.numberOfBands) {
                val band = i.toShort()
                val hz = eq.getCenterFreq(band) / 1000f
                val weight = if (hz <= 180) 1f else if (hz <= 400) 0.4f else 0f
                eq.setBandLevel(band, (db * 100 * weight).toInt()
                    .coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
        }.onFailure { release() }
    }
    fun release() { runCatching { effect?.release() }; effect = null; session = 0 }
}
