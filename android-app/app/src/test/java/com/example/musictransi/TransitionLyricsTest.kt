package com.example.musictransi

import com.example.musictransi.lyrics.LrcParser
import com.example.musictransi.playback.TransitionMath
import org.junit.Assert.*
import org.junit.Test

class TransitionLyricsTest {
    @Test fun phaseCorrectionHandlesWrapAndSlowsAnIncomingBeatThatIsAhead() {
        val grid = com.example.musictransi.playback.BeatGrid.Profile(120.0, 0.0, 1.0)
        assertEquals(30.0, TransitionMath.phaseError(grid, grid, 1000, 1030), .01)
        assertEquals(-20.0, TransitionMath.phaseError(grid, grid, 1000, 1480), .01)
        assertTrue(TransitionMath.correctedSpeed(1f, 30.0) < 1f)
        assertTrue(TransitionMath.correctedSpeed(1f, -30.0) > 1f)
    }
    @Test fun masterTimelineFinishesAtTrackEndEvenAfterLateIncomingStart() {
        assertEquals(0f, TransitionMath.progress(92000, 92000, 8000))
        assertEquals(.5f, TransitionMath.progress(96000, 92000, 8000))
        assertEquals(1f, TransitionMath.progress(100000, 92200, 7800))
        assertEquals(-3f to -3f, TransitionMath.bass(.5f))
        assertEquals(0f, TransitionMath.bass(0f).first, .0001f)
        assertEquals(0f, TransitionMath.bass(1f).second, .0001f)
    }
    @Test fun parsesRepeatedTimestampsFractionsAndOffsetWithoutMetadataLines() {
        val lines = LrcParser.parse("[ar:Fixture]\n[offset:-100]\n[00:01.20][00:03.004]Synthetic line\n[00:65]Invalid\n[00:05]Last")
        assertEquals(listOf(1100L, 2904L, 4900L), lines.map { it.timeMs })
        assertEquals("Synthetic line", lines[0].text)
    }
}
