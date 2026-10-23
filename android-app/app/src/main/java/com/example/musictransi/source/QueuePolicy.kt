package com.example.musictransi.source

object QueuePolicy {
    fun nextSlot(current: Int, blending: Boolean) = current + if (blending) 2 else 1
    fun move(queue: List<Track>, current: Int, from: Int, to: Int, blending: Boolean): List<Track> {
        val first = nextSlot(current, blending).coerceAtLeast(0)
        if (from !in queue.indices || to !in queue.indices || from < first || to < first) return queue
        return queue.toMutableList().apply { add(to, removeAt(from).copy(automatic = false)) }
    }
    data class Placement(val queue: List<Track>, val index: Int)
    fun insert(queue: List<Track>, current: Int, track: Track, playNow: Boolean, blending: Boolean): Placement {
        val items = if (playNow) queue.filterIndexed { i, t -> i <= current || !t.automatic }.toMutableList()
            else queue.toMutableList()
        val firstAuto = items.indices.firstOrNull { it > current && items[it].automatic } ?: items.size
        val target = when {
            playNow -> (current + 1).coerceIn(0, items.size)
            blending -> maxOf(firstAuto, current + 2).coerceAtMost(items.size)
            else -> firstAuto
        }
        items.add(target, track.copy(automatic = false))
        return Placement(items, target)
    }
}
