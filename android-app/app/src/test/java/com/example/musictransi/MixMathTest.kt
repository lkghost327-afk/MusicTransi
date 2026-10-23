package com.example.musictransi

import com.example.musictransi.playback.MixMath
import org.junit.Assert.*
import org.junit.Test

class MixMathTest {
    @Test fun constantPowerAcrossOverlap() {
        for (i in 0..100) {
            val (a, b) = MixMath.gains(i / 100f)
            assertEquals(MixMath.HEADROOM * MixMath.HEADROOM, a * a + b * b, 0.0001f)
        }
        assertEquals(0f, MixMath.gains(0f).second, 0.0001f)
        assertEquals(0f, MixMath.gains(1f).first, 0.0001f)
    }
    @Test fun shortOrUnknownTracksCannotBeSwallowedByTransition() {
        assertEquals(2000L, MixMath.duration(8000, 6000, 180000))
        assertEquals(0L, MixMath.duration(8000, 180000, -1))
        assertEquals(8000L, MixMath.duration(8000, 180000, 180000))
    }
}
