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

// Cached optical layers keep the glass treatment usable on Android 8–11 too.
// This is a lightweight glass material, not a live backdrop-refraction shader.
fun Modifier.playerGlass(enabled: Boolean, dark: Boolean = true): Modifier {
    if (!enabled) return this
    val shape = RoundedCornerShape(28.dp)
    return shadow(10.dp, shape, clip = false,
        ambientColor = Color.Black.copy(alpha = .18f), spotColor = Color.Black.copy(alpha = .24f))
        .clip(shape).drawWithCache {
        val fill = Brush.linearGradient(listOf(
            Color.White.copy(alpha = if (dark) .19f else .72f),
            Color.White.copy(alpha = if (dark) .055f else .38f),
            Color(0xFFCBD5EF).copy(alpha = if (dark) .1f else .48f)))
        val edge = Brush.linearGradient(
            0f to Color.White.copy(alpha = .66f), .28f to Color.White.copy(alpha = .18f),
            .55f to Color.White.copy(alpha = .035f), 1f to Color(0xFFDCEBFF).copy(alpha = .4f),
            start = Offset.Zero, end = Offset(size.width, size.height))
        val shine = Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) .2f else .3f), Color.Transparent),
            center = Offset(size.width * .16f, -size.height * .6f), radius = size.width * .7f)
        val reflection = Brush.radialGradient(listOf(Color(0xFFB4CAFF).copy(alpha = .12f), Color.Transparent),
            center = Offset(size.width * .95f, size.height * 1.4f), radius = size.width * .55f)
        val line = 1.dp.toPx()
        val inset = line / 2
        val radius = (28.dp.toPx() - inset).coerceAtMost(size.height / 2 - inset).coerceAtLeast(0f)
        onDrawWithContent {
            drawRect(fill)
            drawRect(shine)
            drawRect(reflection)
            drawContent()
            drawRoundRect(edge, Offset(inset, inset), Size((size.width-line).coerceAtLeast(0f), (size.height-line).coerceAtLeast(0f)),
                androidx.compose.ui.geometry.CornerRadius(radius), style = Stroke(line))
            val inner = 2.dp.toPx()
            drawRoundRect(Color.Black.copy(alpha = if (dark) .12f else .045f), Offset(inner, inner),
                Size((size.width - inner * 2).coerceAtLeast(0f), (size.height - inner * 2).coerceAtLeast(0f)),
                androidx.compose.ui.geometry.CornerRadius((radius - inner).coerceAtLeast(0f)), style = Stroke(.5.dp.toPx()))
        }
    }
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
