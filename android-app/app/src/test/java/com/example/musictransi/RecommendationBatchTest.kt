package com.example.musictransi

import com.example.musictransi.source.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RecommendationBatchTest {
    private val seed = Track("seed", "Original", "Artist", 180, music = true)
    private fun songs(count: Int) = (1..count).map { seed.copy(id = "id$it", title = "Song $it") }

    @Test fun eachSurfaceGetsItsOwnBudgetWithoutUnnecessaryFallback() = runTest {
        for ((surface, count) in listOf(RecommendationSurface.AUTOPLAY to 3,
            RecommendationSurface.HOME to 8, RecommendationSurface.MIX to 20)) {
            val result = recommendationBatch(seed, emptyList(), surface, { songs(30) },
                { error("Full radio page must not trigger another request") })
            assertEquals(count, result.size)
        }
    }

    @Test fun oneRadioResultDoesNotStopAutoplayOrHomeFromFilling() = runTest {
        for (surface in listOf(RecommendationSurface.AUTOPLAY, RecommendationSurface.HOME)) {
            val result = recommendationBatch(seed, emptyList(), surface, { songs(1) }, { songs(12) })
            assertEquals(surface.limit, result.size)
            assertEquals(result.size, result.map { it.id }.distinct().size)
        }
    }

    @Test fun sparseResultsNeverGetForbiddenFillerAndLocalSeedsDoNotFetch() = runTest {
        val result = recommendationBatch(seed, songs(1), RecommendationSurface.HOME,
            { songs(2) }, { listOf(seed.copy(id = "trailer", title = "Film Trailer")) })
        assertEquals(listOf("id2"), result.map { it.id })
        assertTrue(recommendationBatch(seed.copy(local = true), emptyList(), RecommendationSurface.HOME,
            { error("Local radio") }, { error("Local search") }).isEmpty())
    }

    @Test fun cancellationIsNeverConvertedIntoFallbackWork() = runTest {
        try {
            recommendationBatch(seed, emptyList(), RecommendationSurface.HOME,
                { throw CancellationException() }, { error("Cancelled radio triggered search") })
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
}
