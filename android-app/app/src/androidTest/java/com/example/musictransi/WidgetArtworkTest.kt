package com.example.musictransi

import android.graphics.Bitmap
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musictransi.ui.squareSystemArtwork
import org.junit.Assert.*
import org.junit.Test

class WidgetArtworkTest {
    @Test fun cropsWideAndTallArtWithoutStretchingAndBoundsSize() {
        for ((width, height) in listOf(640 to 360, 360 to 640, 128 to 128)) {
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            image.eraseColor(Color.RED)
            image.setPixel(width / 2, height / 2, Color.BLUE)
            val result = squareSystemArtwork(image)
            assertEquals(result.width, result.height)
            assertTrue(result.width <= 256)
            assertEquals(Color.RED, result.getPixel(0, 0))
        }
    }
    @Test fun bothRemoteViewsInflateWithAllTransportTargets() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            for (layout in listOf(R.layout.widget_compact, R.layout.widget_artwork)) {
                val view = RemoteViews(context.packageName, layout).apply(context, FrameLayout(context))
                for (id in listOf(R.id.widget_title, R.id.widget_artist, R.id.widget_cover,
                    R.id.widget_previous, R.id.widget_play, R.id.widget_next, R.id.widget_like)) {
                    assertNotNull(view.findViewById<android.view.View>(id))
                }
            }
        }
    }
}
