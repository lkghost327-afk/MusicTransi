package com.example.musictransi.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import com.example.musictransi.MainActivity
import com.example.musictransi.R
import com.example.musictransi.playback.MixService
import com.example.musictransi.source.Track

open class CompactPlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = PlayerWidgets.render(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = PlayerWidgets.render(context)
}
class ArtworkPlayerWidget : CompactPlayerWidget()

/** Event driven: no polling, periodic wakeups or separate playback engine. */
object PlayerWidgets {
    private data class Snapshot(val id: String?, val title: String, val artist: String, val playing: Boolean,
        val bitmap: Bitmap?, val liked: Boolean)
    private var current: Snapshot? = null
    fun publish(context: Context, track: Track?, playing: Boolean, cover: Bitmap?, liked: Boolean) {
        val next = Snapshot(track?.id, track?.title ?: "MusicTransi", track?.artist ?: "Choose a song in the app", playing, cover, liked)
        if (next == current) return
        current = next
        val prefs = context.getSharedPreferences("player-widget", 0)
        if (prefs.getString("title", null) != next.title || prefs.getString("artist", null) != next.artist)
            prefs.edit().putString("title", next.title).putString("artist", next.artist).apply()
        render(context)
    }
    fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val prefs = context.getSharedPreferences("player-widget", 0)
        val snapshot = current ?: Snapshot(null, prefs.getString("title", "MusicTransi")!!,
            prefs.getString("artist", "Choose a song in the app")!!, false, null, false)
        val session = context.getSharedPreferences("session", 0)
        val token = session.getString("notificationControlToken", null) ?: java.util.UUID.randomUUID().toString().also {
            session.edit().putString("notificationControlToken", it).apply()
        }
        fun command(action: String) = PendingIntent.getService(context, action.hashCode(),
            Intent(context, MixService::class.java).setAction(action).putExtra("controlToken", token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, 100, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        for ((provider, layout) in listOf(CompactPlayerWidget::class.java to R.layout.widget_compact,
            ArtworkPlayerWidget::class.java to R.layout.widget_artwork)) {
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isEmpty()) continue
            val views = RemoteViews(context.packageName, layout)
            views.setTextViewText(R.id.widget_title, snapshot.title)
            views.setTextViewText(R.id.widget_artist, snapshot.artist)
            views.setImageViewResource(R.id.widget_play, if (snapshot.playing) R.drawable.ic_pause else R.drawable.ic_play)
            views.setContentDescription(R.id.widget_play, if (snapshot.playing) "Pause" else "Play")
            views.setImageViewResource(R.id.widget_like, if (snapshot.liked) R.drawable.ic_like else R.drawable.ic_unlike)
            views.setContentDescription(R.id.widget_like, if (snapshot.liked) "Unlike song" else "Like song")
            if (snapshot.bitmap != null) views.setImageViewBitmap(R.id.widget_cover, snapshot.bitmap)
            else views.setImageViewResource(R.id.widget_cover, R.drawable.ic_music)
            views.setOnClickPendingIntent(R.id.widget_title, open)
            views.setOnClickPendingIntent(R.id.widget_cover, open)
            views.setOnClickPendingIntent(R.id.widget_artist, open)
            views.setOnClickPendingIntent(R.id.widget_previous, command("previous"))
            views.setOnClickPendingIntent(R.id.widget_play, command("widget_toggle"))
            views.setOnClickPendingIntent(R.id.widget_next, command("next"))
            views.setOnClickPendingIntent(R.id.widget_like, command("like"))
            manager.updateAppWidget(ids, views)
        }
    }
}
