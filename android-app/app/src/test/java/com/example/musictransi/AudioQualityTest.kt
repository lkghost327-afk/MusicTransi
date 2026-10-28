package com.example.musictransi

import com.example.musictransi.source.AudioQuality
import org.junit.Assert.*
import org.junit.Test

class AudioQualityTest {
    @Test fun ranksOfferedStreamsPerTierWithUnknownLast() {
        val offered = listOf(256, 64, 128, -1, 320, 48)
        assertEquals(48, AudioQuality.LOW.rank(offered) { it }.first())
        assertEquals(128, AudioQuality.MEDIUM.rank(offered) { it }.first())
        assertEquals(256, AudioQuality.HIGH.rank(offered) { it }.first())
        assertEquals(320, AudioQuality.VERY_HIGH.rank(offered) { it }.first())
        AudioQuality.entries.forEach { assertEquals(-1, it.rank(offered) { value -> value }.last()) }
    }
    @Test fun fallsBackToAvailableBitratesWithoutInventingQuality() {
        assertEquals(listOf(64, 128), AudioQuality.LOW.rank(listOf(128, 64)) { it })
        assertEquals(listOf(128, 64), AudioQuality.VERY_HIGH.rank(listOf(64, 128)) { it })
        assertEquals(AudioQuality.HIGH, AudioQuality.fromPreference("LOSSLESS"))
        assertEquals(AudioQuality.LOW, AudioQuality.fromPreference("LOW"))
    }
}
