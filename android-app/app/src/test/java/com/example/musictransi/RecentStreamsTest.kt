package com.example.musictransi

import com.example.musictransi.source.RecentStreams
import com.example.musictransi.source.ResolvedAudio
import org.junit.Assert.*
import org.junit.Test

class RecentStreamsTest {
    @Test fun expiresAndInvalidatesSignedUrls() {
        var clock = 0L
        val cache = RecentStreams { clock }
        val source = ResolvedAudio("https://example.org/signed")
        cache.put("song", source)
        clock = 119999; assertEquals(source, cache.get("song"))
        clock = 120000; assertNull(cache.get("song"))
        cache.put("song", source); cache.invalidate("song"); assertNull(cache.get("song"))
    }
    @Test fun boundsMemoryAndRejectsClockReversal() {
        var clock = 100L
        val cache = RecentStreams { clock }
        repeat(9) { cache.put("$it", ResolvedAudio("https://example.org/$it")) }
        assertNull(cache.get("0")); assertNotNull(cache.get("8"))
        clock = 99; assertNull(cache.get("8"))
    }
}
