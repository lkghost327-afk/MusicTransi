package com.example.musictransi.playback

import android.app.*
import androidx.core.graphics.scale
import android.content.*
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.audiofx.HapticGenerator
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.musictransi.MainActivity
import com.example.musictransi.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class MixState(
    val queue: List<Track> = emptyList(), val index: Int = -1,
    val playing: Boolean = false, val loading: Boolean = false,
    val positionMs: Long = 0, val durationMs: Long = 0,
    val blend: Float = 0f, val crossfadeSeconds: Int = 8,
    val haptics: Boolean = false, val hapticsSupported: Boolean = false,
    val message: String? = null,
    val autoplay: Boolean = true, val findingRelated: Boolean = false,
    val bassBlend: Boolean = true,
    val normalize: Boolean = true, val playerStyle: Int = 0,
    val hapticAdvanceMs: Int = 35,
    val hapticIntensity: Float = .65f, val hapticStatus: String = "",
    val mixStatus: String = "",
    val themeMode: Int = 0,
    val glassAppearance: Boolean = false,
) { val current: Track? get() = queue.getOrNull(index) }

/** Owns both decks independently of the Activity. All player state stays on the main thread. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MixService : Service() {
    val searchHistory by lazy { com.example.musictransi.library.ListeningHistory(applicationContext, "search-selections", 30) }
    val listeningHistory by lazy { com.example.musictransi.library.ListeningHistory(applicationContext) }
    private val listeningProgress = com.example.musictransi.library.ListeningProgress()
    val library by lazy { com.example.musictransi.library.LibraryStore(applicationContext) }
    inner class LocalBinder : Binder() { val service get() = this@MixService }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(MixState())
    val state = mutable.asStateFlow()
    private lateinit var active: ExoPlayer
    private lateinit var standby: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private lateinit var vibration: MusicVibration
    private var nativeHaptics = false
    private var activeProbe = AudioProbe()
    private var standbyProbe = AudioProbe()
    private var activeLevel = 1f
    private var standbyLevel = 1f
    private val rejected = mutableListOf<Track>()
    private var activeHaptic: HapticGenerator? = null
    private var standbyHaptic: HapticGenerator? = null
    private var loadJob: Job? = null
    private var preloadJob: Job? = null
    private var preloadIndex = -1
    private var generation = 0
    private var blending = false
    private var blendDurationMs = 1L
    private var blendStartPosition = 0L
    private var incomingStarted = false
    private var transitionOut: BeatGrid.Profile? = null
    private var transitionIn: BeatGrid.Profile? = null
    private var lastPhaseCheck = -1L
    private var analyzingNext = false
    private var incomingStartMs = 0L
    private var scheduledBeatMs = -1L
    private var beatSpeed = 1f
    private var speedReleaseStart = -1L
    private var resumeAfterFocus = false
    private var foreground = false
    private var radioJob: Job? = null
    private var radioSeed: String? = null
    private var continueRadio = false
    private var activeBass = BassBlend()
    private var standbyBass = BassBlend()
    private var lastPublishedSecond = -1L
    private var notificationArtwork: android.graphics.Bitmap? = null
    private var notificationArtworkKey: String? = null
    private var notificationArtworkJob: Job? = null
    private var publishedMetadata: Triple<Track?, Long, android.graphics.Bitmap?>? = null
    private val prefs by lazy { getSharedPreferences("session", MODE_PRIVATE) }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { pause() }
    }

    override fun onBind(intent: Intent) = LocalBinder()
    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterFocus) { resumeAfterFocus = false; play() }
                    AudioManager.AUDIOFOCUS_LOSS -> { pause(); resumeAfterFocus = false }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        val wasPlaying = mutable.value.playing
                        pause(abandon = false)
                        resumeAfterFocus = wasPlaying
                    }
                }
            }.build()
        vibration = MusicVibration(this)
        active = makePlayer(activeProbe)
        standby = makePlayer(standbyProbe)
        session = MediaSession(this, "MusicTransi").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = play()
                override fun onPause() = pause()
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onSeekTo(pos: Long) = seek(pos)
                override fun onStop() = stopPlayback()
                override fun onCustomAction(action: String, extras: Bundle?) { if (action == "like") toggleNotificationLike() }
            })
            isActive = false
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Music playback", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        else registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        val supported = Build.VERSION.SDK_INT >= 31 && runCatching { HapticGenerator.isAvailable() }.getOrDefault(false)
        nativeHaptics = supported
        mutable.value = restore().copy(hapticsSupported = supported || vibration.available,
            hapticStatus = if (supported) "Native audio haptics available" else if (vibration.available)
                "Audio pulse mode · Android ${Build.VERSION.RELEASE}" else "No vibration motor detected")
        runCatching {
            val blocked = JSONArray(prefs.getString("rejected", "[]"))
            repeat(blocked.length()) { i -> blocked.getJSONObject(i).let {
                rejected += Track(it.getString("id"), it.getString("title"), it.getString("artist"))
            } }
        }
        scope.launch {
            var previousLikes: Set<String>? = null
            library.state.collect { saved ->
                val likes = saved.likes.map { it.id }.toSet()
                if (likes != previousLikes) { previousLikes = likes; if (session.isActive) publish() }
            }
        }
        scope.launch {
            while (isActive) {
                tick()
                delay(if (mutable.value.playing) 40 else 1000)
            }
        }
        scope.launch {
            while (isActive) {
                val s = mutable.value
                if (s.haptics && !nativeHaptics && s.playing && active.isPlaying) {
                    val activePulse = activeProbe.pulseAt((active.currentPosition + s.hapticAdvanceMs * active.playbackParameters.speed).toLong())
                    val incomingPulse = if (standby.isPlaying) standbyProbe.pulseAt((standby.currentPosition + s.hapticAdvanceMs * standby.playbackParameters.speed).toLong()) else 0f
                    vibration.pulse(maxOf(activePulse * (1 - s.blend), incomingPulse * s.blend), s.hapticIntensity)
                    delay(16)
                } else delay(100)
            }
        }
    }

    private fun makePlayer(probe: AudioProbe): ExoPlayer = ExoPlayer.Builder(this,
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(probe)).build()
        })
        .setLoadControl(DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 30000, 1000, 2000).build())
        .build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setHapticChannelsMuted(false).build(), false)
            setWakeMode(C.WAKE_MODE_NETWORK)
            volume = MixMath.HEADROOM
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (this@apply === active) {
                        pause()
                        update { copy(loading = false, message = "Playback failed. Tap Play to resolve the stream again.") }
                        active.clearMediaItems()
                    } else {
                        cancelBlend()
                        update { copy(message = "The next track could not buffer. Tap Next to retry.") }
                    }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (this@apply === active) {
                        update { copy(loading = playbackState == Player.STATE_BUFFERING) }
                        if (playbackState == Player.STATE_ENDED && !blending) next()
                    }
                }
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    refreshHaptics()
                    if (::active.isInitialized && ::standby.isInitialized) {
                        activeBass.attach(active.audioSessionId)
                        standbyBass.attach(standby.audioSessionId)
                    }
                }
            })
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "pause" -> pause()
            "next" -> next()
            "previous" -> previous()
            "like" -> toggleNotificationLike()
            "stop" -> stopPlayback()
            "play" -> play()
        }
        return START_NOT_STICKY // Never restart sound unexpectedly after process death.
    }

    fun enqueue(track: Track, playNow: Boolean = false) {
        val placement = QueuePolicy.insert(mutable.value.queue, mutable.value.index, track, playNow, blending)
        if (playNow || (!blending && placement.index == mutable.value.index + 1)) cancelBlend()
        update { copy(queue = placement.queue, message = null) }
        persist()
        if (playNow || mutable.value.index < 0) load(placement.index)
        else prepareNext()
    }

    fun setAutoplay(enabled: Boolean) {
        radioJob?.cancel(); radioJob = null; radioSeed = null; continueRadio = false
        update { copy(autoplay = enabled, findingRelated = false, message = null) }
        if (!enabled) {
            val keepThrough = mutable.value.index + if (blending) 1 else 0
            if (!blending) cancelBlend()
            update { copy(queue = queue.filterIndexed { i, track -> i <= keepThrough || !track.automatic }) }
            prepareNext()
        } else ensureRadio()
        persist()
    }

    fun playNext(track: Track) {
        val s = mutable.value
        if (s.index < 0) { enqueue(track); return }
        val target = QueuePolicy.nextSlot(s.index, blending).coerceAtMost(s.queue.size)
        if (!blending) cancelBlend()
        update { copy(queue = queue.toMutableList().apply { add(target, track.copy(automatic = false)) },
            message = if (blending) "Added after the transition already playing." else "Added to play next.") }
        persist(); prepareNext()
    }

    fun moveQueued(from: Int, to: Int) {
        val s = mutable.value
        val moved = QueuePolicy.move(s.queue, s.index, from, to, blending)
        if (moved == s.queue) return
        if (!blending) cancelBlend()
        update { copy(queue = moved) }; persist(); prepareNext()
    }

    fun shuffleUpcoming() {
        val s = mutable.value
        val first = QueuePolicy.nextSlot(s.index, blending).coerceIn(0, s.queue.size)
        if (!blending) cancelBlend()
        update { copy(queue = queue.take(first) + queue.drop(first).shuffled().map { it.copy(automatic = false) }) }
        persist(); prepareNext()
    }

    fun clearUpcoming() {
        radioJob?.cancel(); radioJob = null; radioSeed = null; continueRadio = false
        val keep = QueuePolicy.nextSlot(mutable.value.index, blending).coerceAtLeast(0)
        if (!blending) cancelBlend()
        update { copy(queue = queue.take(keep), autoplay = false, findingRelated = false,
            message = "Upcoming songs cleared. Autoplay is off.") }
        persist()
    }

    fun playCollection(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.copy(automatic = false) }.let { if (shuffle) it.shuffled() else it }
        update { copy(queue = items) }
        load(if (shuffle) 0 else startIndex.coerceIn(items.indices))
    }

    fun enqueueCollection(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (mutable.value.index < 0) { playCollection(tracks); return }
        if (!blending) cancelBlend()
        val s = mutable.value
        val queue = tracks.fold(s.queue) { items, track ->
            QueuePolicy.insert(items, s.index, track.copy(automatic = false), false, blending).queue
        }
        update { copy(queue = queue) }
        persist(); prepareNext()
    }

    fun retryRadio() { radioSeed = null; ensureRadio() }

    private fun ensureRadio() {
        val s = mutable.value
        val seed = s.current ?: return
        if (!s.autoplay || seed.local || s.queue.size - s.index > 3 ||
            radioJob?.isActive == true || radioSeed == seed.id) return
        radioSeed = seed.id
        val token = generation
        update { copy(findingRelated = true) }
        radioJob = scope.launch {
            try {
                val recentHistory = listeningHistory.tracks.value.take(30)
                val picks = YouTubeSource.recommendations(seed, recentHistory + rejected + mutable.value.queue)
                ensureActive()
                if (token != generation || !mutable.value.autoplay) return@launch
                val fresh = RadioPolicy.select(picks, listeningHistory.tracks.value.take(30) + rejected + mutable.value.queue)
                update { copy(queue = queue + fresh, message = if (fresh.isEmpty())
                    "No new related songs found. Try another song or retry Autoplay." else null) }
                persist(); prepareNext()
                if (continueRadio && fresh.isNotEmpty()) { continueRadio = false; next() }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                update { copy(message = "Autoplay could not find more songs. Check your connection and retry.") }
            } finally {
                if (token == generation) {
                    update { copy(findingRelated = false) }
                    if (continueRadio && !mutable.value.playing) { continueRadio = false; pause() }
                }
            }
        }
    }

    fun setBassBlend(enabled: Boolean) {
        update { copy(bassBlend = enabled) }
        if (!enabled) { activeBass.gain(0f); standbyBass.gain(0f) }
        persist()
    }

    fun setNormalize(enabled: Boolean) { update { copy(normalize = enabled) }; persist() }
    fun setPlayerStyle(style: Int) { update { copy(playerStyle = style.coerceIn(0, 4)) }; persist() }
    fun setGlassAppearance(enabled: Boolean) { update { copy(glassAppearance = enabled) }; persist() }
    fun setHapticAdvance(ms: Int) { update { copy(hapticAdvanceMs = ms.coerceIn(-150, 150)) }; persist() }
    fun setThemeMode(mode: Int) { update { copy(themeMode = mode.coerceIn(0, 2)) }; persist() }
    fun setHapticIntensity(value: Float) { update { copy(hapticIntensity = value.coerceIn(.2f, 1f)) }; persist() }
    fun testVibration() {
        val success = vibration.pulse(1f, mutable.value.hapticIntensity, test = true)
        update { copy(hapticStatus = if (success) "Test sent. If silent, check phone vibration settings."
            else "Vibration unavailable or blocked on this device.") }
    }
    fun notInterested() {
        val track = mutable.value.current ?: return
        rejected += track
        while (rejected.size > 200) rejected.removeAt(0)
        cancelBlend()
        update { copy(queue = queue.filterIndexed { i, item -> i <= index ||
            RadioPolicy.songKey(item) != RadioPolicy.songKey(track) }) }
        persist()
        radioSeed = null
        next()
    }

    fun playAt(index: Int) = load(index)

    fun removeQueued(index: Int) {
        if (index == mutable.value.index || index !in mutable.value.queue.indices || blending) return
        cancelBlend()
        update { copy(queue = queue.filterIndexed { i, _ -> i != index },
            index = if (index < this.index) this.index - 1 else this.index) }
        persist(); prepareNext()
    }

    fun clearQueue() {
        stopPlayback()
        update { copy(queue = emptyList(), index = -1, durationMs = 0, message = null) }
        persist()
    }

    private fun load(index: Int) {
        val track = mutable.value.queue.getOrNull(index) ?: return
        listeningProgress.reset()
        generation++
        radioJob?.cancel(); radioJob = null; radioSeed = null; continueRadio = false
        val token = generation
        loadJob?.cancel()
        cancelBlend()
        active.stop()
        active.clearMediaItems()
        update { copy(index = index, positionMs = 0, durationMs = 0, loading = true, playing = true, findingRelated = false, message = null) }
        promoteForeground()
        if (!takeFocus()) return
        persist()
        loadJob = scope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) { com.example.musictransi.downloads.OfflineDownloads(this@MixService).localAudio(track) } ?: YouTubeSource.resolve(track)
                ensureActive()
                if (token != generation) return@launch
                refreshArtwork(track.id, resolved.artwork)
                setSource(active, resolved)
                active.playWhenReady = mutable.value.playing
                prepareNext()
                ensureRadio()
                publish()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == generation) {
                    pause()
                    update { copy(loading = false, message = "Unable to load this song. Check your connection or try another track.") }
                }
            }
        }
    }

    private fun setSource(player: ExoPlayer, source: ResolvedAudio) {
        player.playbackParameters = PlaybackParameters.DEFAULT
        if (player === active) { activeProbe.resetTrack(); activeLevel = 1f }
        else { standbyProbe.resetTrack(); standbyLevel = 1f }
        val http = OkHttpDataSource.Factory(YouTubeSource.http)
            .setUserAgent("Mozilla/5.0").setDefaultRequestProperties(source.headers)
        val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))
        player.setMediaSource(factory.createMediaSource(MediaItem.fromUri(source.url)))
        player.prepare()
    }

    private fun prepareNext() {
        val target = mutable.value.index + 1
        val track = mutable.value.queue.getOrNull(target) ?: return
        if (target == preloadIndex) return
        preloadJob?.cancel()
        preloadIndex = target
        val token = generation
        preloadJob = scope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) { com.example.musictransi.downloads.OfflineDownloads(this@MixService).localAudio(track) } ?: YouTubeSource.resolve(track)
                ensureActive()
                if (token != generation || target != preloadIndex) return@launch
                refreshArtwork(track.id, resolved.artwork)
                standby.playWhenReady = false
                standby.volume = 0f
                setSource(standby, resolved)
                withTimeoutOrNull(5000) { while (active.duration <= 0) delay(100) }
                ensureActive()
                // Analyze only one short head while the current song is playing. Volume stays zero.
                if (mutable.value.crossfadeSeconds > 0 && active.duration - active.currentPosition > 30000) {
                    analyzingNext = true
                    update { copy(mixStatus = "Listening for beats…") }
                    try {
                        withTimeoutOrNull(25000) {
                            while (standby.currentPosition < 16000 && standby.playbackState != Player.STATE_ENDED) {
                                standby.playWhenReady = mutable.value.playing && active.isPlaying
                                delay(100)
                            }
                        }
                        standby.pause()
                        val profile = standbyProbe.beatProfile()
                        incomingStartMs = profile?.nextBeat(0)?.coerceIn(0, 900) ?: 0
                        standbyProbe.originMs = incomingStartMs
                        standby.seekTo(incomingStartMs)
                        update { copy(mixStatus = if (profile != null) "Next · ${profile.bpm.toInt()} BPM" else "Gentle blend ready") }
                    } finally { analyzingNext = false }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                // Keep target marked: do not hammer a failing provider every tick.
                update { copy(message = "Next track is unavailable. Tap Next to retry or choose another song.") }
            }
        }
    }

    private fun tick() {
        val s = mutable.value
        if (listeningProgress.observe(s.playing && active.isPlaying, SystemClock.elapsedRealtime())) {
            s.current?.let { track -> scope.launch {
                try { listeningHistory.record(track) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Playback continues if local history storage is unavailable. */ }
            } }
        }
        val duration = active.duration.coerceAtLeast(0)
        val position = active.currentPosition.coerceAtLeast(0)
        activeLevel += ((if (s.normalize) activeProbe.normalization else 1f) - activeLevel) * .06f
        standbyLevel += ((if (s.normalize) standbyProbe.normalization else 1f) - standbyLevel) * .06f
        if (!blending) active.volume = MixMath.HEADROOM * activeLevel
        if (!blending && speedReleaseStart >= 0 && active.isPlaying) {
            val progress = ((position - speedReleaseStart) / 8000f).coerceIn(0f, 1f)
            active.playbackParameters = PlaybackParameters(beatSpeed + (1f - beatSpeed) * progress, 1f)
            if (progress >= 1f) speedReleaseStart = -1
        }

        if (s.playing && active.isPlaying && !blending && s.crossfadeSeconds > 0 &&
            !analyzingNext && standby.playbackState == Player.STATE_READY && preloadIndex == s.index + 1) {
            val overlap = MixMath.duration(s.crossfadeSeconds * 1000L, duration, standby.duration)
            if (overlap >= 1000 && duration - position in 1..overlap && scheduledBeatMs < 0) {
                val outgoing = activeProbe.beatProfile(position)
                val incoming = standbyProbe.beatProfile()
                val speed = BeatGrid.speed(outgoing, incoming)
                transitionOut = if (speed != null) outgoing else null
                transitionIn = if (speed != null) incoming else null
                lastPhaseCheck = -1
                beatSpeed = speed ?: 1f
                standby.playbackParameters = PlaybackParameters(beatSpeed, 1f)
                scheduledBeatMs = if (speed != null) outgoing!!.nextBeat(position) else position
                update { copy(mixStatus = if (speed != null) "Beat sync · ${outgoing!!.bpm.toInt()} BPM" else "Gentle blend") }
            }
            if (scheduledBeatMs >= 0 && position >= scheduledBeatMs && duration - position > 0) {
                blending = true
                incomingStarted = false
                blendStartPosition = position
                blendDurationMs = (duration - position).coerceAtLeast(1)
                if (s.bassBlend) standbyBass.gain(-6f)
                refreshHaptics()
                standby.play()
            }
        }
        if (blending && s.playing) {
            // Playback position, not wall time: pauses and buffering don't advance the fade.
            if (active.playbackState == Player.STATE_BUFFERING) {
                standby.pause() // Keep the incoming beat from running ahead of a stalled outgoing deck.
            } else if (standby.playbackState == Player.STATE_BUFFERING) {
                val ended = active.playbackState == Player.STATE_ENDED
                cancelBlend()
                update { copy(message = "Connection slowed; keeping the current track at full level.") }
                if (ended) next()
            } else {
                if (!standby.playWhenReady) standby.play()
                if (!standby.isPlaying && !(incomingStarted && active.playbackState == Player.STATE_ENDED)) return
                if (!incomingStarted) {
                    incomingStarted = true
                    // Two independent sinks are not sample-synchronous; reject large startup delays.
                    val elapsed = position - blendStartPosition
                    if (mutable.value.mixStatus.startsWith("Beat sync") && elapsed > 120) {
                        transitionOut = null; transitionIn = null
                        beatSpeed = 1f
                        standby.playbackParameters = PlaybackParameters.DEFAULT
                        update { copy(mixStatus = "Smooth blend · timing uncertain") }
                    }
                    blendStartPosition = position
                    blendDurationMs = (duration - position).coerceAtLeast(1)
                }
                // One master timeline ends both gain envelopes together, even after late startup.
                val progress = TransitionMath.progress(position, blendStartPosition, blendDurationMs)
                val outGrid = transitionOut; val inGrid = transitionIn
                if (outGrid != null && inGrid != null && position - lastPhaseCheck >= 300) {
                    lastPhaseCheck = position
                    val error = TransitionMath.phaseError(outGrid, inGrid, position, standby.currentPosition)
                    if (kotlin.math.abs(error) > 90) {
                        // Never chase a substantially wrong grid with audible speed swings.
                        transitionOut = null; transitionIn = null
                        beatSpeed = 1f; standby.playbackParameters = PlaybackParameters.DEFAULT
                        update { copy(mixStatus = "Smooth blend · beat phase uncertain") }
                    } else {
                        standby.playbackParameters = PlaybackParameters(TransitionMath.correctedSpeed(beatSpeed, error), 1f)
                    }
                }
                val gains = MixMath.gains(progress)
                if (s.bassBlend) {
                    val bass = TransitionMath.bass(progress)
                    activeBass.gain(bass.first)
                    standbyBass.gain(bass.second)
                }
                active.volume = gains.first * activeLevel
                standby.volume = gains.second * standbyLevel
                update { copy(blend = progress) }
                if (progress >= 1f || active.playbackState == Player.STATE_ENDED) promoteStandby()
            }
        }
        update { copy(positionMs = active.currentPosition.coerceAtLeast(0),
            durationMs = active.duration.coerceAtLeast(0)) }
        if (position / 1000 != lastPublishedSecond) {
            lastPublishedSecond = position / 1000
            publish(updateNotification = false)
        }
    }

    private fun promoteStandby() {
        listeningProgress.reset()
        beatSpeed = standby.playbackParameters.speed
        val old = active
        active = standby
        standby = old
        val oldProbe = activeProbe; activeProbe = standbyProbe; standbyProbe = oldProbe
        activeLevel = standbyLevel; standbyLevel = 1f
        val oldBass = activeBass; activeBass = standbyBass; standbyBass = oldBass
        activeBass.gain(0f); standbyBass.gain(0f)
        blending = false
        incomingStarted = false
        speedReleaseStart = active.currentPosition
        scheduledBeatMs = -1
        incomingStartMs = 0
        standby.stop()
        standby.clearMediaItems()
        active.volume = MixMath.HEADROOM * activeLevel
        update { copy(index = index + 1, blend = 0f, loading = false, message = null) }
        preloadIndex = -1
        refreshHaptics()
        persist()
        prepareNext()
        ensureRadio()
        publish()
    }

    fun play() {
        resumeAfterFocus = false
        if (mutable.value.current == null) return
        if (active.mediaItemCount == 0 || active.playbackState == Player.STATE_ENDED) {
            load(mutable.value.index); return
        }
        promoteForeground()
        if (!takeFocus()) return
        update { copy(playing = true, message = null) }
        active.play()
        if (blending) standby.play()
        publish()
    }

    fun pause(abandon: Boolean = true) {
        vibration.cancel()
        continueRadio = false
        resumeAfterFocus = false
        active.pause()
        standby.pause()
        update { copy(playing = false, positionMs = active.currentPosition.coerceAtLeast(0)) }
        if (abandon) audio.abandonAudioFocusRequest(focus)
        publish()
        if (foreground && abandon) { stopForeground(STOP_FOREGROUND_DETACH); foreground = false }
    }

    fun next() {
        if (mutable.value.queue.getOrNull(mutable.value.index + 1) == null) {
            val waiting = mutable.value.autoplay && mutable.value.current?.local == false &&
                (radioJob?.isActive == true || radioSeed != mutable.value.current?.id)
            pause(abandon = !waiting)
            continueRadio = waiting
            ensureRadio()
            return
        }
        if (!analyzingNext && preloadIndex == mutable.value.index + 1 && standby.playbackState == Player.STATE_READY) {
            promoteForeground()
            if (!takeFocus()) return
            update { copy(playing = true) }
            standby.play()
            promoteStandby()
        } else load(mutable.value.index + 1)
    }

    fun previous() {
        if (active.currentPosition > 3000) seek(0)
        else load((mutable.value.index - 1).coerceAtLeast(0))
    }

    fun seek(position: Long) {
        cancelBlend()
        activeProbe.originMs = position.coerceIn(0, active.duration.coerceAtLeast(0))
        activeProbe.clearBeats()
        vibration.cancel()
        active.seekTo(position.coerceIn(0, active.duration.coerceAtLeast(0)))
        prepareNext()
        publish()
    }

    fun setCrossfade(seconds: Int) {
        cancelBlend()
        update { copy(crossfadeSeconds = seconds.coerceIn(0, 16)) }
        persist(); prepareNext()
    }

    fun setHaptics(enabled: Boolean) {
        update { copy(haptics = enabled && hapticsSupported) }
        refreshHaptics()
        if (!enabled) vibration.cancel()
        persist()
    }

    private fun refreshHaptics() {
        if (Build.VERSION.SDK_INT < 31 || !::active.isInitialized || !::standby.isInitialized) return
        activeHaptic?.release(); standbyHaptic?.release()
        activeHaptic = null; standbyHaptic = null
        if (!mutable.value.haptics || !nativeHaptics) return
        try {
            if (active.audioSessionId > 0)
                activeHaptic = HapticGenerator.create(active.audioSessionId)?.also {
                    check(it.setEnabled(true) == android.media.audiofx.AudioEffect.SUCCESS)
                }
            if (blending && standby.audioSessionId > 0)
                standbyHaptic = HapticGenerator.create(standby.audioSessionId)?.also {
                    check(it.setEnabled(true) == android.media.audiofx.AudioEffect.SUCCESS)
                }
        } catch (_: Exception) {
            activeHaptic?.release(); standbyHaptic?.release()
            activeHaptic = null; standbyHaptic = null
            nativeHaptics = false
            update { copy(haptics = vibration.available, hapticStatus = "Using audio pulse mode on this output.") }
        }
    }

    private fun cancelBlend() {
        transitionOut = null; transitionIn = null; lastPhaseCheck = -1
        preloadJob?.cancel()
        analyzingNext = false
        scheduledBeatMs = -1
        incomingStartMs = 0
        speedReleaseStart = -1
        beatSpeed = 1f
        active.playbackParameters = PlaybackParameters.DEFAULT
        preloadIndex = -1
        blending = false
        incomingStarted = false
        standby.stop(); standby.clearMediaItems()
        active.volume = MixMath.HEADROOM
        activeBass.gain(0f); standbyBass.gain(0f)
        update { copy(blend = 0f, mixStatus = "") }
        refreshHaptics()
    }

    private fun takeFocus(): Boolean {
        if (audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return true
        pause()
        update { copy(loading = false, message = "Another app has audio focus. Tap Play when it finishes.") }
        return false
    }

    private fun promoteForeground() {
        session.isActive = true
        startForeground(NOTIFICATION, notification())
        foreground = true
    }

    private fun notification(): Notification {
        val s = mutable.value
        fun action(name: String) = PendingIntent.getService(this, name.hashCode(),
            Intent(this, MixService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(com.example.musictransi.R.drawable.ic_music)
            .setContentTitle(s.current?.title ?: "MusicTransi")
            .setContentText(s.current?.artist ?: "Your continuous mix")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(s.playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setLargeIcon(notificationArtwork)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, com.example.musictransi.R.drawable.ic_previous), "Previous", action("previous")).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, if (s.playing) com.example.musictransi.R.drawable.ic_pause else com.example.musictransi.R.drawable.ic_play), if (s.playing) "Pause" else "Play",
                action(if (s.playing) "pause" else "play")).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, com.example.musictransi.R.drawable.ic_next), "Next", action("next")).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, likeIcon()), if (currentLiked()) "Unlike" else "Like", action("like")).build())
            .setDeleteIntent(action("stop"))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun publish(updateNotification: Boolean = true) {
        if (!::session.isInitialized || !session.isActive) return
        val s = mutable.value
        val artworkKey = s.current?.let { "${it.id}|${it.artwork}" }
        if (artworkKey != notificationArtworkKey) {
            notificationArtworkKey = artworkKey; notificationArtwork = null; notificationArtworkJob?.cancel()
            s.current?.let { track -> notificationArtworkJob = scope.launch {
                val cover = com.example.musictransi.ui.loadCoverBitmap(applicationContext, track.artwork, track.id)
                if (notificationArtworkKey == artworkKey && session.isActive) {
                    notificationArtwork = cover?.let {
                        val ratio = 256f / maxOf(it.width, it.height)
                        if (ratio < 1f) it.scale((it.width * ratio).toInt().coerceAtLeast(1), (it.height * ratio).toInt().coerceAtLeast(1), true) else it
                    }
                    publish()
                }
            } }
        }
        val metadata = Triple(s.current, s.durationMs, notificationArtwork)
        if (publishedMetadata != metadata) {
        publishedMetadata = metadata
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, s.current?.title.orEmpty())
            .putString(MediaMetadata.METADATA_KEY_ARTIST, s.current?.artist.orEmpty())
            .putLong(MediaMetadata.METADATA_KEY_DURATION, s.durationMs)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, notificationArtwork)
            .putBitmap(MediaMetadata.METADATA_KEY_ART, notificationArtwork).build())
        }
        session.setPlaybackState(PlaybackState.Builder().setActions(
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP)
            .addCustomAction(PlaybackState.CustomAction.Builder("like", if (currentLiked()) "Unlike" else "Like", likeIcon()).build())
            .setState(if (s.loading && s.playing) PlaybackState.STATE_BUFFERING else if (s.playing)
                PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, active.currentPosition, if (s.playing) 1f else 0f).build())
        if (updateNotification && s.current != null)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }

    private fun currentLiked() = library.state.value.likes.any { it.id == mutable.value.current?.id }
    private fun likeIcon() = if (currentLiked()) com.example.musictransi.R.drawable.ic_like else com.example.musictransi.R.drawable.ic_unlike
    private fun toggleNotificationLike() {
        val track = mutable.value.current ?: return
        scope.launch {
            try { library.toggleLike(track); publish() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { update { copy(message = "Couldn't update liked songs. Please try again.") } }
        }
    }

    fun stopPlayback() {
        radioJob?.cancel(); radioJob = null; radioSeed = null; continueRadio = false
        generation++; loadJob?.cancel(); cancelBlend(); active.stop(); active.clearMediaItems()
        pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        session.isActive = false
        notificationArtworkJob?.cancel(); notificationArtworkKey = null; notificationArtwork = null
        update { copy(positionMs = 0, loading = false, findingRelated = false) }
        stopSelf()
    }

    private inline fun update(block: MixState.() -> MixState) { mutable.value = mutable.value.block() }
    private fun refreshArtwork(id: String, artwork: String) {
        if (artwork.isBlank()) return
        update { copy(queue = queue.map { if (it.id == id) it.copy(artwork = artwork) else it }) }
        persist()
    }
    private fun persist() {
        val s = mutable.value
        val array = JSONArray()
        s.queue.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title)
            .put("artist", it.artist).put("duration", it.durationSeconds).put("local", it.local)
            .put("artwork", it.artwork).put("automatic", it.automatic).put("music", it.music)) }
        val blocked = JSONArray()
        rejected.forEach { blocked.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist)) }
        prefs.edit().putString("queue", array.toString()).putInt("index", s.index)
            .putInt("crossfade", s.crossfadeSeconds).putBoolean("autoplay", s.autoplay)
            .putBoolean("bassBlend", s.bassBlend).putBoolean("normalize", s.normalize)
            .putInt("hapticAdvance", s.hapticAdvanceMs).putBoolean("haptics", s.haptics).putFloat("hapticIntensity", s.hapticIntensity)
            .putInt("playerStyle", s.playerStyle).putInt("themeMode", s.themeMode).putBoolean("glassAppearance", s.glassAppearance).putString("rejected", blocked.toString()).apply()
    }
    private fun restore(): MixState = runCatching {
        val array = JSONArray(prefs.getString("queue", "[]"))
        val queue = (0 until array.length()).map { i -> array.getJSONObject(i).let {
            Track(it.getString("id"), it.getString("title"), it.getString("artist"),
                it.optLong("duration"), it.optBoolean("local"), it.optString("artwork"), it.optBoolean("automatic"), it.optBoolean("music")) } }
        val oldIndex = prefs.getInt("index", -1).coerceIn(-1, queue.lastIndex)
        val clean = queue.filterIndexed { i, track -> i <= oldIndex || !track.automatic || RadioPolicy.eligible(track) }
        MixState(queue = clean, index = oldIndex,
            hapticAdvanceMs = prefs.getInt("hapticAdvance", 35).coerceIn(-150, 150),
            haptics = prefs.getBoolean("haptics", false), hapticIntensity = prefs.getFloat("hapticIntensity", .65f),
            crossfadeSeconds = prefs.getInt("crossfade", 8).coerceIn(0, 16),
            autoplay = prefs.getBoolean("autoplay", true), bassBlend = prefs.getBoolean("bassBlend", true),
            normalize = prefs.getBoolean("normalize", true), playerStyle = prefs.getInt("playerStyle", 0).coerceIn(0, 4),
            glassAppearance = prefs.getBoolean("glassAppearance", false),
            themeMode = prefs.getInt("themeMode", 0).coerceIn(0, 2))
    }.getOrDefault(MixState())

    override fun onDestroy() {
        scope.cancel()
        library.close()
        vibration.cancel()
        unregisterReceiver(noisy)
        audio.abandonAudioFocusRequest(focus)
        if (Build.VERSION.SDK_INT >= 31) { activeHaptic?.release(); standbyHaptic?.release() }
        active.release(); standby.release(); session.release()
        activeBass.release(); standbyBass.release()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        super.onDestroy()
    }
    companion object {
        const val CHANNEL = "playback"
        const val NOTIFICATION = 7
    }
}
