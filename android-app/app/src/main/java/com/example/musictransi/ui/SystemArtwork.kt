package com.example.musictransi.ui

import android.graphics.Bitmap
import androidx.core.graphics.scale

/** Same centre crop as the in-app square cover; bound binder/notification bitmap size. */
fun squareSystemArtwork(bitmap: Bitmap): Bitmap {
    val side = minOf(bitmap.width, bitmap.height)
    val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
    return if (side > 256) square.scale(256, 256) else square
}
