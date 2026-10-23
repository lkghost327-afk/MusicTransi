package com.example.musictransi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun SuggestedMixDialog(mix: SuggestedMix, dismiss: () -> Unit, play: () -> Unit, queue: () -> Unit, save: () -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
                Row {
                    Text(mix.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = dismiss) { Text("Done") }
                }
                Text(mix.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${mix.tracks.size} songs", modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = play) { Text("Play mix") }
                    OutlinedButton(onClick = queue) { Text("Queue") }
                }
                TextButton(onClick = save) { Text("Save as playlist") }
                LazyColumn(Modifier.weight(1f)) {
                    items(mix.tracks, key = { it.id }) { track ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Artwork(track.artwork, Modifier.size(48.dp), trackId = track.id)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(track.title, maxLines = 2)
                                Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
