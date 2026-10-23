package com.example.musictransi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musictransi.source.*
import kotlinx.coroutines.*

@Composable fun PlaylistSongAdder(dismiss: () -> Unit, add: suspend (List<Track>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var selected by remember { mutableStateOf<List<Track>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Add songs") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("Song or artist") }, singleLine = true)
            TextButton(enabled = query.isNotBlank() && !busy, onClick = { busy = true; scope.launch {
                try { results = YouTubeSource.search(query); error = if (results.isEmpty()) "No matches. Try another search." else null }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "Search failed. Try again." }
                finally { busy = false }
            } }) { Text("Search") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it) }
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(results, key = { it.id }) { track ->
                    fun toggle() { selected = if (selected.any { it.id == track.id }) selected.filterNot { it.id == track.id } else selected + track }
                    Row(Modifier.fillMaxWidth().clickable { toggle() }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(selected.any { it.id == track.id }, { toggle() })
                        Column(Modifier.weight(1f)) { Text(track.title, maxLines = 2); Text(track.artist, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(enabled = selected.isNotEmpty() && !busy, onClick = { busy = true; scope.launch {
        try { add(selected); dismiss() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't add songs. Try again." }
        finally { busy = false }
    } }) { Text("Add ${selected.size} songs") } }, dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } })
}
