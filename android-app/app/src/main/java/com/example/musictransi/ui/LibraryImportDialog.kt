package com.example.musictransi.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.musictransi.library.*
import kotlinx.coroutines.*

@Composable fun LibraryImportDialog(store: LibraryStore, dismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<ImportRow>>(emptyList()) }
    var matches by remember { mutableStateOf<List<ImportMatch>>(emptyList()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; saved = false; matches = emptyList(); rows = emptyList(); message = null
            try { rows = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { LibraryImport.parse(readBounded(it, 2*1024*1024).toString(Charsets.UTF_8)) } } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Couldn't open this file." }
            finally { busy = false }
        }
    }
    Dialog(onDismissRequest = { if (!saving) { job?.cancel(); dismiss() } }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
                Row { Text("Import your music", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)); TextButton(onClick = { job?.cancel(); dismiss() }, enabled = !saving) { Text("Done") } }
                Text("Import metadata from exported CSV or JSON files. Songs and albums are matched to this app's catalog; audio files and account access aren't transferred.", style = MaterialTheme.typography.bodySmall)
                Text("CSV columns: title, artist, playlist, type. Type: playlist, likes or album. Up to 500 entries / 2 MB.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = { picker.launch(arrayOf("text/*", "application/json", "application/octet-stream")) }, enabled = !busy && !saving) { Text("Choose export file") }
                message?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("${matches.size} of ${rows.size} checked · ${matches.count { it.track != null || it.album != null }} matched", modifier = Modifier.padding(vertical = 8.dp))
                Row {
                    TextButton(enabled = rows.isNotEmpty() && !busy && !saving && !saved, onClick = {
                        busy = true; message = null
                        job = scope.launch {
                            try {
                                for (row in rows.drop(matches.size)) {
                                    val match = try { LibraryImport.match(row) } catch (e: CancellationException) { throw e } catch (_: Exception) { ImportMatch(row) }
                                    matches = matches + match
                                }
                                message = "Review the matches below. Unmatched entries won't be saved."
                            } finally { busy = false }
                        }
                    }) { Text(if (matches.isEmpty()) "Find matches" else "Continue matching") }
                    if (busy) TextButton(onClick = { job?.cancel() }) { Text("Stop") }
                }
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(rows) { index, row ->
                        val match = matches.getOrNull(index)
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(row.title); Text("${row.artist} · ${row.collection}", style = MaterialTheme.typography.bodySmall)
                            Text(when { match == null -> "Not checked"; match.track != null -> "Matched: ${match.track.title} — ${match.track.artist}"; match.album != null -> "Matched album: ${match.album.title}"; else -> "No confident match / provider unavailable" }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Button(enabled = !busy && !saving && !saved && matches.any { it.track != null || it.album != null }, onClick = {
                    saving = true
                    scope.launch {
                        try {
                            store.importCollections(matches.filter { it.row.kind == "playlist" && it.track != null }.groupBy { it.row.collection }.mapValues { it.value.mapNotNull { m -> m.track } },
                                matches.filter { it.row.kind == "likes" }.mapNotNull { it.track }, matches.mapNotNull { it.album })
                            saved = true; message = "Imported into your Library. Existing playlists with the same name were merged without duplicate tracks."
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { message = "Couldn't save this import. Nothing was changed; try again." }
                        finally { saving = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Saving…" else "Import matched items") }
            }
        }
    }
}
