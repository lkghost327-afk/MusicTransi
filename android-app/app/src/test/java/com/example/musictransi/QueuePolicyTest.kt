package com.example.musictransi

import com.example.musictransi.source.*
import org.junit.Assert.*
import org.junit.Test

class QueuePolicyTest {
    @Test fun reorderProtectsCurrentAndAudibleIncomingSongs() {
        val items = listOf(track("current"), track("incoming"), track("later", true), track("last"))
        assertEquals(items, QueuePolicy.move(items, 0, 2, 1, true))
        assertEquals(items, QueuePolicy.move(items, 0, 0, 2, false))
        val moved = QueuePolicy.move(items, 0, 2, 1, false)
        assertEquals(listOf("current", "later", "incoming", "last"), moved.map { it.id })
        assertFalse(moved[1].automatic)
        assertEquals(2, QueuePolicy.nextSlot(0, true))
        assertEquals(1, QueuePolicy.nextSlot(0, false))
    }
    private fun track(id: String, auto: Boolean = false) = Track(id, id, "Artist", 200, automatic = auto)
    @Test fun manualChoicePrecedesAutomaticPicks() {
        val result = QueuePolicy.insert(listOf(track("playing"), track("manual"), track("radio", true)),
            0, track("added"), false, false)
        assertEquals(listOf("playing", "manual", "added", "radio"), result.queue.map { it.id })
    }
    @Test fun playNowDropsOldRecommendationsButPreservesManualNext() {
        val result = QueuePolicy.insert(listOf(track("playing"), track("manual"), track("radio", true)),
            0, track("chosen"), true, false)
        assertEquals(listOf("playing", "chosen", "manual"), result.queue.map { it.id })
        assertEquals(1, result.index)
    }
    @Test fun neverDisplacesAnAudibleIncomingDeck() {
        val result = QueuePolicy.insert(listOf(track("playing"), track("blending", true), track("later", true)),
            0, track("chosen"), false, true)
        assertEquals(listOf("playing", "blending", "chosen", "later"), result.queue.map { it.id })
    }
}
