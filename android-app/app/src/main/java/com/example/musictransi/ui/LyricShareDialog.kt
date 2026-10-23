package com.example.musictransi.ui

import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.musictransi.lyrics.LyricCards
import com.example.musictransi.source.Track
import kotlinx.coroutines.*
import java.io.File

@Composable fun LyricShareDialog(track: Track, lines: List<String>, dismiss: () -> Unit) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<File?>(null) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(track.id, lines) {
        try {
            val cover = withTimeoutOrNull(4000) { loadCoverBitmap(context, track.artwork, track.id) }
            val result = withContext(Dispatchers.IO) { LyricCards.render(context, track, lines, cover).let { it to android.graphics.BitmapFactory.decodeFile(it.path) } }
            file = result.first; bitmap = result.second
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't create the share card. Try again." }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Share lyrics") }, text = {
        Column {
            bitmap?.let { Image(it.asImageBitmap(), "Selected lyric lines with song and artist", Modifier.fillMaxWidth().aspectRatio(.8f)) }
            error?.let { Text(it) }
            if (file == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(enabled = file != null, onClick = {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.shares", file!!)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}\n${lines.joinToString("\n")}")
                clipData = ClipData.newUri(context.contentResolver, "Lyrics", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share lyric card"))
        } catch (_: Exception) { error = "No sharing app is available." }
    }) { Text("Share card") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
