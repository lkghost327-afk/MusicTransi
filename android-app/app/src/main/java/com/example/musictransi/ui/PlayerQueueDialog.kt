package com.example.musictransi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.musictransi.playback.MixState

@Composable fun ImmersiveSurface(track: com.example.musictransi.source.Track, content: @Composable () -> Unit) {
    val cover = coverBitmap(track.artwork, track.id)
    var targetTint by remember { mutableStateOf(coverTint(cover)) }
    LaunchedEffect(cover) { if (cover != null) targetTint = coverTint(cover) }
    val tint by animateColorAsState(targetTint, tween(1400), label = "Immersive album colour")
    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, onSurface = Color.White,
        onSurfaceVariant = Color(0xFFCCC2BF), surface = Color(0xFF251B1A))) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint, Color(0xFF171315))))) {
            CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
        }
    }
}

@Composable fun PlayerQueueDialog(s: MixState, dismiss: () -> Unit, toggle: () -> Unit, previous: () -> Unit,
    next: () -> Unit, seek: (Long) -> Unit, playAt: (Int) -> Unit, autoplay: (Boolean) -> Unit,
    shuffle: () -> Unit, manage: () -> Unit) {
    val track = s.current ?: return
    var seeking by remember(track.id) { mutableStateOf<Float?>(null) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveSurface(track) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp).then(screenEntrance("queue"))) {
                Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track.artwork, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)), trackId = track.id)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = dismiss) { Text("Done") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = shuffle) { Text("Shuffle upcoming") }
                    Spacer(Modifier.weight(1f))
                    Text("Autoplay", fontSize = 12.sp)
                    Switch(s.autoplay, autoplay)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Continue Playing", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = manage) { Text("Edit") }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                    if (s.index >= s.queue.lastIndex) item { Text(if (s.findingRelated) "Finding your next song…" else "Your upcoming songs will appear here.") }
                    itemsIndexed(s.queue.drop(s.index + 1)) { index, song ->
                        LibraryTrackRow(song, { playAt(s.index + 1 + index) }, manage)
                    }
                }
                Slider(seeking ?: if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f,
                    { seeking = it }, enabled = s.durationMs > 0,
                    onValueChangeFinished = { seeking?.let { seek((it * s.durationMs).toLong()) }; seeking = null },
                    modifier = Modifier.semantics { contentDescription = "Queue song position" })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(queueTime(s.positionMs), fontSize = 12.sp)
                    Text("−${queueTime((s.durationMs - s.positionMs).coerceAtLeast(0))}", fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Transport("Previous", Color.White, previous, true)
                    Transport(if (s.playing) "Pause" else "Play", Color.White, toggle, true)
                    Transport("Next", Color.White, next, true)
                }
            }
        }
    }
}
private fun queueTime(ms: Long) = "%d:%02d".format(ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
