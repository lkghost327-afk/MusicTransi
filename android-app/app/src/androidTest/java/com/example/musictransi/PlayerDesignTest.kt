package com.example.musictransi

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.musictransi.playback.MixState
import com.example.musictransi.source.Track
import com.example.musictransi.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerDesignTest {
    @get:Rule val compose = createComposeRule()
    @Test fun queueKeepsTransportAndSeekAvailableAfterScrolling() {
        var state by mutableStateOf(MixState(queue = (0..30).map { Track("local-$it", "Song $it", "Artist", local = true) }, index = 0, playing = true, durationMs = 120000))
        var next = 0; var previous = 0; var edit = false
        compose.setContent { MaterialTheme {
            PlayerQueueDialog(state, {}, { state = state.copy(playing = !state.playing) }, { previous++ }, { next++ },
                { state = state.copy(positionMs = it) }, { state = state.copy(index = it) }, { state = state.copy(autoplay = it) }, {}, { edit = true })
        } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Song 25"))
        compose.onNodeWithText("Song 25").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithContentDescription("Queue song position").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.runOnIdle { assertEquals(60000L, state.positionMs); assertEquals(1, next); assertEquals(1, previous); assertTrue(edit) }
    }
    @Test fun categoryAndNavigationActionsReachTheirTargets() {
        var category = ""
        var selected by mutableIntStateOf(0)
        compose.setContent { MaterialTheme {
            androidx.compose.foundation.layout.Column {
                BrowseCategories { category = it }
                MusicNavigation(selected) { selected = it }
            }
        } }
        compose.onNodeWithText("Tamil").performClick()
        compose.onNodeWithText("Library").performClick()
        compose.runOnIdle { assertEquals("Tamil music", category); assertEquals(3, selected) }
        compose.onNodeWithText("Library").assertIsSelected()
    }
    @Test fun suggestedMixActionsRemainAvailableWhileBrowsing() {
        var played = 0; var queued = 0; var saved = 0
        val mix = SuggestedMix("Your rotation", "From recent listening", (0..30).map { Track("local-$it", "Song $it", "Artist", local = true) })
        compose.setContent { MaterialTheme {
            SuggestedMixDialog(mix, {}, { played++ }, { queued++ }, { saved++ })
        } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Song 25"))
        compose.onNodeWithText("Play mix").assertIsDisplayed().performClick()
        compose.onNodeWithText("Queue").assertIsDisplayed().performClick()
        compose.onNodeWithText("Save as playlist").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, played); assertEquals(1, queued); assertEquals(1, saved) }
    }

}
