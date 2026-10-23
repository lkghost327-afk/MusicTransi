package com.example.musictransi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musictransi.source.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import com.example.musictransi.downloads.OfflineDownloads

@Composable fun AlbumScreen(album: Album, saved: Boolean, modifier: Modifier, back: () -> Unit, save: () -> Unit,
    play: (List<Track>, Int, Boolean) -> Unit, queue: (List<Track>) -> Unit, songMenu: (Track) -> Unit, addPlaylist: (List<Track>) -> Unit) {
    var tracks by remember(album.id) { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember(album.id) { mutableStateOf(true) }
    var error by remember(album.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val offline = remember { OfflineDownloads(context) }
    suspend fun fetch() {
        loading = true; error = null; tracks = emptyList()
        try {
            tracks = withContext(Dispatchers.IO) { offline.albumTracks(album.id) }
            if (tracks.isNotEmpty()) loading = false
            var page = YouTubeSource.albumTracks(album)
            val result = page.tracks.toMutableList()
            var pages = 1
            while (page.next != null) {
                check(pages++ < 50) { "Album pagination limit" }
                page = YouTubeSource.albumTracks(album, page.next)
                result += page.tracks
            }
            tracks = result.distinctBy { it.id }
            withContext(Dispatchers.IO) { offline.cacheAlbum(album.id, tracks) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (tracks.isEmpty()) error = "Couldn't load this album. Check your connection and try again." }
        finally { loading = false }
    }
    LaunchedEffect(album.id) { fetch() }
    LazyColumn(modifier, contentPadding = PaddingValues(24.dp)) {
        item {
            TextButton(onClick = back) { Text("‹ Back") }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(album.artwork, Modifier.size(184.dp).clip(RoundedCornerShape(16.dp)))
                Text(album.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                Text(album.artist, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                TextButton(onClick = save) { Text(if (saved) "♥ Saved to Library" else "♡ Save album") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp))
            error?.let { Text(it); TextButton(onClick = { scope.launch { fetch() } }) { Text("Retry") } }
            if (!loading && error == null) {
                Text("${tracks.size} songs", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                CollectionButtons(tracks, { list, shuffle -> play(list, 0, shuffle) }, queue)
                TextButton(onClick = { addPlaylist(tracks) }, enabled = tracks.isNotEmpty()) { Text("Add album to playlist") }
                if (tracks.isEmpty()) Text("No playable tracks were returned for this album.")
            }
        }
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            LibraryTrackRow(track, { play(tracks, index, false) }, { songMenu(track) })
        }
    }
}
