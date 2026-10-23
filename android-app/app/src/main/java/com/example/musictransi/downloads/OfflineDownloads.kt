package com.example.musictransi.downloads

import android.app.DownloadManager
import android.app.job.*
import android.content.*
import android.net.Uri
import androidx.core.net.toUri
import android.os.Environment
import com.example.musictransi.source.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Metadata survives process death; Android owns the actual transfers and network retries. */
class OfflineDownloads(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("offline-downloads", Context.MODE_PRIVATE)
    private val manager = app.getSystemService(DownloadManager::class.java)
    data class Entry(val track: Track, val status: String, val percent: Int?, val ready: Boolean)
    private fun key(id: String) = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun file(id: String) = File(requireNotNull(app.getExternalFilesDir(Environment.DIRECTORY_MUSIC)), "${key(id)}.audio")
    private fun json(t: Track) = JSONObject().put("id", t.id).put("title", t.title).put("artist", t.artist)
        .put("duration", t.durationSeconds).put("artwork", t.artwork).put("music", t.music)
    private fun track(j: JSONObject) = Track(j.getString("id"), j.getString("title"), j.getString("artist"),
        j.optLong("duration"), artwork = j.optString("artwork"), music = j.optBoolean("music"))
    private fun records(): List<JSONObject> = prefs.all.filterKeys { it.startsWith("song:") }.values.mapNotNull {
        runCatching { JSONObject(it as String) }.getOrNull()
    }
    private fun record(id: String): JSONObject? = prefs.getString("song:${key(id)}", null)?.let(::JSONObject)
    @android.annotation.SuppressLint("UseKtx") // Need commit's success result before accepting durable work.
    private fun save(j: JSONObject) { check(prefs.edit().putString("song:${key(j.getString("id"))}", j.toString()).commit()) }

    fun entries(): List<Entry> = synchronized(lock) { records().map { j ->
        val t = track(j); val download = j.optLong("download", -1)
        if (download < 0) Entry(t, if (j.optBoolean("failed")) "Couldn't prepare download · retry" else "Waiting to prepare", null, false)
        else manager.query(DownloadManager.Query().setFilterById(download)).use { c ->
            if (!c.moveToFirst()) Entry(t, "Download missing · retry", null, false)
            else {
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val ready = status == DownloadManager.STATUS_SUCCESSFUL && file(t.id).isFile && file(t.id).length() > 0
                Entry(t, when {
                    ready -> "Available offline"
                    status == DownloadManager.STATUS_RUNNING -> "Downloading"
                    status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED -> "Waiting for network or Android"
                    else -> "Download failed or file missing · retry"
                }, if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else null, ready)
            }
        }
    }.sortedBy { it.track.title } }

    fun localAudio(t: Track): ResolvedAudio? = synchronized(lock) {
        val id = record(t.id)?.optLong("download", -1) ?: return@synchronized null
        if (id < 0) return@synchronized null
        manager.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL &&
                file(t.id).isFile && file(t.id).length() > 0) ResolvedAudio(file(t.id).toURI().toString(), artwork = t.artwork) else null
        }
    }
    fun enqueue(tracks: List<Track>, wifiOnly: Boolean) = synchronized(lock) {
        val existing = entries().associateBy { it.track.id }
        tracks.filterNot { it.local }.distinctBy { it.id }.forEach { t ->
            val status = existing[t.id]
            if (status?.ready == true || status?.status == "Downloading" || status?.status == "Waiting for network or Android" || status?.status == "Waiting to prepare") return@forEach
            record(t.id)?.optLong("download", -1)?.takeIf { it >= 0 }?.let { manager.remove(it) }
            file(t.id).delete()
            save(json(t).put("token", UUID.randomUUID().toString()).put("wifi", wifiOnly).put("download", -1))
        }
        schedule()
    }
    @android.annotation.SuppressLint("UseKtx") // Check persistence failure; KTX edit returns Unit.
    fun remove(id: String) = synchronized(lock) {
        record(id)?.optLong("download", -1)?.takeIf { it >= 0 }?.let { manager.remove(it) }
        check(prefs.edit().remove("song:${key(id)}").commit())
        file(id).delete()
        Unit
    }
    fun queued(): List<JSONObject> = synchronized(lock) { records().filter { it.optLong("download", -1) < 0 && !it.optBoolean("failed") } }
    suspend fun prepare(j: JSONObject) {
        try {
            val t = track(j)
            val source = YouTubeSource.resolve(t)
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                val current = record(t.id)
                if (current?.optString("token") != j.optString("token") || current.optLong("download", -1) >= 0) return
                require(source.url.toUri().scheme == "https")
                val request = DownloadManager.Request(source.url.toUri()).setTitle(t.title).setDescription(t.artist)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_MUSIC, "${key(t.id)}.audio")
                    .setAllowedOverRoaming(false)
                if (j.optBoolean("wifi", true)) request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                source.headers.forEach { (name, value) -> request.addRequestHeader(name, value) }
                val downloadId = manager.enqueue(request)
                try { save(j.put("download", downloadId)) }
                catch (e: Exception) { manager.remove(downloadId); throw e }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { synchronized(lock) {
            val current = record(j.getString("id"))
            if (current?.optString("token") == j.optString("token") && current.optLong("download", -1) < 0) save(j.put("failed", true))
        } }
    }
    @android.annotation.SuppressLint("UseKtx") // Check commit before reporting the offline album metadata saved.
    fun cacheAlbum(id: String, tracks: List<Track>) = synchronized(lock) {
        check(prefs.edit().putString("album:${key(id)}", JSONArray(tracks.map(::json)).toString()).commit())
    }
    fun albumTracks(id: String): List<Track> = synchronized(lock) {
        val array = JSONArray(prefs.getString("album:${key(id)}", "[]"))
        (0 until array.length()).map { track(array.getJSONObject(it)) }
    }
    private fun schedule() {
        val scheduler = app.getSystemService(JobScheduler::class.java)
        // A new user batch replaces the resolver job; submitted DownloadManager transfers continue.
        // This also avoids losing a batch added while the preceding resolver is finishing.
        if (queued().isNotEmpty()) {
            check(scheduler.schedule(JobInfo.Builder(JOB, ComponentName(app, DownloadPreparationService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                .setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build()) == JobScheduler.RESULT_SUCCESS)
        }
    }
    fun ensureScheduled() = synchronized(lock) { schedule() }
    companion object { private val lock = Any(); private const val JOB = 9041 }
}

class DownloadPreparationService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            val downloads = OfflineDownloads(this@DownloadPreparationService)
            try {
                withTimeoutOrNull(180000) {
                    while (isActive) {
                        val next = downloads.queued().firstOrNull() ?: break
                        downloads.prepare(next)
                    }
                }
                withContext(Dispatchers.Main) { jobFinished(params, downloads.queued().isNotEmpty()) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { withContext(Dispatchers.Main) { jobFinished(params, true) } }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
