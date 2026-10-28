package com.example.musictransi.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musictransi.source.Track
import kotlin.math.*

/** Matte translucent material; no refractive rims or expensive animated shaders. */
fun Modifier.frostedPanel(dark: Boolean = true): Modifier {
    val shape = RoundedCornerShape(22.dp)
    return clip(shape).background(Brush.verticalGradient(listOf(
        if (dark) Color(0xB82C2932) else Color(0xE8F4F3F6),
        if (dark) Color(0xD9222028) else Color(0xE8EAE8EF))))
        .border(.5.dp, if (dark) Color.White.copy(alpha = .10f) else Color.Black.copy(alpha = .06f), shape)
}

/** Gestures live on artwork/mini-player only, leaving sliders and lists independent. */
@Composable fun SwipeArtwork(
    key: String, modifier: Modifier = Modifier,
    left: () -> Unit, right: () -> Unit, up: () -> Unit, down: () -> Unit,
    content: @Composable () -> Unit
) {
    var x by remember(key) { mutableFloatStateOf(0f) }
    var y by remember(key) { mutableFloatStateOf(0f) }
    var dragging by remember(key) { mutableStateOf(false) }
    val actions by rememberUpdatedState(listOf(left, right, up, down))
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val returnX by animateFloatAsState(if (dragging) x else 0f, label = "Swipe return X")
    val returnY by animateFloatAsState(if (dragging) y else 0f, label = "Swipe return Y")
    Box(modifier.graphicsLayer {
        translationX = (if (dragging) x else returnX) * .35f
        translationY = (if (dragging) y else returnY) * .35f
        rotationZ = (translationX / 100f).coerceIn(-3f, 3f)
    }.pointerInput(key, threshold) {
        detectDragGestures(
            onDragStart = { x = 0f; y = 0f; dragging = true },
            onDragCancel = { dragging = false },
            onDragEnd = {
                dragging = false
                if (max(abs(x), abs(y)) >= threshold) {
                    if (abs(x) > abs(y) * 1.25f) actions[if (x < 0) 0 else 1]()
                    else if (abs(y) > abs(x) * 1.25f) actions[if (y < 0) 2 else 3]()
                }
            }
        ) { change, delta -> change.consume(); x += delta.x; y += delta.y }
    }, contentAlignment = Alignment.Center) { content() }
}

@Composable fun OrbitPlayer(track: Track, bitmap: Bitmap?, size: Dp, position: Long, duration: Long) {
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val accent = Color(0xFFA5F4DE)
    Box(Modifier.padding(vertical = 12.dp).size(size).semantics {
        contentDescription = "Orbit player, ${(progress * 100).toInt()} percent played"
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension * .46f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .17f), Color.Transparent)), radius * 1.08f)
            for (i in 0 until 60) {
                val a = (i * 6 - 90) * PI.toFloat() / 180f
                val unit = Offset(cos(a), sin(a))
                drawLine(Color.White.copy(alpha = if (i % 5 == 0) .5f else .15f),
                    center + unit * (radius - if (i % 5 == 0) 11.dp.toPx() else 5.dp.toPx()),
                    center + unit * radius, 1.dp.toPx())
            }
            val r = radius * .86f
            val corner = center - Offset(r, r)
            val bounds = Size(r * 2, r * 2)
            drawArc(Color.White.copy(alpha = .12f), -90f, 360f, false, corner, bounds, style = Stroke(2.dp.toPx()))
            drawArc(accent, -90f, progress * 360f, false, corner, bounds, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            val a = (progress * 360 - 90) * PI.toFloat() / 180f
            val point = center + Offset(cos(a), sin(a)) * r
            drawCircle(accent.copy(alpha = .18f), 10.dp.toPx(), point)
            drawCircle(accent, 4.dp.toPx(), point)
        }
        Artwork(track.artwork, Modifier.fillMaxSize(.65f).clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = .2f), CircleShape), trackId = track.id, bitmap = bitmap)
    }
}
