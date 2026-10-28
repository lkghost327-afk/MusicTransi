package com.example.musictransi.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musictransi.source.AudioQuality

@Composable fun AudioQualitySettings(selected: AudioQuality, change: (AudioQuality) -> Unit) {
    val context = LocalContext.current
    var settingsError by remember { mutableStateOf(false) }
    Text("Streaming audio quality", fontWeight = FontWeight.Bold)
    AudioQuality.entries.forEach { quality ->
        val hint = when (quality) {
            AudioQuality.LOW -> "Target up to 48 kbps · less data"
            AudioQuality.MEDIUM -> "Target up to 128 kbps"
            AudioQuality.HIGH -> "Target up to 256 kbps"
            AudioQuality.VERY_HIGH -> "Highest offered bitrate · more data"
        }
        FilterChip(selected = quality == selected, onClick = { change(quality) },
            label = { Column { Text(quality.label); Text(hint, style = MaterialTheme.typography.bodySmall) } })
    }
    FilterChip(selected = false, onClick = {}, enabled = false, label = { Text("Lossless · unavailable for streaming") })
    Text("Uses available source streams, not guaranteed bitrates. If the target is missing, the nearest available tier is used. High and Very high may sound identical. Changes apply to newly loaded streams; current/preloaded songs and downloads keep their existing quality.",
        style = MaterialTheme.typography.bodySmall)
    Text("Local FLAC/WAV files keep their source format; mixing, effects and the device output can change the decoded audio. This is not a bit-perfect output mode.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    Text("Dolby Atmos / device sound", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
    Text("This source does not supply verified Atmos mixes. On supported phones, device sound settings may offer Dolby effects. Those effects do not turn a stereo stream into a genuine Atmos recording.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = {
        settingsError = runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }.isFailure
    }) { Text("Open device sound settings") }
    if (settingsError) Text("Open your phone's Settings → Sound manually.", style = MaterialTheme.typography.bodySmall)
}
