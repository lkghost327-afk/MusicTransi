package com.example.musictransi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.musictransi.source.YouTubeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

private object ArtistPortraits {
    private val lock = Mutex()
    suspend fun get(context: android.content.Context, artist: String): String = withContext(Dispatchers.IO) {
        lock.withLock {
            val prefs = context.getSharedPreferences("artist-portraits", 0)
            val key = artist.trim().lowercase(java.util.Locale.ROOT)
            val now = System.currentTimeMillis()
            val old = prefs.getString(key, null)?.split("|", limit = 2)
            val age = old?.firstOrNull()?.toLongOrNull()?.let { now - it }
            val cached = old?.getOrNull(1).orEmpty()
            val ttl = if (cached.isBlank()) 60 * 60 * 1000L else 7 * 24 * 60 * 60 * 1000L
            if (age != null && age in 0 until ttl) return@withLock cached
            val url = try { YouTubeSource.artistPortrait(artist).orEmpty() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { return@withLock cached }
            val editor = prefs.edit()
            if (prefs.all.size >= 200 && !prefs.contains(key)) {
                prefs.all.entries.minByOrNull { it.value.toString().substringBefore('|').toLongOrNull() ?: 0L }?.let { editor.remove(it.key) }
            }
            editor.putString(key, "$now|$url").apply()
            url
        }
    }
}

@Composable fun ArtistPortrait(artist: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val url by produceState("", artist) { value = ""; value = ArtistPortraits.get(context, artist) }
    val bitmap = coverBitmap(url)
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
        .semantics { contentDescription = "$artist artist image" }, contentAlignment = Alignment.Center) {
        if (bitmap != null) Artwork(url, Modifier.fillMaxSize(), bitmap = bitmap)
        else Text(artist.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase(),
            color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
    }
}
