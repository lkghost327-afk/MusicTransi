package com.example.musictransi

import com.example.musictransi.playback.BeatGrid
import org.junit.Assert.*
import org.junit.Test

class BeatGridTest {
    @Test fun decodedFutureBeatsCannotAnchorTheHeardGrid() {
        val grid = BeatGrid()
        repeat(20) { grid.add(it * 500L) }
        val heard = grid.estimate(9500)!!
        repeat(20) { grid.add(10000L + it * 667L) }
        assertEquals(120.0, grid.estimate(9500)!!.bpm, .01)
        assertEquals(heard.anchorMs, grid.estimate(9500)!!.anchorMs, .01)
        assertNull(grid.estimate(40000))
        grid.clear(); assertNull(grid.estimate())
    }
    @Test fun estimatesTempoWithSmallTimingJitter() {
        for (bpm in listOf(90, 110, 120, 150)) {
            val grid = BeatGrid()
            repeat(32) { grid.add((230 + it * 60000.0 / bpm + (it % 3 - 1) * 8).toLong()) }
            val p = grid.estimate()!!
            assertEquals(bpm.toDouble(), p.bpm, 1.0)
            assertTrue(p.nextBeat(18000) >= 18000)
        }
    }
    @Test fun rejectsSilenceSparseAndIrregularOnsets() {
        assertNull(BeatGrid().estimate())
        val sparse = BeatGrid(); repeat(4) { sparse.add(it * 500L) }; assertNull(sparse.estimate())
        val irregular = BeatGrid(); val random = java.util.Random(42); var t = 0L
        repeat(50) { t += 170 + random.nextInt(600); irregular.add(t) }
        assertNull(irregular.estimate())
    }
    @Test fun boundsTempoStretchAndAlignsBeats() {
        val a = BeatGrid.Profile(120.0, 200.0, .9)
        val b = BeatGrid.Profile(118.0, 300.0, .9)
        assertEquals(120f / 118f, BeatGrid.speed(a, b)!!, .001f)
        assertNull(BeatGrid.speed(a, b.copy(confidence = .8)))
        assertNull(BeatGrid.speed(a, b.copy(bpm = 116.0)))
        assertNull(BeatGrid.speed(a, b.copy(bpm = 90.0)))
        assertNull(BeatGrid.speed(null, b))
        assertEquals(1700L, a.nextBeat(1600))
    }
}
