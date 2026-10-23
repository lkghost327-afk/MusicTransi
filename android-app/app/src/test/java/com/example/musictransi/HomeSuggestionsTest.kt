package com.example.musictransi

import com.example.musictransi.source.Track
import com.example.musictransi.ui.HomeSuggestions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HomeSuggestionsTest {
    @Test fun cachesUntilSeedChangesOrUserRefreshesAndBoundsResults() = runTest {
        val model = HomeSuggestions()
        val seed = Track("seed", "Seed", "Artist")
        var calls = 0
        val loader: suspend (Track, List<Track>) -> List<Track> = { _, _ ->
            calls++; (0..20).map { Track("$it", "Song $it", "Artist") }
        }
        model.refresh(seed, emptyList(), false, loader)
        model.refresh(seed, emptyList(), false, loader)
        assertEquals(1, calls); assertEquals(8, model.tracks.size)
        model.refresh(seed, emptyList(), true, loader)
        model.refresh(seed.copy(id = "other"), emptyList(), false, loader)
        assertEquals(3, calls); assertFalse(model.loading)
    }
    @Test fun cancellationAllowsRetryAndFailuresClearLoading() = runTest {
        val model = HomeSuggestions(); val seed = Track("seed", "Seed", "Artist")
        try { model.refresh(seed, emptyList(), false) { _, _ -> throw CancellationException() } }
        catch (_: CancellationException) { }
        assertFalse(model.loading)
        model.refresh(seed, emptyList(), false) { _, _ -> throw IllegalStateException() }
        assertTrue(model.error); assertFalse(model.loading)
        model.refresh(seed, emptyList(), true) { _, _ -> listOf(seed, seed) }
        assertFalse(model.error); assertEquals(1, model.tracks.size)
    }
}
