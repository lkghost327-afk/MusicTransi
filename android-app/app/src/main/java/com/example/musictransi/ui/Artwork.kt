package com.example.musictransi.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.musictransi.source.YouTubeSource
import com.example.musictransi.source.ArtworkPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.OkHttpClient
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private object CoverRequests {
    val slots = Semaphore(3)
    private var client: OkHttpClient? = null
    @Synchronized fun client(context: android.content.Context): OkHttpClient = client ?: YouTubeSource.http.newBuilder()
        .cache(Cache(File(context.cacheDir, "covers-http"), 24L * 1024 * 1024))
        .callTimeout(10, TimeUnit.SECONDS).build().also { client = it }
}

private val covers = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}
@Composable fun coverBitmap(url: String, trackId: String = ""): Bitmap? {
    val context = LocalContext.current.applicationContext
    val key = "$url|$trackId"
    val bitmap by key(key) { produceState<Bitmap?>(covers.get(key), key) {
        value = loadCoverBitmap(context, url, trackId)
    } }
    return bitmap
}
suspend fun loadCoverBitmap(context: android.content.Context, url: String, trackId: String): Bitmap? {
    val key = "$url|$trackId"
    covers.get(key)?.let { return it }
    if (url.startsWith("file:")) return withContext(Dispatchers.IO) {
        runCatching {
            val file = File(java.net.URI(url)).canonicalFile
            val allowed = File(context.filesDir, "playlist-covers").canonicalFile
            require(file.parentFile == allowed && file.length() <= 2 * 1024 * 1024)
            BitmapFactory.decodeFile(file.path)?.also { covers.put(key, it) }
        }.getOrNull()
    }
    repeat(2) { attempt ->
        for (candidate in ArtworkPolicy.candidates(url, trackId)) {
            val bitmap = withContext(Dispatchers.IO) { CoverRequests.slots.withPermit {
                try {
                    CoverRequests.client(context).newCall(Request.Builder().url(candidate).build()).execute().use { response ->
                        check(response.isSuccessful)
                        val output = ByteArrayOutputStream()
                        response.body!!.byteStream().use { input ->
                            val chunk = ByteArray(8192)
                            while (true) {
                                val count = input.read(chunk)
                                if (count < 0) break
                                check(output.size() + count <= 4 * 1024 * 1024)
                                output.write(chunk, 0, count)
                            }
                        }
                        val bytes = output.toByteArray()
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = ArtworkPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
                        }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { covers.put(key, it) }
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { null }
            } }
            if (bitmap != null) return bitmap
        }
        if (attempt == 0) delay(1200)
    }
    return null
}

fun coverTint(bitmap: Bitmap?): Color {
    if (bitmap == null) return Color(0xFF63314A)
    var r = 0L; var g = 0L; var b = 0L; var n = 0
    for (x in 0 until bitmap.width step maxOf(1, bitmap.width / 12))
        for (y in 0 until bitmap.height step maxOf(1, bitmap.height / 12)) {
            val p = bitmap.getPixel(x, y)
            r += android.graphics.Color.red(p); g += android.graphics.Color.green(p); b += android.graphics.Color.blue(p); n++
        }
    return Color(r / n / 255f * .65f, g / n / 255f * .65f, b / n / 255f * .65f)
}
@Composable fun Artwork(url: String, modifier: Modifier = Modifier, trackId: String = "", bitmap: Bitmap? = coverBitmap(url, trackId)) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFFDC687F), Color(0xFF54354B)))), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("♫", color = Color.White.copy(alpha = .7f), fontSize = 38.sp)
    }
}
