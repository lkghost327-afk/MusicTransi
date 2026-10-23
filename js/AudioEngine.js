// AudioEngine.js - Core audio processing engine
// Manages two decks with 3-band EQ, gain control, and analyzers

/**
 * Deck class represents a single DJ channel with an audio graph consisting of:
 * AudioBufferSourceNode -> LowShelf(250Hz) -> Peaking(1200Hz, Q=0.7) -> HighShelf(3500Hz) -> GainNode -> AnalyserNode -> destination (masterGain)
 */
class Deck {
  /**
   * Creates and connects the complete audio processing chain for a deck.
   * @param {AudioContext} context - Web Audio API AudioContext instance.
   * @param {AudioNode} destination - Destination node to route the deck output into (e.g., master gain).
   */
  constructor(context, destination) {
    this.context = context;
    this.destination = destination;

    // --- Audio Graph Nodes ---
    // 3-band Equalizer filters
    // Low band: Low-shelf filter at 250 Hz
    this.lowEQ = this.context.createBiquadFilter();
    this.lowEQ.type = 'lowshelf';
    this.lowEQ.frequency.value = 250;
    this.lowEQ.gain.value = 0;

    // Mid band: Peaking filter at 1200 Hz with Q factor of 0.7
    this.midEQ = this.context.createBiquadFilter();
    this.midEQ.type = 'peaking';
    this.midEQ.frequency.value = 1200;
    this.midEQ.Q.value = 0.7;
    this.midEQ.gain.value = 0;

    // High band: High-shelf filter at 3500 Hz
    this.highEQ = this.context.createBiquadFilter();
    this.highEQ.type = 'highshelf';
    this.highEQ.frequency.value = 3500;
    this.highEQ.gain.value = 0;

    // Volume gain node (0.0 to 1.0)
    this.gainNode = this.context.createGain();
    this.gainNode.gain.value = 1.0;

    // Real-time audio analyzer for visualizers and metering
    this.analyser = this.context.createAnalyser();
    this.analyser.fftSize = 2048;

    // Connect the audio processing chain:
    // lowEQ -> midEQ -> highEQ -> gainNode -> analyser -> destination
    this.lowEQ.connect(this.midEQ);
    this.midEQ.connect(this.highEQ);
    this.highEQ.connect(this.gainNode);
    this.gainNode.connect(this.analyser);
    if (this.destination) {
      this.analyser.connect(this.destination);
    }

    // --- Playback State ---
    this.source = null;            // Current AudioBufferSourceNode instance
    this.buffer = null;            // Decoded AudioBuffer
    this.startTime = 0;            // Context time when current playback started
    this.startOffset = 0;          // Offset into audio track when current playback started (seconds)
    this.pauseOffset = 0;          // Position where playback was paused (seconds)
    this.playbackRateStartTime = 0; // Context time when current playbackRate was set
    this.playbackRateStartOffset = 0; // Audio offset at the moment playbackRate was set
    this.playing = false;          // Whether the deck is currently playing
    this.playbackRate = 1.0;       // Current playback rate speed multiplier
    this.trackInfo = {
      name: '',
      bpm: 0,
      duration: 0,
      key: ''
    };
  }

  /**
   * Loads an AudioBuffer into the deck and stores associated track metadata.
   * If the deck is currently playing, playback is stopped before loading.
   * @param {AudioBuffer} audioBuffer - The decoded audio buffer.
   * @param {Object} [trackInfo={}] - Metadata object with {name, bpm, duration, key}.
   */
  load(audioBuffer, trackInfo = {}) {
    if (this.playing) {
      this.stop();
    }

    this.buffer = audioBuffer || null;
    this.trackInfo = {
      name: trackInfo.name || '',
      bpm: trackInfo.bpm || 0,
      duration: audioBuffer ? audioBuffer.duration : (trackInfo.duration || 0),
      key: trackInfo.key || ''
    };

    this.pauseOffset = 0;
    this.startOffset = 0;
    this.playbackRateStartOffset = 0;
    this.playbackRateStartTime = 0;
    this.playbackRate = 1.0;
  }

  /**
   * Creates a new AudioBufferSourceNode, connects it to the chain, and starts playback.
   * Handles already-playing state cleanly by disconnecting the old source first.
   * @param {number} [when=0] - AudioContext timestamp at which to start playback.
   * @param {number} [offset=0] - Offset in seconds into the audio buffer to start playback from.
   * @param {number} [duration] - Optional duration in seconds to play.
   */
  play(when = 0, offset = 0, duration = undefined) {
    if (!this.buffer) {
      console.warn('Deck: Cannot play, no audio buffer loaded.');
      return;
    }

    // Clean up any existing source node to prevent overlapping or leaked sources
    this._cleanupSource();

    const now = this.context.currentTime;
    const scheduledWhen = Math.max(now, Number(when) || 0);

    // Validate and clamp offset within track boundaries
    let targetOffset = Number(offset);
    if (!Number.isFinite(targetOffset) || targetOffset < 0) {
      targetOffset = 0;
    }
    if (this.buffer.duration > 0 && targetOffset >= this.buffer.duration) {
      targetOffset = 0;
    }

    // Instantiate and configure fresh AudioBufferSourceNode
    const source = this.context.createBufferSource();
    source.buffer = this.buffer;
    source.playbackRate.value = this.playbackRate;

    // Connect source into the start of the audio graph
    source.connect(this.lowEQ);
    this.source = source;

    // Update timing state
    this.startTime = scheduledWhen;
    this.startOffset = targetOffset;
    this.playbackRateStartTime = scheduledWhen;
    this.playbackRateStartOffset = targetOffset;
    this.pauseOffset = targetOffset;
    this.playing = true;

    // Handle track completion
    source.onended = () => {
      // Only update state if this was not triggered by manual cleanup of a replaced source
      if (this.source === source) {
        this.playing = false;
        this.source = null;
        this.pauseOffset = this.buffer ? this.buffer.duration : 0;
        this.playbackRateStartOffset = this.pauseOffset;
      }
    };

    // Trigger playback
    if (duration !== undefined && duration !== null && Number.isFinite(Number(duration))) {
      source.start(scheduledWhen, targetOffset, Math.max(0, Number(duration)));
    } else {
      source.start(scheduledWhen, targetOffset);
    }
  }

  /**
   * Pauses playback, saving the current position to pauseOffset and releasing the source node.
   */
  pause() {
    if (!this.playing) {
      return;
    }

    // Capture precise current position
    this.pauseOffset = this.getCurrentTime();
    this.playbackRateStartOffset = this.pauseOffset;
    this.playing = false;

    this._cleanupSource();
  }

  /**
   * Resumes playback from the stored pauseOffset.
   */
  resume() {
    if (this.playing || !this.buffer) {
      return;
    }

    let resumeOffset = this.pauseOffset;
    // If paused at or beyond the track's end, wrap back to start
    if (this.buffer.duration > 0 && resumeOffset >= this.buffer.duration) {
      resumeOffset = 0;
    }

    this.play(0, resumeOffset);
  }

  /**
   * Stops playback immediately, resets playback offset to 0, and disconnects the source node.
   */
  stop() {
    this.playing = false;
    this.pauseOffset = 0;
    this.startOffset = 0;
    this.playbackRateStartOffset = 0;
    this._cleanupSource();
  }

  /**
   * Calculates the current playback position in seconds, accounting for playbackRate changes
   * and pause/resume intervals.
   * @returns {number} Current playback position in seconds.
   */
  getCurrentTime() {
    if (!this.buffer) {
      return 0;
    }

    if (!this.playing) {
      return Math.min(this.buffer.duration, Math.max(0, this.pauseOffset));
    }

    const now = this.context.currentTime;
    // If playback is scheduled in the future, current position is the starting offset
    if (now < this.startTime) {
      return Math.min(this.buffer.duration, Math.max(0, this.playbackRateStartOffset));
    }

    const elapsedContext = now - this.playbackRateStartTime;
    const currentPos = this.playbackRateStartOffset + (elapsedContext * this.playbackRate);
    return Math.min(this.buffer.duration, Math.max(0, currentPos));
  }

  /**
   * Returns remaining playback time in the track in seconds.
   * @returns {number}
   */
  getRemainingTime() {
    if (!this.buffer) {
      return 0;
    }
    return Math.max(0, this.buffer.duration - this.getCurrentTime());
  }

  /**
   * Smoothly adjusts the deck volume using a short ramp (0.02s) to prevent audible clicks.
   * @param {number} value - Volume between 0.0 (silent) and 1.0 (full).
   */
  setVolume(value) {
    const numericVal = Number(value);
    if (!Number.isFinite(numericVal)) {
      return;
    }
    const gain = Math.max(0, Math.min(1, numericVal));
    const now = this.context.currentTime;
    this.gainNode.gain.setTargetAtTime(gain, now, 0.02);
  }

  /**
   * Sets EQ band gain in dB ('low', 'mid', 'high') using setTargetAtTime.
   * @param {string} band - 'low', 'mid', or 'high'.
   * @param {number} gainDb - Gain in decibels.
   */
  setEQ(band, gainDb) {
    const gain = Number(gainDb);
    if (!Number.isFinite(gain)) {
      return;
    }

    const now = this.context.currentTime;
    const timeConstant = 0.02;

    switch (String(band).toLowerCase()) {
      case 'low':
        this.lowEQ.gain.setTargetAtTime(gain, now, timeConstant);
        break;
      case 'mid':
        this.midEQ.gain.setTargetAtTime(gain, now, timeConstant);
        break;
      case 'high':
        this.highEQ.gain.setTargetAtTime(gain, now, timeConstant);
        break;
      default:
        console.warn(`Deck: Unknown EQ band "${band}". Expected "low", "mid", or "high".`);
    }
  }

  /**
   * Sets the playback speed rate with a short ramp.
   * Updates timing offsets to maintain accurate current time reporting.
   * @param {number} rate - Playback rate multiplier (e.g., 1.0 = normal, 1.1 = +10%).
   */
  setPlaybackRate(rate) {
    const numericRate = Number(rate);
    if (!Number.isFinite(numericRate) || numericRate <= 0) {
      return;
    }

    const now = this.context.currentTime;

    // If currently playing, compute accumulated position up to now before changing rate
    if (this.playing && this.source) {
      if (now >= this.startTime) {
        const elapsed = now - this.playbackRateStartTime;
        this.playbackRateStartOffset += elapsed * this.playbackRate;
        this.playbackRateStartTime = now;
      }
    }

    this.playbackRate = numericRate;

    // Smoothly transition source playback rate if active
    if (this.source && this.source.playbackRate) {
      this.source.playbackRate.setTargetAtTime(numericRate, now, 0.02);
    }
  }

  /**
   * Returns a Float32Array of frequency data from the deck's analyser.
   * @returns {Float32Array}
   */
  getAnalyserData() {
    if (!this.analyser) {
      return new Float32Array(0);
    }
    const data = new Float32Array(this.analyser.frequencyBinCount);
    this.analyser.getFloatFrequencyData(data);
    return data;
  }

  /**
   * Returns a Float32Array of time domain (waveform) data from the deck's analyser.
   * @returns {Float32Array}
   */
  getTimeDomainData() {
    if (!this.analyser) {
      return new Float32Array(0);
    }
    const data = new Float32Array(this.analyser.fftSize);
    this.analyser.getFloatTimeDomainData(data);
    return data;
  }

  /**
   * Returns true if an audio buffer is currently loaded.
   * @returns {boolean}
   */
  isLoaded() {
    return this.buffer !== null;
  }

  /**
   * Stops playback, disconnects source, and clears loaded buffer and track metadata.
   */
  reset() {
    this.stop();
    this.buffer = null;
    this.trackInfo = {
      name: '',
      bpm: 0,
      duration: 0,
      key: ''
    };
    this.playbackRate = 1.0;
    this.pauseOffset = 0;
    this.startOffset = 0;
    this.playbackRateStartOffset = 0;
    this.playbackRateStartTime = 0;
  }

  /**
   * Helper method to stop and disconnect the current source node safely.
   * @private
   */
  _cleanupSource() {
    if (this.source) {
      const src = this.source;
      this.source = null;
      src.onended = null;
      try {
        src.stop();
      } catch (err) {
        // Source may have already stopped or not started yet
      }
      try {
        src.disconnect();
      } catch (err) {
        // Ignore disconnect errors
      }
    }
  }
}

/**
 * AudioEngine class coordinates two Deck instances, master volume control,
 * and audio file decoding.
 */
class AudioEngine {
  /**
   * Initializes properties in an uninitialized state.
   */
  constructor() {
    this.context = null;
    this.deckA = null;
    this.deckB = null;
    this.masterGain = null;
    this.activeDeck = 'A';
    this.initialized = false;
  }

  /**
   * Initializes the AudioContext, master gain node, and two Deck instances.
   * Handles browser autoplay policy by attempting resume and binding gesture listeners if blocked.
   * @returns {Promise<void>}
   */
  async init() {
    if (this.initialized && this.context) {
      if (this.context.state === 'suspended') {
        try {
          await this.context.resume();
        } catch (err) {
          console.warn('AudioEngine: Failed to resume AudioContext during init:', err);
        }
      }
      return;
    }

    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) {
      throw new Error('Web Audio API is not supported in this browser.');
    }

    this.context = new AudioContextClass();

    // Master gain node
    this.masterGain = this.context.createGain();
    this.masterGain.gain.value = 1.0;
    this.masterGain.connect(this.context.destination);

    // Initialize Deck A and Deck B connected to master gain
    this.deckA = new Deck(this.context, this.masterGain);
    this.deckB = new Deck(this.context, this.masterGain);

    this.activeDeck = 'A';
    this.initialized = true;

    // Handle browser autoplay policy restrictions
    if (this.context.state === 'suspended') {
      try {
        await this.context.resume();
      } catch (err) {
        // Autoplay policy prevented immediate resume; register one-time user gesture listeners
        this._setupAutoplayResume();
      }
    }
  }

  /**
   * Decodes an audio File or Blob into an AudioBuffer using Web Audio API decodeAudioData.
   * @param {File|Blob} file - The audio file to load and decode.
   * @returns {Promise<AudioBuffer>} Decoded audio buffer.
   */
  async loadFile(file) {
    if (!file) {
      throw new Error('AudioEngine.loadFile: No file provided.');
    }

    // Ensure audio engine is initialized before decoding
    if (!this.initialized || !this.context) {
      await this.init();
    }

    // Read the file as an ArrayBuffer
    let arrayBuffer;
    if (typeof file.arrayBuffer === 'function') {
      arrayBuffer = await file.arrayBuffer();
    } else {
      arrayBuffer = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(reader.error || new Error('Failed to read file as ArrayBuffer.'));
        reader.readAsArrayBuffer(file);
      });
    }

    // Decode audio data (supporting both callback and promise-based Web Audio implementations)
    return new Promise((resolve, reject) => {
      // Create a slice copy to avoid issues in engines that detach ArrayBuffers
      const bufferCopy = arrayBuffer.slice(0);
      this.context.decodeAudioData(
        bufferCopy,
        (decodedBuffer) => resolve(decodedBuffer),
        (decodeError) => reject(decodeError || new Error('Failed to decode audio file.'))
      );
    });
  }

  /**
   * Returns the Deck instance that is currently designated as active.
   * @returns {Deck|null}
   */
  getActiveDeck() {
    return this.activeDeck === 'A' ? this.deckA : this.deckB;
  }

  /**
   * Returns the Deck instance that is currently designated as inactive.
   * @returns {Deck|null}
   */
  getInactiveDeck() {
    return this.activeDeck === 'A' ? this.deckB : this.deckA;
  }

  /**
   * Toggles which deck is active ('A' <-> 'B') and returns the newly active Deck.
   * @returns {Deck|null}
   */
  swapDecks() {
    this.activeDeck = this.activeDeck === 'A' ? 'B' : 'A';
    return this.getActiveDeck();
  }

  /**
   * Sets master gain volume (0.0 to 1.0) with a smooth 0.02s ramp.
   * @param {number} value - Target volume level between 0.0 and 1.0.
   */
  setMasterVolume(value) {
    if (!this.masterGain || !this.context) {
      return;
    }
    const numericVal = Number(value);
    if (!Number.isFinite(numericVal)) {
      return;
    }
    const gain = Math.max(0, Math.min(1, numericVal));
    const now = this.context.currentTime;
    this.masterGain.gain.setTargetAtTime(gain, now, 0.02);
  }

  /**
   * Returns the current AudioContext time in seconds.
   * @returns {number}
   */
  getCurrentTime() {
    return this.context ? this.context.currentTime : 0;
  }

  /**
   * Suspends the AudioContext, pausing audio processing and saving system resources.
   * @returns {Promise<void>}
   */
  async suspend() {
    if (this.context && this.context.state === 'running') {
      await this.context.suspend();
    }
  }

  /**
   * Resumes the AudioContext after suspension.
   * @returns {Promise<void>}
   */
  async resume() {
    if (this.context && this.context.state === 'suspended') {
      await this.context.resume();
    }
  }

  /**
   * Attaches one-time event listeners on common user interactions to resume the context
   * if autoplay policy prevented initial startup.
   * @private
   */
  _setupAutoplayResume() {
    const resumeHandler = async () => {
      if (this.context && this.context.state === 'suspended') {
        try {
          await this.context.resume();
        } catch (err) {
          console.warn('AudioEngine: Failed to resume on user interaction:', err);
        }
      }
      if (this.context && this.context.state === 'running') {
        window.removeEventListener('click', resumeHandler);
        window.removeEventListener('keydown', resumeHandler);
        window.removeEventListener('touchstart', resumeHandler);
      }
    };

    window.addEventListener('click', resumeHandler, { passive: true });
    window.addEventListener('keydown', resumeHandler, { passive: true });
    window.addEventListener('touchstart', resumeHandler, { passive: true });
  }
}

export { Deck, AudioEngine };
export default AudioEngine;
