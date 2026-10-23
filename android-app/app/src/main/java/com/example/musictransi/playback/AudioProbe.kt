package com.example.musictransi.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Pass-through PCM analysis; events are consumed against playback position, not decode time. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioProbe : BaseAudioProcessor() {
    data class Pulse(val timeMs: Long, val strength: Float)
    private val pulses = ArrayDeque<Pulse>()
    private var detector = PulseDetector()
    private var hapticDetector = PulseDetector(90, 1.35)
    private val beats = BeatGrid()
    @Synchronized fun beatProfile(positionMs: Long? = null) = beats.estimate(positionMs)
    @Synchronized fun clearBeats() = beats.clear()
    private var frames = 0L
    private var windowFrames = 0
    private var energy = 0.0
    private var profileEnergy = 0.0
    private var profileWindows = 0
    @Volatile var originMs = 0L
    @Volatile var normalization = 1f
        private set
    @Volatile var firstSoundMs = -1L
        private set

    @Synchronized fun resetTrack() {
        beats.clear()
        originMs = 0; normalization = 1f; profileEnergy = 0.0; profileWindows = 0; firstSoundMs = -1
        clearTimeline()
    }
    @Synchronized private fun clearTimeline() {
        pulses.clear(); frames = 0; windowFrames = 0; energy = 0.0; detector = PulseDetector(); hapticDetector = PulseDetector(90, 1.35)
    }
    override fun onConfigure(input: AudioFormat): AudioFormat =
        if (input.encoding == C.ENCODING_PCM_16BIT) input else AudioFormat.NOT_SET
    override fun onFlush() = clearTimeline()
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val analysis = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        analyze(analysis)
        output.put(inputBuffer).flip()
    }
    @Synchronized private fun analyze(input: ByteBuffer) {
        val channels = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate
        if (channels <= 0 || rate <= 0) return
        val size = maxOf(1, rate / 50)
        while (input.remaining() >= channels * 2) {
            var frameEnergy = 0.0
            repeat(channels) { val sample = input.short / 32768.0; frameEnergy += sample * sample }
            energy += frameEnergy / channels; frames++; windowFrames++
            if (windowFrames >= size) {
                val rms = sqrt(energy / windowFrames)
                val time = originMs + frames * 1000 / rate
                if (firstSoundMs < 0 && rms > .012) firstSoundMs = (time - 20).coerceAtLeast(0)
                if (rms > .012 && profileWindows < 500) {
                    profileEnergy += rms * rms; profileWindows++
                    if (profileWindows >= 50) normalization = (.16 / sqrt(profileEnergy / profileWindows)).toFloat().coerceIn(.5f, 1f)
                }
                val strength = detector.observe(rms, time)
                if (strength > 0) beats.add(time)
                val hapticStrength = hapticDetector.observe(rms, time)
                if (hapticStrength > 0) { pulses.addLast(Pulse((time - 10).coerceAtLeast(0), hapticStrength)); while (pulses.size > 250) pulses.removeFirst() }
                windowFrames = 0; energy = 0.0
            }
        }
    }
    @Synchronized fun pulseAt(positionMs: Long): Float {
        var strength = 0f
        while (pulses.isNotEmpty() && pulses.first().timeMs <= positionMs) {
            val event = pulses.removeFirst()
            if (positionMs - event.timeMs <= 100) strength = maxOf(strength, event.strength)
        }
        return strength
    }
}
