package com.example.musictransi.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import com.example.musictransi.source.Track

data class ArtworkPage(val track: Track, val queueIndex: Int)

/** Queue position determines navigation direction, including notification/automatic changes. */
@Composable fun TrackArtworkTransition(track: Track, queueIndex: Int, content: @Composable (Track) -> Unit) {
    AnimatedContent(
        targetState = ArtworkPage(track, queueIndex),
        contentKey = { it.track.id },
        modifier = Modifier.clipToBounds(),
        transitionSpec = {
            val direction = if (targetState.queueIndex < initialState.queueIndex) -1 else 1
            ((slideInHorizontally(tween(520)) { direction * it / 3 } + fadeIn(tween(460))) togetherWith
                (slideOutHorizontally(tween(520)) { -direction * it / 3 } + fadeOut(tween(320))))
                .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(520) }))
        }, label = "Track artwork slide"
    ) { page -> content(page.track) }
}
