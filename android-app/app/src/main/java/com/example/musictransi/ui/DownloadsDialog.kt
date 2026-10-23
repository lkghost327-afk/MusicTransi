package com.example.musictransi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.musictransi.downloads.OfflineDownloads
import com.example.musictransi.source.Track
import kotlinx.coroutines.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable fun DownloadsButton(tracks: List<Track>? = null) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, enabled = tracks == null || tracks.any { !it.local }) {
        Text(if (tracks == null) "Downloads" else "Download / manage offline")
    }
    if (open) DownloadsDialog(tracks) { open = false }
}

@Composable private fun DownloadsDialog(tracks: List<Track>?, dismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext
    val store = remember { OfflineDownloads(app) }
    var entries by remember { mutableStateOf<List<OfflineDownloads.Entry>>(emptyList()) }
    var wifi by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun edit(action: () -> Unit) { busy = true; scope.launch {
        try { withContext(Dispatchers.IO) { action() }; entries = withContext(Dispatchers.IO) { store.entries() }; error = null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't update downloads. Check storage and try again." }
        finally { busy = false }
    } }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (isActive) {
            try { entries = withContext(Dispatchers.IO) { store.entries() } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn't read downloads." }
            delay(1500)
        }
    } }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Offline music") }, text = {
        Column {
            Text("Saved on this phone. Removing a download removes its offline copy from all collections. Uninstalling deletes downloads.")
            Row { Text("New downloads: Wi-Fi only", Modifier.weight(1f)); Switch(wifi, { wifi = it }) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val selected = tracks ?: entries.map { it.track }
            TextButton(onClick = { edit { store.enqueue(selected, wifi) } }, enabled = !busy && selected.any { !it.local }) {
                Text(if (tracks == null) "Retry incomplete downloads" else "Download ${tracks.count { !it.local }} songs / retry failed")
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                val visible = if (tracks == null) entries else entries.filter { e -> tracks.any { it.id == e.track.id } }
                if (visible.isEmpty()) item { Text("No downloads yet. Start above to save this collection.") }
                items(visible, key = { it.track.id }) { entry ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(entry.track.title, maxLines = 2)
                        Text(entry.status + (entry.percent?.takeIf { !entry.ready }?.let { " · $it%" } ?: ""))
                        TextButton(onClick = { edit { store.remove(entry.track.id) } }, enabled = !busy) { Text("Remove download") }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
}
