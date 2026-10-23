package com.example.musictransi.lyrics

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.musictransi.source.Track
import java.io.File

object LyricCards {
    fun canSelect(lines: List<String>) = lines.size in 1..4 && lines.joinToString("\n").length <= 280
    fun render(context: Context, track: Track, lines: List<String>, cover: Bitmap? = null): File {
        require(canSelect(lines))
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 1080f, 1350f, intArrayOf(Color.rgb(80, 44, 77), Color.rgb(20, 20, 28)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1080f, 1350f, paint)
        fun text(value: String, y: Float, size: Float, color: Int, max: Int, x: Float = 96f, width: Int = 888) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, tp, width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(max).setEllipsize(android.text.TextUtils.TruncateAt.END).setLineSpacing(12f, 1f).build()
            canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore()
        }
        val coverRect = RectF(96f, 96f, 320f, 320f)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(coverRect, 24f, 24f, Path.Direction.CW) })
        paint.shader = null
        if (cover != null) {
            val side = minOf(cover.width, cover.height)
            val left = (cover.width - side) / 2; val top = (cover.height - side) / 2
            paint.isFilterBitmap = true
            canvas.drawBitmap(cover, Rect(left, top, left + side, top + side), coverRect, paint)
        } else {
            paint.color = Color.rgb(118, 73, 110); canvas.drawRect(coverRect, paint)
            text("♪", 154f, 84f, Color.WHITE, 1, 178f, 130)
        }
        canvas.restore()
        text(track.title, 100f, 42f, Color.WHITE, 2, 356f, 628)
        text(track.artist, 230f, 30f, Color.LTGRAY, 2, 356f, 628)
        paint.color = Color.argb(55, 255, 255, 255); paint.strokeWidth = 2f
        canvas.drawLine(96f, 358f, 984f, 358f, paint)
        text(lines.joinToString("\n"), 400f, 46f, Color.WHITE, 11)
        text("MUSICTRANSI", 1260f, 24f, Color.LTGRAY, 1)
        val directory = File(context.cacheDir, "lyric-shares").apply { mkdirs() }
        directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(7)?.forEach { it.delete() }
        val file = File(directory, "lyrics-${java.util.UUID.randomUUID()}.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return file
    }
}
