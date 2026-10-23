package com.example.musictransi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.*
import android.os.IBinder
import android.provider.OpenableColumns
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musictransi.playback.MixService
import com.example.musictransi.source.Track
import com.example.musictransi.ui.MusicScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.musictransi.theme.MusicTransiTheme

class MainActivity : ComponentActivity() {
  private var service by mutableStateOf<MixService?>(null)
  private var bound = false
  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      service = (binder as MixService.LocalBinder).service
    }
    override fun onServiceDisconnected(name: ComponentName?) { service = null }
  }
  private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
  private val files = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    uris.forEach { uri ->
      runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
      val title = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
      } ?: "Local track"
      startPlaybackService()
      service?.enqueue(Track(uri.toString(), title, "On this device", local = true))
    }
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      val connected = service
      val state = connected?.state?.collectAsStateWithLifecycle()?.value
      val dark = when (state?.themeMode) { 1 -> false; 2 -> true; else -> androidx.compose.foundation.isSystemInDarkTheme() }
      MusicTransiTheme(dynamicColor = false, darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          if (connected != null && state != null) {
            MusicScreen(state, connected, { files.launch(arrayOf("audio/*")) }, ::startPlaybackService)
          }
        }
      }
    }
  }
  override fun onStart() {
    super.onStart()
    if (!bound) bound = bindService(Intent(this, MixService::class.java), connection, BIND_AUTO_CREATE)
  }
  override fun onDestroy() {
    if (bound) { unbindService(connection); bound = false; service = null }
    super.onDestroy()
  }
  private fun startPlaybackService() {
    startService(Intent(this, MixService::class.java))
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
      android.content.pm.PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
  }
}
