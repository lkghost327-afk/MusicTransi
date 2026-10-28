package com.example.musictransi.ui

import androidx.compose.runtime.*
import com.example.musictransi.source.Track
import com.example.musictransi.source.YouTubeSource
import kotlinx.coroutines.CancellationException

/** One bounded result set per screen; playback ticks never trigger network requests. */
@Stable class HomeSuggestions {
    var tracks by mutableStateOf<List<Track>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf(false); private set
    private var completedSeed: String? = null
    private var displayedSeed: String? = null
    private var generation = 0L
    private var activeSeed: String? = null
    suspend fun refresh(seed: Track, excluded: List<Track>, force: Boolean,
        load: suspend (Track, List<Track>) -> List<Track> = YouTubeSource::homeRecommendations) {
        if (!force && completedSeed == seed.id && displayedSeed == seed.id && !loading) return
        if (!force && loading && activeSeed == seed.id) return
        val request = ++generation
        activeSeed = seed.id
        if (displayedSeed != seed.id) { tracks = emptyList(); displayedSeed = seed.id }
        loading = true; error = false
        try {
            val result = load(seed, excluded).distinctBy { it.id }.take(8)
            if (request != generation) return
            tracks = result
            completedSeed = seed.id
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (request == generation) { error = true; completedSeed = null } }
        finally { if (request == generation) { loading = false; activeSeed = null } }
    }
}
