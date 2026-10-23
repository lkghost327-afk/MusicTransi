package com.example.musictransi

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.musictransi.lyrics.*
import com.example.musictransi.source.Track
import com.example.musictransi.ui.LyricsDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun transportsAndSeekStayVisibleWhileLyricsScroll() {
        var playing by mutableStateOf(true)
        var position by mutableLongStateOf(0L)
        var next = 0; var previous = 0
        compose.setContent { MaterialTheme {
            LyricsDialog(Track("fixture", "Fixture", "Test"), position, 120000, playing,
                { position = it }, { playing = !playing }, { previous++ }, { next++ }, {},
                load = { _, _ -> Lyrics((0..30).map { LyricLine(it * 3000L, "Synthetic line $it") }) })
        } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("lyric-0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithTag("lyrics-seek").assertIsDisplayed().performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        compose.runOnIdle { assertEquals(60000L, position); assertFalse(playing) }
        compose.waitForIdle()
        compose.onNodeWithTag("lyric-20").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.runOnIdle { assertEquals(1, next); assertEquals(1, previous) }
    }
    @Test fun missingLyricsStillAllowPlaybackAndSeeking() {
        compose.setContent { MaterialTheme {
            LyricsDialog(Track("local", "Local", "Test", local = true), 0, 90000, false,
                {}, {}, {}, {}, {}, load = { _, _ -> Lyrics() })
        } }
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithTag("lyrics-seek").assertIsDisplayed().assertIsEnabled()
    }
    @Test fun highlightingKeepsWrappedLineHeightStable() {
        var position by mutableLongStateOf(0L)
        compose.setContent { MaterialTheme {
            LyricsDialog(Track("fixture", "Fixture", "Test"), position, 90000, true,
                {}, {}, {}, {}, {}, load = { _, _ -> Lyrics(listOf(
                    LyricLine(0, "A deliberately long synthetic line that wraps across the narrow lyrics viewport"),
                    LyricLine(3000, "Second line"))) })
        } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("lyric-0").fetchSemanticsNodes().isNotEmpty() }
        val before = compose.onNodeWithTag("lyric-0").fetchSemanticsNode().size.height
        compose.runOnIdle { position = 3000 }
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("lyric-0").fetchSemanticsNode().size.height)
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test fun selectingLinesDoesNotSeekAndAllowsPreview() {
        var seeks = 0
        compose.setContent { MaterialTheme {
            LyricsDialog(Track("fixture", "Synthetic song", "Test"), 0, 90000, false,
                { seeks++ }, {}, {}, {}, {}, load = { _, _ -> Lyrics(listOf(LyricLine(0, "Synthetic line one"), LyricLine(3000, "Synthetic line two"))) })
        } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("lyric-0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share lines").performClick()
        compose.onNodeWithTag("lyric-0").performClick()
        compose.runOnIdle { assertEquals(0, seeks) }
        compose.onNodeWithText("Preview").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Share card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share card").assertIsDisplayed()
    }
}
