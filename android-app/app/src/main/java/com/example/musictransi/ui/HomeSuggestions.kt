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
    suspend fun refresh(seed: Track, excluded: List<Track>, force: Boolean,
        load: suspend (Track, List<Track>) -> List<Track> = YouTubeSource::recommendations) {
        if (!force && completedSeed == seed.id) return
        if (displayedSeed != seed.id) { tracks = emptyList(); displayedSeed = seed.id }
        loading = true; error = false
        try {
            tracks = load(seed, excluded).distinctBy { it.id }.take(8)
            completedSeed = seed.id
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true; completedSeed = seed.id }
        finally { loading = false }
    }
}
