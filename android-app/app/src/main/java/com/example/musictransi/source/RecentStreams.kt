package com.example.musictransi.source

/** Memory-only: signed stream URLs expire and must never enter the persistent catalogue. */
internal class RecentStreams(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val entries = linkedMapOf<String, Pair<Long, ResolvedAudio>>()
    @Synchronized fun get(id: String): ResolvedAudio? {
        val entry = entries[id] ?: return null
        if (now() - entry.first !in 0 until 120_000) { entries.remove(id); return null }
        return entry.second
    }
    @Synchronized fun put(id: String, audio: ResolvedAudio) {
        entries.remove(id)
        entries[id] = now() to audio
        while (entries.size > 8) entries.remove(entries.keys.first())
    }
    @Synchronized fun invalidate(id: String) { entries.remove(id) }
}
