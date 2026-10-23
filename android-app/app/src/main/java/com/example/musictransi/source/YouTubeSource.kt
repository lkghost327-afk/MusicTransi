package com.example.musictransi.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Uses NewPipe's maintained InnerTube implementation; no APK code is copied. */
object YouTubeSource : MusicSource {
    val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS).build()
    private val lock = Mutex() // Extractor has shared client state.
    private var initialized = false

    private fun StreamInfoItem.track() = Track(url, name, uploaderName.orEmpty(), duration,
        artwork = thumbnails.maxByOrNull { it.height }?.url.orEmpty(), music = true)

    suspend fun recommendations(seed: Track, excluded: List<Track>): List<Track> = recommendationBatch(seed, excluded, 3)
    suspend fun mixRecommendations(seed: Track, excluded: List<Track>): List<Track> = recommendationBatch(seed, excluded, 20)
    private suspend fun recommendationBatch(seed: Track, excluded: List<Track>, limit: Int): List<Track> {
        if (seed.local) return emptyList()
        val related = withContext(Dispatchers.IO) {
            lock.withLock {
                initialize()
                runCatching { MusicRadio.fetch(seed) }.getOrDefault(emptyList())
            }
        }
        val selected = RadioPolicy.select(related, excluded + seed, limit)
        if (selected.size >= limit || (limit == 3 && selected.isNotEmpty())) return selected
        val artist = seed.artist.replace(Regex("(?i)\\s*-\\s*topic$"), "").trim()
        if (artist.isBlank()) return selected
        val fallback = try { search(artist).filter { RadioPolicy.artistMatches(it.artist, artist) } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
        return RadioPolicy.select(selected + fallback, excluded + seed, limit)
    }

    private fun initialize() {
        if (initialized) return
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val builder = okhttp3.Request.Builder().url(request.url())
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
                request.headers().forEach { (name, values) ->
                    builder.removeHeader(name)
                    values.forEach { builder.addHeader(name, it) }
                }
                val bytes = request.dataToSend()
                val method = request.httpMethod()
                val body = if (method == "POST" || method == "PUT" || method == "PATCH")
                    (bytes ?: ByteArray(0)).toRequestBody() else null
                http.newCall(builder.method(method, body).build()).execute().use { response ->
                    if (response.code == 429) throw IOException("YouTube is limiting requests. Try again later.")
                    return Response(response.code, response.message, response.headers.toMultimap(),
                        response.body?.string().orEmpty(), response.request.url.toString())
                }
            }
        })
        initialized = true
    }

    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        lock.withLock {
            initialize()
            val extractor = ServiceList.YouTube.getSearchExtractor(query.trim(), listOf("music_songs"), "")
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                .filter { it.duration > 0 }
                .map { it.track() }
                .distinctBy { it.id }.take(30)
        }
    }

    override suspend fun resolve(track: Track): ResolvedAudio = withContext(Dispatchers.IO) {
        if (track.local) return@withContext ResolvedAudio(track.id)
        lock.withLock {
            initialize()
            val info = StreamInfo.getInfo(ServiceList.YouTube, track.id)
            // Progressive audio avoids video downloads and generated DASH manifests.
            val candidates = info.audioStreams.filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    it.content.startsWith("https://")
            }.sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> {
                it.format == org.schabi.newpipe.extractor.MediaFormat.M4A
            }.thenByDescending { it.averageBitrate }).distinctBy { it.content }.take(3)
            // Some advertised formats intermittently return 403. Check only a byte, then close.
            // Use the same user agent as playback; never retain or download a song for this check.
            for (audio in candidates) {
                val request = okhttp3.Request.Builder().url(audio.content).header("User-Agent", "Mozilla/5.0").build()
                val call = http.newCall(request)
                call.timeout().timeout(6, TimeUnit.SECONDS)
                val usable = runCatching { call.execute().use { response ->
                    response.isSuccessful && response.body?.byteStream()?.read() != -1 && response.body != null
                } }.getOrDefault(false)
                if (usable) return@withLock ResolvedAudio(audio.content,
                    artwork = info.thumbnails.maxByOrNull { it.height }?.url.orEmpty())
            }
            throw IOException("The provider did not supply a playable audio stream. Try again or choose another song.")
        }
    }

    suspend fun searchAlbums(query: String): List<Album> = withContext(Dispatchers.IO) {
        lock.withLock {
            initialize()
            val extractor = ServiceList.YouTube.getSearchExtractor(query.trim(), listOf("music_albums"), "")
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
                .map { Album(it.url, it.name, it.uploaderName.orEmpty(),
                    it.thumbnails.maxByOrNull { image -> image.height }?.url.orEmpty(), it.streamCount) }
                .distinctBy { it.id }.take(30)
        }
    }

    suspend fun albumTracks(album: Album, page: org.schabi.newpipe.extractor.Page? = null): AlbumPage = withContext(Dispatchers.IO) {
        lock.withLock {
            initialize()
            if (page == null) {
                val info = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(ServiceList.YouTube, album.id)
                AlbumPage(info.relatedItems.map { it.track().let { t -> t.copy(artist = t.artist.ifBlank { album.artist }, artwork = t.artwork.ifBlank { album.artwork }) } },
                    info.nextPage.takeIf { org.schabi.newpipe.extractor.Page.isValid(it) })
            } else {
                val more = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getMoreItems(ServiceList.YouTube, album.id, page)
                AlbumPage(more.items.map { it.track() }, more.nextPage.takeIf { org.schabi.newpipe.extractor.Page.isValid(it) })
            }
        }
    }
}
