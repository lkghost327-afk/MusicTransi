package com.example.musictransi.source

import kotlinx.coroutines.CancellationException

/** Surface budgets are independent: a small playback buffer must not limit Home. */
enum class RecommendationSurface(val limit: Int) { AUTOPLAY(3), HOME(8), MIX(20) }

internal suspend fun recommendationBatch(
    seed: Track,
    excluded: List<Track>,
    surface: RecommendationSurface,
    radio: suspend (Track) -> List<Track>,
    search: suspend (String) -> List<Track>
): List<Track> {
    if (seed.local) return emptyList()
    var radioFailure: Exception? = null
    val related = try { radio(seed) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { radioFailure = e; emptyList() }
    val selected = RadioPolicy.select(related, excluded + seed, surface.limit)
    if (selected.size >= surface.limit) return selected
    val artist = seed.artist.replace(Regex("(?i)\\s*-\\s*topic$"), "").trim()
    if (artist.isBlank()) {
        if (radioFailure != null) throw radioFailure
        return selected
    }
    val fallback = try { search(artist).filter { RadioPolicy.artistMatches(it.artist, artist) } }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        // Keep usable partial results, but surface a complete outage for retry.
        if (selected.isEmpty()) throw e
        emptyList()
    }
    return RadioPolicy.select(selected + fallback, excluded + seed, surface.limit)
}
