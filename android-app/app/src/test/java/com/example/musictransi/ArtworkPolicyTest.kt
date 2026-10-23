package com.example.musictransi
import com.example.musictransi.source.ArtworkPolicy
import org.junit.Assert.*
import org.junit.Test
class ArtworkPolicyTest {
    @Test fun sampleFactorNeverStartsAtZero() {
        assertEquals(1, ArtworkPolicy.sampleSize(300, 300))
        assertEquals(4, ArtworkPolicy.sampleSize(2400, 1600))
        assertEquals(1, ArtworkPolicy.sampleSize(-1, -1))
    }
    @Test fun oldQueuesAndProtocolRelativeCoversGetFallbacks() {
        val id = "https://www.youtube.com/watch?v=abcdefghijk"
        assertEquals(listOf("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"), ArtworkPolicy.candidates("", id))
        assertEquals("https://example.org/cover.jpg", ArtworkPolicy.candidates("//example.org/cover.jpg", id).first())
        assertTrue(ArtworkPolicy.candidates("", "content://local/audio").isEmpty())
    }
}
