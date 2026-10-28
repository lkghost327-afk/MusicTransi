package com.example.musictransi.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.example.musictransi.lyrics.*
import com.example.musictransi.source.Track
import kotlinx.coroutines.CancellationException

@Composable fun LyricsDialog(track: Track, position: Long, duration: Long, playing: Boolean,
    seek: (Long) -> Unit, toggle: () -> Unit, previous: () -> Unit, next: () -> Unit, dismiss: () -> Unit,
    load: suspend (Track, Long) -> Lyrics = LyricsClient::fetch) {
    var lyrics by remember(track.id) { mutableStateOf<Lyrics?>(null) }
    var error by remember(track.id) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var follow by remember { mutableStateOf(true) }
    var offset by remember(track.id) { mutableFloatStateOf(0f) }
    var timing by remember { mutableStateOf(false) }
    var estimatedWords by remember { mutableStateOf(true) }
    var selecting by remember(track.id) { mutableStateOf(false) }
    var selected by remember(track.id) { mutableStateOf<Set<Int>>(emptySet()) }
    var sharing by remember(track.id) { mutableStateOf(false) }
    var seeking by remember(track.id) { mutableStateOf<Float?>(null) }
    val state = rememberLazyListState()
    val dragging by state.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(track.id, duration / 1000, retry) {
        lyrics = null; error = null
        try { lyrics = load(track, duration / 1000) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't load lyrics. Check your connection and retry." }
    }
    val lines = lyrics?.lines.orEmpty()
    val shareLines = if (lines.isNotEmpty()) lines.map { it.text } else lyrics?.plain.orEmpty().lines().filter { it.isNotBlank() }
    if (sharing) LyricShareDialog(track, selected.sorted().mapNotNull { shareLines.getOrNull(it) }) { sharing = false }
    fun choose(index: Int) {
        val next = if (index in selected) selected - index else selected + index
        if (next.isEmpty() || LyricCards.canSelect(next.sorted().map { shareLines[it] })) selected = next
    }
    val current = lines.indexOfLast { it.timeMs <= position + (offset * 1000).toLong() }
    LaunchedEffect(track.id, current, follow, selecting) {
        if (follow && !selecting && current >= 0) {
            // Keep the active line below the top edge; typography never changes its measured height.
            val inset = (state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset) / 4
            if (kotlin.math.abs(current - state.firstVisibleItemIndex) > 8) state.scrollToItem(current, -inset)
            else state.animateScrollToItem(current, -inset)
        }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      ImmersiveSurface(track) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp).then(screenEntrance("lyrics"))) {
            Row {
                Text("Lyrics · ${track.title}", maxLines = 2, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                TextButton(onClick = dismiss) { Text("Done") }
            }
            Text("From LRCLIB · availability and timing vary", fontSize = 11.sp)
            if (shareLines.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selecting = !selecting; selected = emptySet(); if (selecting) follow = false }) {
                        Text(if (selecting) "Cancel selection" else "Share lines")
                    }
                    if (selecting) TextButton(onClick = { sharing = true }, enabled = selected.isNotEmpty()) { Text("Preview") }
                }
                if (selecting) Text("Choose up to 4 lines · 280 characters", fontSize = 11.sp)
            }
            Column(Modifier.weight(1f).fillMaxWidth().frostedPanel().padding(16.dp)) {
            when {
                error != null -> { Text(error!!); TextButton(onClick = { retry++ }) { Text("Retry") } }
                lyrics == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 20.dp))
                lyrics!!.instrumental -> Text("This recording is marked instrumental.")
                lines.isNotEmpty() -> {
                    Row { Text("Follow playback", Modifier.weight(1f)); Switch(follow, { follow = it }); TextButton(onClick = { timing = !timing }) { Text("Timing") } }
                    if (lines.any { it.words.isEmpty() }) {
                        Text("Estimated word follow for lines without word timing", style = MaterialTheme.typography.labelSmall)
                        if (timing) Row { Text("Estimate words", Modifier.weight(1f)); Switch(estimatedWords, { estimatedWords = it }) }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state, contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                            val wordEnd = if (!selecting && index == current) WordFollow.highlightedEnd(line,
                                position + (offset * 1000).toLong(), lines.getOrNull(index + 1)?.timeMs ?: duration, estimatedWords) else 0
                            LyricRow(line.text, index, if (selecting) index in selected else index == current, wordEnd) {
                                if (selecting) choose(index) else { follow = true; seek((line.timeMs - (offset * 1000).toLong()).coerceAtLeast(0)) }
                            }
                        }
                    }
                    if (timing) {
                        Text("Lyrics offset: ${"%.1f".format(offset)}s · tap a line to seek", fontSize = 11.sp)
                        Slider(offset, { offset = it }, valueRange = -3f..3f, steps = 59,
                            modifier = Modifier.semantics { contentDescription = "Lyrics timing offset" })
                    }
                }
                lyrics!!.plain.isNotBlank() -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("Unsynced lyrics", fontSize = 11.sp) }
                    itemsIndexed(shareLines) { index, line -> LyricRow(line, index, selecting && index in selected) { if (selecting) choose(index) } }
                }
                else -> Text(if (track.local) "Lyrics lookup needs an online song's title and artist metadata." else "No matching lyrics found for this recording.")
            }
            }
            HorizontalDivider()
            Slider(seeking ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                { seeking = it }, enabled = duration > 0, onValueChangeFinished = {
                    seeking?.let { seek((it * duration).toLong()); follow = true }; seeking = null
                }, modifier = Modifier.testTag("lyrics-seek").semantics { contentDescription = "Song position" })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(lyricTime(seeking?.let { (it * duration).toLong() } ?: position), fontSize = 12.sp)
                Text(lyricTime(duration), fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Transport("Previous", MaterialTheme.colorScheme.onSurface, previous)
                Transport(if (playing) "Pause" else "Play", MaterialTheme.colorScheme.onSurface, toggle)
                Transport("Next", MaterialTheme.colorScheme.onSurface, next)
            }
        }
      }
    }
}
@Composable private fun LyricRow(text: String, index: Int, active: Boolean, wordEnd: Int = 0, seek: () -> Unit) {
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f),
        tween(250), label = "Lyric emphasis")
    val styled = buildAnnotatedString {
        append(text.ifBlank { "♪" })
        if (active && wordEnd > 0) {
            addStyle(SpanStyle(color = color.copy(alpha = .4f)), 0, length)
            addStyle(SpanStyle(color = color), 0, wordEnd.coerceAtMost(length))
        }
    }
    Text(styled, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold,
        color = color, modifier = Modifier.fillMaxWidth().testTag("lyric-$index").clickable(onClick = seek))
}
private fun lyricTime(ms: Long) = "%d:%02d".format(ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
