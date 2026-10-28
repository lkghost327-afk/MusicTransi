package com.example.musictransi

import com.example.musictransi.library.ListeningProgress
import com.example.musictransi.source.Track
import com.example.musictransi.ui.PersonalMixes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PersonalMixesTest {
    @Test fun failedMixRefreshCanRetryWithoutChangingHistory() = runTest {
        val model = PersonalMixes(); val seed = song("seed")
        model.refresh(listOf(seed), emptyList()) { _, _ -> error("Offline") }
        assertTrue(model.failed)
        model.refresh(listOf(seed), emptyList()) { _, _ ->
            (1..24).map { song("new$it", "New $it", "Related") }
        }
        assertFalse(model.failed); assertFalse(model.incomplete)
        assertEquals(3, model.mixes.size)
    }
    private fun song(id: String, title: String = id, artist: String = "Artist") = Track(id, title, artist, 180, music = true)
    @Test fun onlyAudibleElapsedTimeCountsOncePerSession() {
        val p = ListeningProgress()
        p.observe(true, 0)
        for (i in 1..29) assertFalse(p.observe(true, i * 1000L))
        assertTrue(p.observe(true, 30000)); assertFalse(p.observe(true, 31000))
        p.reset(); p.observe(false, 0); assertFalse(p.observe(true, 60000))
        for (i in 1..29) assertFalse(p.observe(true, 60000 + i * 1000L))
        assertTrue(p.observe(true, 90000))
    }

    @Test fun expandsSparsePagesUntilAllPublishedMixesHaveTenDistinctSongs() = runTest {
        val model = PersonalMixes(); val seed = song("seed", "Original")
        var calls = 0
        val loader: suspend (Track, List<Track>) -> List<Track> = { _, _ ->
            calls++; (1..3).map { song("track-${calls * 3 + it}", "Song ${calls * 3 + it}", "Artist $calls") } +
                listOf(song("duplicate", "Original (Lyrics)"), song("trailer", "Movie trailer"))
        }
        model.refresh(listOf(seed), listOf(seed), load = loader)
        assertTrue(calls in 4..6)
        assertEquals(setOf("Your rotation", "Discover", "Fresh finds"), model.mixes.map { it.title }.toSet())
        model.mixes.forEach { mix -> assertTrue(mix.tracks.size >= 10); assertEquals(mix.tracks.size, mix.tracks.map { it.id }.distinct().size); assertFalse(mix.tracks.any { it.id in listOf("duplicate", "trailer") }) }
        val before = calls; model.refresh(listOf(seed), listOf(seed), load = loader); assertEquals(before, calls)
    }
    @Test fun insufficientResultsAreExplicitAndCancellationCanRetry() = runTest {
        val model = PersonalMixes(); val seed = song("seed")
        try { model.refresh(listOf(seed), emptyList()) { _, _ -> throw CancellationException() }; fail() } catch (_: CancellationException) { }
        assertFalse(model.loading)
        model.refresh(listOf(seed), emptyList()) { _, _ -> emptyList() }
        assertTrue(model.incomplete); assertTrue(model.mixes.isEmpty())
        val locals = (1..12).map { song("local$it").copy(local = true) }
        model.refresh(locals, emptyList()) { _, _ -> fail("Local history triggered network"); emptyList() }
        assertTrue(model.mixes.all { it.tracks.size >= 10 })
    }
}
