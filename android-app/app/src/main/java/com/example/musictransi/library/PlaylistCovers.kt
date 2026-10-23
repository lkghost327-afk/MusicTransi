package com.example.musictransi.library

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PlaylistCovers {
    suspend fun save(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { readBounded(it, 8 * 1024 * 1024) }
        require(bytes.size <= 8 * 1024 * 1024) { "Choose an image smaller than 8 MB." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image format couldn't be read." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Couldn't open this image.")
        val side = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width-side)/2, (bitmap.height-side)/2, side, side)
        val scaled = cropped.scale(512, 512)
        val directory = File(context.filesDir, "playlist-covers").apply { mkdirs() }
        val file = File(directory, "${java.util.UUID.randomUUID()}.jpg")
        file.outputStream().use { check(scaled.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
        file.toURI().toString()
    }
}
