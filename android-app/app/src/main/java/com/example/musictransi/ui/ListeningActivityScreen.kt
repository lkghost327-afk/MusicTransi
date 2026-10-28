package com.example.musictransi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musictransi.library.ListeningActivity
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable fun ListeningActivityScreen(store: ListeningActivity, modifier: Modifier = Modifier) {
    val totals by store.state.collectAsState()
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthValue)
    var selectedArtist by rememberSaveable(monthValue) { mutableStateOf<String?>(null) }
    val rows = remember(totals, month) { totals.filter { it.month == month.toString() }.sortedByDescending { it.milliseconds } }
    val artists = remember(rows) { rows.groupBy { it.artist }.mapValues { (_, songs) -> songs.sumOf { it.milliseconds } }.entries.sortedByDescending { it.value } }
    fun minutes(ms: Long) = if (ms < 60000) "${ms / 1000}s" else "${ms / 60000} min"
    LazyColumn(modifier, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { monthValue = month.minusMonths(1).toString() }, enabled = month > YearMonth.now().minusMonths(23),
                    modifier = Modifier.semantics { contentDescription = "Previous month" }) { Text("‹") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f).padding(top = 12.dp))
                TextButton(onClick = { monthValue = month.plusMonths(1).toString() }, enabled = month < YearMonth.now(),
                    modifier = Modifier.semantics { contentDescription = "Next month" }) { Text("›") }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("YOUR MONTH IN MUSIC", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(minutes(rows.sumOf { it.milliseconds }), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Time spent listening")
                    HorizontalDivider()
                    Text("${rows.size} songs · ${artists.size} artists", style = MaterialTheme.typography.titleMedium)
                    rows.firstOrNull()?.let { song ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Artwork(song.artwork, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), trackId = song.id)
                            Column(Modifier.weight(1f)) {
                                Text("Your most listened song", style = MaterialTheme.typography.labelSmall)
                                Text(song.title, fontWeight = FontWeight.SemiBold)
                                Text(song.artist, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Text("On this device · last 24 months · updates every 15 seconds", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (rows.isEmpty()) item { Text("Play some music to start your monthly activity.") }
        item { Text("Top artists", style = MaterialTheme.typography.titleLarge) }
        items(artists, key = { "artist:${it.key}" }) { artist ->
            Surface(onClick = { selectedArtist = if (selectedArtist == artist.key) null else artist.key },
                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ArtistPortrait(artist.key, Modifier.size(56.dp))
                        Column(Modifier.weight(1f)) {
                            Text(artist.key, fontWeight = FontWeight.SemiBold)
                            Text("${minutes(artist.value)} · ${rows.count { it.artist == artist.key }} songs")
                        }
                    }
                    LinearProgressIndicator(progress = { (artist.value.toFloat() / artists.first().value.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Text(selectedArtist?.let { "Songs by $it" } ?: "Top songs", style = MaterialTheme.typography.titleLarge)
            if (selectedArtist != null) TextButton(onClick = { selectedArtist = null }) { Text("Show all songs") }
        }
        items(rows.filter { selectedArtist == null || it.artist == selectedArtist }, key = { it.id }) { song ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(song.artwork, Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)), trackId = song.id)
                Column(Modifier.weight(1f)) {
                    Text(song.title, fontWeight = FontWeight.SemiBold)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium)
                    Text(minutes(song.milliseconds), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
