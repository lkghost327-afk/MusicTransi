package com.example.musictransi

import com.example.musictransi.library.AudibleClock
import com.example.musictransi.lyrics.*
import org.junit.Assert.*
import org.junit.Test

class ActivityWordTest {
    @Test fun audibleTimeExcludesPausesTrackChangesAndDowntime() {
        val clock = AudibleClock()
        assertEquals(0L, clock.sample("a", true, 1000))
        assertEquals(500L, clock.sample("a", true, 1500))
        assertEquals(0L, clock.sample("a", false, 2000))
        assertEquals(0L, clock.sample("a", true, 9000))
        assertEquals(40L, clock.sample("a", true, 9040))
        assertEquals(0L, clock.sample("b", true, 9080))
        assertEquals(1000L, clock.sample("b", true, 60000))
        assertEquals(0L, clock.sample(null, true, 60040))
    }
    @Test fun enhancedLyricsPreserveWordsAndApplyOffsets() {
        val line = LrcParser.parse("[offset:-100]\n[00:01.00]<00:01.00>Hello <00:01.50>world<00:02.00>").single()
        assertEquals("Hello world", line.text)
        assertEquals(listOf(900L, 1400L), line.words.map { it.timeMs })
        assertEquals(0, WordFollow.highlightedEnd(line, 899, 2000, false))
        assertEquals(6, WordFollow.highlightedEnd(line, 1000, 2000, false))
        assertEquals(11, WordFollow.highlightedEnd(line, 1500, 2000, false))
    }
    @Test fun estimatesAreOptionalAndSeekBackwardsWorks() {
        val line = LyricLine(1000, "one two three")
        assertEquals(0, WordFollow.highlightedEnd(line, 2000, 4000, false))
        assertEquals(7, WordFollow.highlightedEnd(line, 2000, 4000, true))
        assertEquals(3, WordFollow.highlightedEnd(line, 1100, 4000, true))
        assertEquals(0, WordFollow.highlightedEnd(line, 900, 4000, true))
        assertEquals(0, WordFollow.highlightedEnd(line, 1000, 1000, true))
    }
}
