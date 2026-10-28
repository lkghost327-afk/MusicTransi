package com.example.musictransi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musictransi.source.Track

/** One short layer animation on navigation, never on the playback clock. */
@Composable fun screenEntrance(key: Any): Modifier {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(240)) }
    return Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}

@Composable fun EditorialCard(eyebrow: String, title: String, subtitle: String, track: Track?, colors: List<Color>, click: () -> Unit) {
    Box(Modifier.width(268.dp).height(310.dp).clip(RoundedCornerShape(18.dp))
        .background(Brush.linearGradient(colors)).clickable(onClick = click)) {
        if (track != null) Artwork(track.artwork, Modifier.fillMaxSize(), trackId = track.id)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .08f), Color.Black.copy(alpha = .85f)))))
        Text(eyebrow, color = Color.White.copy(alpha = .9f), fontSize = 11.sp, letterSpacing = 1.sp,
            modifier = Modifier.padding(20.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(title, color = Color.White, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = .85f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable fun BrowseCategories(search: (String) -> Unit) {
    val categories = listOf("Pop", "Hip-Hop", "Tamil", "Bollywood", "Punjabi", "Telugu", "Electronic", "Acoustic")
    val colors = listOf(0xFFAF3862, 0xFF74439A, 0xFF8F437C, 0xFF7852A6, 0xFF9D4A34, 0xFF3D6F83, 0xFF675096, 0xFF52745D)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categories.chunked(2).forEachIndexed { row, names ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                names.forEachIndexed { col, name ->
                    Box(Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(colors[row * 2 + col]), Color(0xFF24212D))))
                        .clickable { search("$name music") }) {
                        Canvas(Modifier.fillMaxSize()) {
                            for (i in 1..4) drawCircle(Color.White.copy(alpha = .12f), size.height * i / 5f,
                                Offset(size.width, 0f), style = Stroke(3.dp.toPx()))
                        }
                        Text(name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
                    }
                }
            }
        }
    }
}

@Composable fun MusicNavigation(selected: Int, select: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                listOf("Home", "Search", "Queue", "Library", "Activity").forEachIndexed { i, label ->
                    val color by animateColorAsState(if (i == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(180), label = "Navigation color")
                    Column(Modifier.weight(1f).selectable(selected == i, onClick = { select(i) }, role = Role.Tab)
                        .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.size(24.dp)) {
                            val w = size.width; val h = size.height
                            val stroke = 2.dp.toPx()
                            when (i) {
                                4 -> for (bar in 0..2) { val x = w * (.2f + bar * .3f); drawLine(color, Offset(x, h), Offset(x, h * (.5f - bar * .2f)), stroke * 2, StrokeCap.Round) }
                                0 -> drawPath(Path().apply { moveTo(0f, h * .45f); lineTo(w * .5f, 0f); lineTo(w, h * .45f); lineTo(w * .85f, h * .45f); lineTo(w * .85f, h); lineTo(w * .58f, h); lineTo(w * .58f, h * .65f); lineTo(w * .4f, h * .65f); lineTo(w * .4f, h); lineTo(w * .15f, h); lineTo(w * .15f, h * .45f); close() }, color)
                                1 -> { drawCircle(color, w * .32f, Offset(w * .38f, h * .38f), style = Stroke(stroke)); drawLine(color, Offset(w * .62f, h * .62f), Offset(w * .95f, h * .95f), stroke, StrokeCap.Round) }
                                2 -> for (r in 0..2) { val y = h * (.2f + r * .3f); drawCircle(color, stroke, Offset(stroke, y)); drawLine(color, Offset(w * .28f, y), Offset(w, y), stroke, StrokeCap.Round) }
                                3 -> { drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(stroke)); drawLine(color, Offset(w * .25f, h * .2f), Offset(w * .25f, h * .8f), stroke); drawLine(color, Offset(w * .5f, h * .2f), Offset(w * .75f, h * .8f), stroke) }
                            }
                        }
                        Text(label, fontSize = 11.sp, color = color, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
