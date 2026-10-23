package com.example.musictransi.source

object ArtworkPolicy {
    fun sampleSize(width: Int, height: Int, limit: Int = 800): Int {
        require(limit > 0)
        var sample = 1
        while (width / sample > limit || height / sample > limit) sample *= 2
        return sample
    }
    fun videoId(url: String): String? = Regex("(?:[?&]v=|youtu\\.be/)([A-Za-z0-9_-]{11})(?:[^A-Za-z0-9_-]|$)")
        .find(url)?.groupValues?.get(1)
    fun candidates(artwork: String, id: String): List<String> = listOf(
        if (artwork.startsWith("//")) "https:$artwork" else artwork,
        videoId(id)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty()
    ).filter { it.startsWith("https://") }.distinct()
}
