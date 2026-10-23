package com.example.musictransi.ui

import androidx.compose.runtime.*
import com.example.musictransi.source.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

data class SuggestedMix(val title: String, val description: String, val tracks: List<Track>)

@Stable class PersonalMixes {
    var mixes by mutableStateOf<List<SuggestedMix>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var incomplete by mutableStateOf(false); private set
    private var completed: List<Track>? = null
    suspend fun refresh(history: List<Track>, likes: List<Track>, force: Boolean = false,
        load: suspend (Track, List<Track>) -> List<Track> = YouTubeSource::mixRecommendations) {
        val basis = (history.take(30) + likes.take(30)).distinctBy { it.id }
        if (!force && completed == basis) return
        completed = null
        fun unique(items: List<Track>, excluded: List<Track> = emptyList(), limit: Int = 20): List<Track> {
            val result = mutableListOf<Track>()
            items.filter { it.local || RadioPolicy.eligible(it) }.forEach { t ->
                if (result.size < limit && (result + excluded).none { RadioPolicy.sameRecording(it, t) }) result += t.copy(automatic = false)
            }
            return result
        }
        val known = unique(basis)
        fun publish(candidates: List<Track>) {
            val rotation = unique(known + candidates)
            val ranked = RadioPolicy.personalized(candidates, history, likes)
            val discover = unique(RadioPolicy.select(ranked, emptyList(), 20) + known)
            val fresh = unique(candidates.sortedBy { t -> known.any { RadioPolicy.artistMatches(it.artist, t.artist) } }, basis)
            mixes = buildList {
                if (rotation.size >= 10) add(SuggestedMix("Your rotation", "Your favourites, filled out with related music", rotation))
                if (discover.size >= 10) add(SuggestedMix("Discover", "More from artists you love and similar artists", discover))
                if (fresh.size >= 10) add(SuggestedMix("Fresh finds", "New to your recent listening", fresh))
            }
            incomplete = basis.isNotEmpty() && (rotation.size < 10 || discover.size < 10 || fresh.size < 10)
        }
        publish(emptyList()); failed = false; loading = true
        try {
            val seeds = known.filter { !it.local }.distinctBy { it.artist.lowercase() }.toMutableList()
            val tried = mutableSetOf<String>()
            val candidates = mutableListOf<Track>()
            val finished = withTimeoutOrNull(60000) {
                repeat(6) {
                    val seed = seeds.firstOrNull { it.id !in tried } ?: return@withTimeoutOrNull true
                    tried += seed.id
                    try {
                        val batch = load(seed, basis + candidates)
                        candidates += RadioPolicy.select(batch, basis + candidates, 30)
                        publish(candidates)
                        if (!incomplete && candidates.size >= 20) return@withTimeoutOrNull true
                        // Expand sparse radio pages using discovered recordings, rather than repeating the same request.
                        seeds += batch.filter { RadioPolicy.eligible(it) && it.id !in tried && seeds.none { old -> old.id == it.id } }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failed = true }
                }
                true
            }
            if (finished == null) failed = true
            publish(candidates)
            completed = basis
        } finally { loading = false }
    }
}
