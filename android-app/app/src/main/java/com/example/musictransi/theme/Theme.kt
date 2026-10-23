package com.example.musictransi.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFF375F),
    secondary = androidx.compose.ui.graphics.Color(0xFFFF8FA6),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF242426),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFAAAAAF),
    background = androidx.compose.ui.graphics.Color(0xFF000000),
    surface = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF5F5F7),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF5F5F7))

private val LightColorScheme =
  lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFEA3659),
    secondary = androidx.compose.ui.graphics.Color(0xFF9A405C),
    background = androidx.compose.ui.graphics.Color(0xFFFCFAFB),
    surface = androidx.compose.ui.graphics.Color(0xFFFCFAFB),
    onSurface = androidx.compose.ui.graphics.Color(0xFF211D23),

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
  )

@Composable
fun MusicTransiTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
