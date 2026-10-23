package com.example.musictransi

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.musictransi.source.Track
import com.example.musictransi.ui.TrackArtworkTransition
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArtworkMotionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nextExitsLeftAndPreviousExitsRightThenSettles() {
        var index by mutableIntStateOf(0)
        val tracks = listOf(Track("first", "First", "Artist"), Track("second", "Second", "Artist"))
        compose.setContent {
            TrackArtworkTransition(tracks[index], index) { t ->
                Box(Modifier.size(180.dp).testTag(t.id)) { Text(t.title) }
            }
        }
        compose.waitForIdle()
        val origin = compose.onNodeWithTag("first").fetchSemanticsNode().positionInRoot.x
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { index = 1 }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(180)
        assertTrue(compose.onNodeWithTag("first").fetchSemanticsNode().positionInRoot.x < origin)
        assertTrue(compose.onNodeWithTag("second").fetchSemanticsNode().positionInRoot.x > origin)
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("first").assertDoesNotExist()
        compose.runOnIdle { index = 0 }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(180)
        assertTrue(compose.onNodeWithTag("second").fetchSemanticsNode().positionInRoot.x > origin)
        assertTrue(compose.onNodeWithTag("first").fetchSemanticsNode().positionInRoot.x < origin)
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("second").assertDoesNotExist()
        compose.onNodeWithTag("first").assertIsDisplayed()
    }
}
