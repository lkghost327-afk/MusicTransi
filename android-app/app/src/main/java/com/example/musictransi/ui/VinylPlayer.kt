package com.example.musictransi.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.musictransi.source.Track
import kotlinx.coroutines.isActive

@Composable fun VinylPlayer(track: Track, bitmap: Bitmap?, playing: Boolean, size: Dp, recordOnly: Boolean = false) {
    var angle by remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val arm by animateFloatAsState(if (playing) 0f else -27f, tween(650), label = "Tonearm")
    LaunchedEffect(playing, lifecycle, recordOnly) {
        if (playing) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (isActive) withFrameNanos { now ->
                if (previous != 0L) angle = (angle + ((now - previous) / 1_000_000_000f).coerceAtMost(.05f) * (if (recordOnly) 20f else 200f)) % 360f
                previous = now
            }
        }
    }
    Box(Modifier.padding(vertical = 12.dp).width(size).height(if (recordOnly) size else size * .88f)
        .clip(RoundedCornerShape(18.dp)).background(if (recordOnly) Color.Transparent else Color(0xFF373239))) {
        Box((if (recordOnly) Modifier.fillMaxSize() else Modifier.padding(start = size * .035f, top = size * .035f).size(size * .76f))
            .graphicsLayer { rotationZ = angle }.clip(CircleShape).background(Color(0xFF111115)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                for (i in 7..22) drawCircle(Color.White.copy(alpha = .09f), this.size.minDimension * i / 46f, style = Stroke(1f))
                drawLine(Color.White.copy(alpha = .15f), center, Offset(this.size.width * .83f, this.size.height * .15f), 2f)
            }
            Artwork(track.artwork, Modifier.fillMaxSize(.40f).clip(CircleShape), trackId = track.id, bitmap = bitmap)
            Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFCBC8C9)))
        }
        if (!recordOnly) Canvas(Modifier.fillMaxSize()) {
            val pivot = Offset(this.size.width * .87f, this.size.height * .16f)
            drawCircle(Color(0xFF17171B), this.size.width * .07f, pivot)
            rotate(arm, pivot) {
                val elbow = Offset(this.size.width * .84f, this.size.height * .51f)
                val tip = Offset(this.size.width * .65f, this.size.height * .70f)
                drawLine(Color.Black.copy(alpha = .4f), pivot + Offset(4f, 6f), elbow + Offset(4f, 6f), 12f, StrokeCap.Round)
                drawLine(Color(0xFFD9D4D5), pivot, elbow, 7f, StrokeCap.Round)
                drawLine(Color(0xFFD9D4D5), elbow, tip, 7f, StrokeCap.Round)
                drawLine(Color(0xFFFA7D99), tip, tip + Offset(-8f, 10f), 12f, StrokeCap.Round)
                drawCircle(Color(0xFF85818A), this.size.width * .035f, pivot)
            }
            drawCircle(if (playing) Color(0xFFEE718B) else Color(0xFF69626B), 5.dp.toPx(), Offset(this.size.width * .10f, this.size.height * .94f))
            drawRoundRect(Color(0xFF18171D), Offset(this.size.width * .79f, this.size.height * .88f), Size(this.size.width * .13f, 8.dp.toPx()))
        }
    }
}
