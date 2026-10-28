package com.example.musictransi.source

enum class AudioQuality(val label: String, val ceilingKbps: Int) {
    LOW("Low", 48), MEDIUM("Medium", 128), HIGH("High", 256), VERY_HIGH("Very high", Int.MAX_VALUE);

    /** Prefer the best offered stream within the target; otherwise the smallest above it.
     * Unknown bitrates stay last. No resampling or fabricated bitrate tiers. */
    fun <T> rank(streams: List<T>, bitrate: (T) -> Int): List<T> = streams.sortedWith(
        compareBy<T> { if (bitrate(it) <= 0) 2 else if (bitrate(it) <= ceilingKbps) 0 else 1 }
            .thenBy { if (bitrate(it) in 1..ceilingKbps) -bitrate(it) else bitrate(it) })

    companion object {
        fun fromPreference(value: String?) = entries.firstOrNull { it.name == value } ?: HIGH
    }
}
