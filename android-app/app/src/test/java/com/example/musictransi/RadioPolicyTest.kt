package com.example.musictransi

import com.example.musictransi.source.RadioPolicy
import com.example.musictransi.source.Track
import org.junit.Assert.*
import org.junit.Test

class RadioPolicyTest {
    @Test fun ranksListeningAndLikesWithoutRemovingRelatedArtists() {
        val random = Track("a", "A song", "New artist", 200, music = true)
        val liked = random.copy(id = "b", title = "B song", artist = "Favourite")
        val ranked = RadioPolicy.personalized(listOf(random, liked), emptyList(), listOf(liked.copy(id = "old")))
        assertEquals(listOf("b", "a"), ranked.map { it.id })
    }
    @Test fun rejectsCompactEditsAndChannelLevelVariants() {
        for (suffix in listOf("BassBoosted", "SpedUp", "TikTok edit", "Fan edit", "Live at Wembley")) {
            assertFalse(RadioPolicy.eligible(Track("a", "Song ($suffix)", "Artist", 200, music = true)))
        }
        assertFalse(RadioPolicy.eligible(Track("a", "Song", "BassBoosted", 200, music = true)))
    }
    @Test fun sameTitleDifferentArtistsAreNotAutomaticallyTheSameRecording() {
        val a = Track("a", "Hello", "Adele", 295, music = true)
        val b = Track("b", "Hello", "Lionel Richie", 250, music = true)
        assertFalse(RadioPolicy.sameRecording(a, b))
        assertEquals(listOf("b"), RadioPolicy.select(listOf(b), listOf(a)).map { it.id })
        assertTrue(RadioPolicy.sameRecording(a, a.copy(id = "upload", title = "Hello (Lyrics)", artist = "Lyrics channel")))
    }
    @Test fun avoidsThreeSameArtistSongsWhenRadioOffersAnAlternative() {
        val recent = listOf(Track("a", "One", "A", 200), Track("b", "Two", "A", 200))
        val pool = listOf(Track("c", "Three", "A", 200, music = true), Track("d", "Four", "B", 200, music = true))
        assertEquals("d", RadioPolicy.select(pool, recent).first().id)
        assertEquals("c", RadioPolicy.select(pool.take(1), recent).first().id)
    }
    @Test fun excludesPlayedRecordingsAlternateUploadsAndLongMixes() {
        val played = Track("old", "Carefree (Official Audio)", "Artist - Topic", 180)
        val candidates = listOf(played, Track("reupload", "Carefree [Official Video]", "Artist", 182),
            Track("mix", "Hour mix", "Artist", 3600), Track("clip", "Clip", "Artist", 20),
            Track("new", "Another song", "Artist", 220))
        val result = RadioPolicy.select(candidates.map { it.copy(music = true) }, listOf(played))
        assertEquals(listOf("new"), result.map { it.id })
        assertTrue(result.single().automatic)
    }
    @Test fun preservesProviderOrderAndBoundsLookahead() {
        val candidates = (1..8).map { Track("id$it", "Track $it", "Artist", 200, music = true) }
        val result = RadioPolicy.select(candidates + candidates, emptyList())
        assertEquals(listOf("id1", "id2", "id3"), result.map { it.id })
        assertEquals(8, RadioPolicy.select(candidates + candidates, emptyList(), 20).size)
    }
    @Test fun blocksTheReportedLyricVideoAndBassBoostSequenceAcrossUploaders() {
        val seed = Track("seed", "Blinding Lights", "The Weeknd", 200)
        val candidates = listOf(
            Track("lyric", "The Weeknd - Blinding Lights (Lyrics)", "Random Lyrics", 201, music = true),
            Track("official", "Blinding Lights - Official Video", "TheWeekndVEVO", 202, music = true),
            Track("bass", "Blinding Lights (Bass Boosted)", "Bass Channel", 201, music = true),
            Track("trailer", "Film Official Trailer", "Studio", 180, music = true),
            Track("unknown", "Random upload", "Someone", 200),
            Track("new", "Save Your Tears", "The Weeknd", 215, music = true))
        assertEquals(listOf("new"), RadioPolicy.select(candidates, listOf(seed)).map { it.id })
    }
    @Test fun legitimateSongParenthesesAndUnicodeArePreserved() {
        assertNotEquals(RadioPolicy.songKey(Track("a", "Stay (I Missed You)", "A")),
            RadioPolicy.songKey(Track("b", "Stay (Faraway, So Close!)", "B")))
        assertEquals(RadioPolicy.songKey(Track("a", "Déjà Vu", "A")), RadioPolicy.songKey(Track("b", "Deja Vu (Lyrics)", "B")))
        assertTrue(RadioPolicy.artistMatches("The Weeknd - Topic", "The Weeknd"))
        assertFalse(RadioPolicy.artistMatches("Weeknd fan edits", "The Weeknd"))
    }
    @Test fun excludesInstrumentalAndTributeVariantsEvenFromMusicCatalog() {
        val variants = listOf("Instrumental", "Acapella", "A Cappella", "Tribute", "Bootleg")
            .map { Track(it, "Blinding Lights ($it)", "Artist", 200, music = true) }
        assertTrue(RadioPolicy.select(variants, emptyList()).isEmpty())
    }
}
