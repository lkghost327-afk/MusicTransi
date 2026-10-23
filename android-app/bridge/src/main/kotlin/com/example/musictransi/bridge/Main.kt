package com.example.musictransi.bridge

import com.example.musictransi.source.*
import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.Executors

private val gson = Gson()
private data class Ticket(val audio: ResolvedAudio, val expires: Long)
private val tickets = java.util.concurrent.ConcurrentHashMap<String, Ticket>()
private val streaming = YouTubeSource.http.newBuilder().callTimeout(0, java.util.concurrent.TimeUnit.SECONDS).build()
private data class RadioRequest(val seed: Track, val excluded: List<Track> = emptyList())
private fun HttpExchange.json(status: Int, value: Any) {
    val bytes = gson.toJson(value).toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    responseHeaders.set("Cache-Control", "no-store")
    sendResponseHeaders(status, bytes.size.toLong()); responseBody.use { it.write(bytes) }
}
private fun HttpExchange.body(): String {
    val bytes = requestBody.readNBytes(262145)
    require(bytes.size <= 262144) { "Request too large" }
    return bytes.toString(Charsets.UTF_8)
}
private fun providerId(id: String): String {
    val u = URI(id)
    require(u.scheme == "https" && u.host in setOf("www.youtube.com", "youtube.com", "music.youtube.com", "youtu.be"))
    return id
}

fun main() {
    val token = System.getenv("MUSICTRANSI_TOKEN") ?: error("Set MUSICTRANSI_TOKEN to a private random value (at least 24 characters).")
    require(token.length >= 24)
    val host = System.getenv("MUSICTRANSI_HOST") ?: "127.0.0.1"
    val port = System.getenv("MUSICTRANSI_PORT")?.toInt() ?: 8787
    val server = HttpServer.create(InetSocketAddress(host, port), 16)
    server.executor = Executors.newFixedThreadPool(8)
    server.createContext("/") { e ->
        try {
            val path = e.requestURI.path
            val query = e.requestURI.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate {
                val pair = it.split('=', limit = 2)
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
            }
            if (path == "/audio" && e.requestMethod in setOf("GET", "HEAD")) {
                val ticket = query["ticket"]?.let { tickets[it] }
                if (ticket == null || ticket.expires < System.currentTimeMillis()) { e.json(401, mapOf("error" to "Audio ticket expired")); return@createContext }
                val builder = Request.Builder().url(ticket.audio.url).header("User-Agent", "Mozilla/5.0")
                e.requestHeaders.getFirst("Range")?.takeIf { it.matches(Regex("bytes=\\d*-\\d*")) }?.let { builder.header("Range", it) }
                if (e.requestMethod == "HEAD") builder.head()
                streaming.newCall(builder.build()).execute().use { response ->
                    for (key in listOf("Content-Type", "Content-Range", "Accept-Ranges", "Content-Length")) response.header(key)?.let { e.responseHeaders.set(key, it) }
                    e.responseHeaders.set("Cache-Control", "no-store")
                    val body = response.body
                    e.sendResponseHeaders(response.code, if (e.requestMethod == "HEAD") -1 else body?.contentLength()?.takeIf { it > 0 } ?: 0)
                    if (e.requestMethod != "HEAD") e.responseBody.use { out -> body?.byteStream()?.use { it.copyTo(out, 32768) } }
                }
                return@createContext
            }
            val supplied = e.requestHeaders.getFirst("Authorization").orEmpty().toByteArray()
            if (!java.security.MessageDigest.isEqual(supplied, "Bearer $token".toByteArray())) { e.json(401, mapOf("error" to "Invalid bridge token")); return@createContext }
            val value: Any = runBlocking {
                when {
                    path == "/health" && e.requestMethod == "GET" -> mapOf("status" to "ok", "version" to "0.5")
                    path == "/search" && e.requestMethod == "GET" -> {
                        val q = query["q"].orEmpty().trim(); require(q.isNotEmpty() && q.length <= 200)
                        if (query["type"] == "albums") YouTubeSource.searchAlbums(q) else YouTubeSource.search(q)
                    }
                    path == "/album" && e.requestMethod == "GET" -> {
                        val album = Album(providerId(query["id"].orEmpty()), "", "")
                        var page = YouTubeSource.albumTracks(album); val result = page.tracks.toMutableList(); var count = 1
                        while (page.next != null) { check(count++ < 50); page = YouTubeSource.albumTracks(album, page.next); result += page.tracks }
                        result.distinctBy { it.id }
                    }
                    path == "/radio" && e.requestMethod == "POST" -> {
                        val request = gson.fromJson(e.body(), RadioRequest::class.java)
                        providerId(request.seed.id); require(request.excluded.size <= 500)
                        YouTubeSource.recommendations(request.seed, request.excluded)
                    }
                    path == "/prepare" && e.requestMethod == "POST" -> {
                        val track = gson.fromJson(e.body(), Track::class.java)
                        providerId(track.id); require(!track.local)
                        val audio = YouTubeSource.resolve(track)
                        val uri = URI(audio.url)
                        require(uri.scheme == "https" && uri.host.endsWith(".googlevideo.com"))
                        tickets.entries.removeIf { it.value.expires < System.currentTimeMillis() }
                        check(tickets.size < 64) { "Too many prepared tracks" }
                        val id = UUID.randomUUID().toString()
                        tickets[id] = Ticket(audio, System.currentTimeMillis() + 20 * 60 * 1000)
                        mapOf("path" to "/audio?ticket=$id", "artwork" to audio.artwork)
                    }
                    else -> throw IllegalArgumentException("Unknown route or method")
                }
            }
            e.json(200, value)
        } catch (_: IllegalArgumentException) { runCatching { e.json(400, mapOf("error" to "Invalid request")) } }
        catch (error: Exception) {
            System.err.println("Source request failed: ${error.javaClass.simpleName} at ${error.stackTrace.firstOrNull()}")
            runCatching { e.json(502, mapOf("error" to "Source unavailable. Check the bridge connection or try another song.")) }
        }
        finally { e.close() }
    }
    server.start()
    println("MusicTransi private bridge listening on $host:$port. Keep this process running for iPhone online playback.")
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(1) })
}
