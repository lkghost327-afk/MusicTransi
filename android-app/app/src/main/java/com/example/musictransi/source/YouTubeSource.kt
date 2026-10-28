package com.example.musictransi.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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
    private val recentStreams = RecentStreams()
    fun invalidateStream(track: Track?) { track?.let { song ->
        AudioQuality.entries.forEach { recentStreams.invalidate("${song.id}|${it.name}") }
    } }

    suspend fun artistPortrait(name: String): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            initialize()
            val extractor = ServiceList.YouTube.getSearchExtractor(name.trim(), listOf("music_artists"), "")
            extractor.fetchPage()
            val matches = extractor.initialPage.items.filterIsInstance<org.schabi.newpipe.extractor.channel.ChannelInfoItem>()
                .filter { RadioPolicy.artistMatches(it.name, name) }.distinctBy { it.url }
            // Homonymous artists are ambiguous: prefer a fallback to somebody else's photo.
            matches.singleOrNull()?.thumbnails?.maxByOrNull { it.height }?.url?.takeIf { it.startsWith("https://") }
        }
    }

    private fun StreamInfoItem.track() = Track(url, name, uploaderName.orEmpty(), duration,
        artwork = thumbnails.maxByOrNull { it.height }?.url.orEmpty(), music = true)

    suspend fun recommendations(seed: Track, excluded: List<Track>): List<Track> = batch(seed, excluded, RecommendationSurface.AUTOPLAY)
    suspend fun homeRecommendations(seed: Track, excluded: List<Track>): List<Track> = batch(seed, excluded, RecommendationSurface.HOME)
    suspend fun mixRecommendations(seed: Track, excluded: List<Track>): List<Track> = batch(seed, excluded, RecommendationSurface.MIX)
    private suspend fun batch(seed: Track, excluded: List<Track>, surface: RecommendationSurface) =
        recommendationBatch(seed, excluded, surface, radio = { track ->
            withContext(Dispatchers.IO) {
                // Only helper/client-state access needs the extractor lock. The immutable
                // request and response parser do not touch NewPipe's shared state.
                val request = lock.withLock { initialize(); MusicRadio.prepare(track) }
                if (request == null) emptyList() else MusicRadio.execute(request)
            }
        }, search = ::search)

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

    override suspend fun resolve(track: Track): ResolvedAudio = resolve(track, AudioQuality.HIGH)
    suspend fun resolve(track: Track, quality: AudioQuality): ResolvedAudio = withContext(Dispatchers.IO) {
        if (track.local) return@withContext ResolvedAudio(track.id)
        val cacheKey = "${track.id}|${quality.name}"
        recentStreams.get(cacheKey)?.let { return@withContext it }
        val (candidates, artwork) = lock.withLock {
            initialize()
            val info = StreamInfo.getInfo(ServiceList.YouTube, track.id)
            // Progressive audio avoids video downloads and generated DASH manifests.
            val available = info.audioStreams.filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    it.content.startsWith("https://")
            }.distinctBy { it.content }
            val candidates = quality.rank(available) { it.averageBitrate }.take(3)
            candidates.map { it.content } to info.thumbnails.maxByOrNull { it.height }?.url.orEmpty()
        }
            // Some advertised formats intermittently return 403. Check only a byte, then close.
            // Use the same user agent as playback; never retain or download a song for this check.
            for (audio in candidates) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val request = okhttp3.Request.Builder().url(audio).header("User-Agent", "Mozilla/5.0")
                    .header("Range", "bytes=0-0").build()
                val call = http.newCall(request)
                call.timeout().timeout(6, TimeUnit.SECONDS)
                val usable = runCatching { call.execute().use { response ->
                    response.isSuccessful && response.body?.byteStream()?.read() != -1 && response.body != null
                } }.getOrDefault(false)
                if (usable) return@withContext ResolvedAudio(audio, artwork = artwork).also { recentStreams.put(cacheKey, it) }
            }
            throw IOException("The provider did not supply a playable audio stream. Try again or choose another song.")
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
