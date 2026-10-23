package com.example.musictransi.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musictransi.library.*
import com.example.musictransi.source.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val Accent = Color(0xFFEA3659)

@Composable fun CollectionButtons(tracks: List<Track>, play: (List<Track>, Boolean) -> Unit, queue: (List<Track>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = { play(tracks, false) }, enabled = tracks.isNotEmpty()) { Text("Play all") }
        OutlinedButton(onClick = { play(tracks, true) }, enabled = tracks.isNotEmpty()) { Text("Shuffle") }
        TextButton(onClick = { queue(tracks) }, enabled = tracks.isNotEmpty()) { Text("Queue") }
    }
    DownloadsButton(tracks)
}

@Composable fun LibraryScreen(state: LibraryState, store: LibraryStore, modifier: Modifier,
    play: (List<Track>, Int, Boolean) -> Unit, queue: (List<Track>) -> Unit,
    songMenu: (Track) -> Unit, openAlbum: (Album) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Long?>(null) } // -1: liked songs
    var create by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var addingSongs by remember { mutableStateOf(false) }
    var coverTarget by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    var rename by remember { mutableStateOf<SavedPlaylist?>(null) }
    var delete by remember { mutableStateOf<SavedPlaylist?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun edit(action: suspend () -> Unit) { scope.launch {
        try { action(); error = null } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't save that change. Please try again." }
    } }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = coverTarget
        if (uri != null && target != null) edit { store.setCover(target, PlaylistCovers.save(context, uri)) }
    }
    if (importing) LibraryImportDialog(store) { importing = false }
    if (addingSongs && selected != null && selected != -1L) PlaylistSongAdder({ addingSongs = false }) { store.addTracks(selected!!, it) }
    val playlist = state.playlists.find { it.id == selected }
    val tracks = if (selected == -1L) state.likes else playlist?.tracks.orEmpty()
    BackHandler(selected != null) { selected = null }
    LazyColumn(modifier, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            (error ?: state.error)?.let { Text(it, color = Accent) }
        }
        if (selected == null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { create = true }) { Text("New playlist") }
                    OutlinedButton(onClick = { importing = true }) { Text("Import library") }
                }
            }
            item {
                if (state.playlists.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                        items(state.playlists, key = { it.id }) { list ->
                            Column(Modifier.width(150.dp).clickable { selected = list.id }) {
                                Box(Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFC58F73), Color(0xFF4B3B44))))) {
                                    val first = list.tracks.firstOrNull()
                                    if (list.cover.isNotBlank()) Artwork(list.cover, Modifier.fillMaxSize())
                                    else if (first != null) Artwork(first.artwork, Modifier.fillMaxSize(), trackId = first.id)
                                    else Text(list.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(14.dp))
                                }
                                Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
                Text("Saved on this phone", color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
                DownloadsButton()
                CollectionCard("♥", "Liked songs", "${state.likes.size} songs", { selected = -1L })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Playlists", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { create = true }) { Text("＋ New playlist") }
                }
                if (state.playlists.isEmpty()) Text("Make a playlist, then add songs using their ••• menu.", color = Color.Gray)
            }
            items(state.playlists, key = { "playlist:${it.id}" }) { list ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { CollectionCard("♫", list.name, "${list.tracks.size} songs", { selected = list.id }, list.cover.ifBlank { list.tracks.firstOrNull()?.artwork.orEmpty() }) }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "Playlist options for ${list.name}" }) { Text("•••") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = list })
                            DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menu = false; delete = list })
                        }
                    }
                }
            }
            item { Text("Saved albums", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
                if (state.albums.isEmpty()) Text("Search Albums and save a release to find it here.", color = Color.Gray) }
            items(state.albums, key = { "album:${it.id}" }) { album -> AlbumRow(album) { openAlbum(album) } }
        } else {
            item {
                TextButton(onClick = { selected = null }) { Text("‹ Library") }
                Text(if (selected == -1L) "Liked songs" else playlist?.name ?: "Playlist removed", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("${tracks.size} songs", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                if (playlist != null) {
                    if (playlist.cover.isNotBlank()) Artwork(playlist.cover, Modifier.size(150.dp).clip(RoundedCornerShape(14.dp)))
                    Row {
                        Button(onClick = { addingSongs = true }) { Text("Add songs") }
                        TextButton(onClick = { coverTarget = playlist.id; coverPicker.launch(arrayOf("image/*")) }) { Text("Change cover") }
                        if (playlist.cover.isNotBlank()) TextButton(onClick = { edit { store.setCover(playlist.id, "") } }) { Text("Reset") }
                    }
                }
                CollectionButtons(tracks, { list, shuffle -> play(list, 0, shuffle) }, queue)
                if (tracks.isEmpty()) Text("No songs yet. Find a song in Search and use Like or Add to playlist.", modifier = Modifier.padding(top = 16.dp), color = Color.Gray)
            }
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                Column {
                    LibraryTrackRow(track, { play(tracks, index, false) }, { songMenu(track) })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (selected != -1L && playlist != null) {
                            TextButton(onClick = { edit { store.moveTrack(playlist.id, track.id, -1) } }, enabled = index > 0,
                                modifier = Modifier.semantics { contentDescription = "Move ${track.title} up" }) { Text("↑") }
                            TextButton(onClick = { edit { store.moveTrack(playlist.id, track.id, 1) } }, enabled = index < tracks.lastIndex,
                                modifier = Modifier.semantics { contentDescription = "Move ${track.title} down" }) { Text("↓") }
                        }
                        TextButton(onClick = { edit { if (selected == -1L) store.removeLike(track.id) else playlist?.let { store.removeTrack(it.id, track.id) } } }) { Text("Remove", color = Accent) }
                    }
                }
            }
        }
    }
    if (create || rename != null) PlaylistNameDialog(rename?.name.orEmpty(), { create = false; rename = null }) { name ->
        val target = rename
        edit { if (target == null) selected = store.createPlaylist(name) else store.renamePlaylist(target.id, name) }
        create = false; rename = null
    }
    delete?.let { target -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete ${target.name}?") },
        text = { Text("This removes the playlist. Your liked songs and other playlists are kept.") },
        confirmButton = { TextButton(onClick = { edit { store.deletePlaylist(target.id) }; delete = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } }) }
}

@Composable private fun CollectionCard(symbol: String, title: String, subtitle: String, click: () -> Unit, artwork: String = "") {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (artwork.isNotBlank()) Artwork(artwork, Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)))
        else Box(Modifier.size(60.dp).background(Color(0xFFF9DDE4), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(symbol, fontSize = 28.sp, color = Accent) }
        Column(Modifier.padding(start = 14.dp)) { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(subtitle, fontSize = 12.sp, color = Color.Gray) }
    }
}
@Composable fun AlbumRow(album: Album, click: () -> Unit) = CollectionCard("♫", album.title, album.artist, click, album.artwork)
@Composable fun LibraryTrackRow(track: Track, play: () -> Unit, more: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = play).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track.artwork, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), trackId = track.id)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1) }
        TextButton(onClick = more, modifier = Modifier.semantics { contentDescription = "Song options for ${track.title}" }) { Text("•••") }
    }
}
@Composable fun PlaylistNameDialog(initial: String = "", dismiss: () -> Unit, heading: String? = null, save: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(heading ?: if (initial.isBlank()) "New playlist" else "Rename playlist") },
        text = { OutlinedTextField(name, { name = it.take(80) }, label = { Text("Playlist name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { save(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable fun PlaylistPicker(tracks: List<Track>, state: LibraryState, store: LibraryStore, dismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun add(id: Long? = null, name: String = "") { if (saving) return; saving = true; scope.launch {
        try { if (id == null) store.createPlaylist(name, tracks) else store.addTracks(id, tracks); dismiss() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't save songs. Try again." }
        finally { saving = false }
    } }
    AlertDialog(onDismissRequest = { if (!saving) dismiss() }, title = { Text("Add to playlist") }, text = {
        LazyColumn(Modifier.heightIn(max = 350.dp)) {
            item { Text("${tracks.size} song(s)", color = Color.Gray); error?.let { Text(it, color = Accent) }
                OutlinedTextField(newName, { newName = it.take(80) }, label = { Text("New playlist name") }, singleLine = true)
                TextButton(onClick = { add(name = newName.trim()) }, enabled = !saving && newName.isNotBlank()) { Text("Create & add songs") } }
            items(state.playlists, key = { it.id }) { list -> TextButton(onClick = { add(list.id) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(list.name) } }
        }
    }, confirmButton = { TextButton(onClick = dismiss, enabled = !saving) { Text("Done") } })
}
