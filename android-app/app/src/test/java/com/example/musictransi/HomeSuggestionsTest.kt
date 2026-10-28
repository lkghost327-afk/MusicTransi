package com.example.musictransi

import com.example.musictransi.source.Track
import com.example.musictransi.ui.HomeSuggestions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HomeSuggestionsTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun returningToAnEarlierSeedAfterCancelledNavigationReloadsItsShelf() = runTest {
        val model = HomeSuggestions(); val first = Track("a", "First", "Artist")
        model.refresh(first, emptyList(), false) { _, _ -> listOf(first) }
        val pending = CompletableDeferred<List<Track>>()
        val next = launch { model.refresh(first.copy(id = "b"), emptyList(), false) { _, _ -> pending.await() } }
        runCurrent(); next.cancel(); next.join()
        model.refresh(first, emptyList(), false) { _, _ -> listOf(first) }
        assertEquals(listOf(first), model.tracks)
    }
    @Test fun aFailedSeedCanRetryWithoutForce() = runTest {
        val model = HomeSuggestions(); val seed = Track("seed", "Seed", "Artist")
        model.refresh(seed, emptyList(), false) { _, _ -> error("Offline") }
        model.refresh(seed, emptyList(), false) { _, _ -> listOf(seed) }
        assertFalse(model.error); assertEquals(listOf(seed), model.tracks)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun lateResultsCannotReplaceANewerSeedAndIdenticalRequestsCoalesce() = runTest {
        val model = HomeSuggestions(); val first = Track("a", "First", "Artist")
        val second = first.copy(id = "b")
        val pending = CompletableDeferred<List<Track>>()
        var calls = 0
        val old = launch { model.refresh(first, emptyList(), false) { _, _ -> calls++; pending.await() } }
        runCurrent()
        model.refresh(first, emptyList(), false) { _, _ -> calls++; emptyList() }
        assertEquals(1, calls)
        model.refresh(second, emptyList(), false) { _, _ -> listOf(second) }
        pending.complete(listOf(first)); old.join()
        assertEquals(listOf(second), model.tracks); assertFalse(model.loading)
    }
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
