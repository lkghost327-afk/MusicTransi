/**
 * @file AutoMixer.js
 * @description Core DJ transition and mixing engine module for the DJ AutoMix Web Application.
 *
 * Implements professional DJ mixing techniques:
 * 1. Bass isolation & swapping: strictly avoids muddy low-end clashing by ensuring
 *    only one bassline dominates at any time. Outgoing bass cuts rapidly between 40-50%,
 *    while incoming bass opens rapidly between 50-60%.
 * 2. Phased EQ crossfading: high frequencies introduce rhythm/percussion early,
 *    mid frequencies blend harmonic elements progressively, and lows swap cleanly.
 * 3. Equal-power crossfade curves (cos/sin): prevents perceived volume dips at the
 *    50% crossover point, preserving consistent acoustic loudness (~ -3dB per channel).
 * 4. Automatic tempo / BPM matching: dynamically adjusts playback rate for transitions
 *    within ±8% tempo differences (including 0.5x/2.0x octave harmonic relationships).
 * 5. Musical phrase alignment: transition durations scaled to 4, 8, 16, or 32 musical bars.
 */

// Pre-calculated mathematical constants to avoid allocations in animation frame loops
const HALF_PI = Math.PI / 2;
const DEFAULT_TRANSITION_DURATION = 10; // Fallback transition duration in seconds
const DEFAULT_BEATS_PER_BAR = 4;        // Standard 4/4 musical time signature
const PARAM_RAMP_TIME = 0.02;           // Smooth audio param ramping constant (20ms)

/**
 * AutoMixer class manages automated blending, crossfading, EQ isolation,
 * and tempo synchronization between dual DJ audio decks.
 */
export default class AutoMixer {
  /**
   * Creates an instance of AutoMixer.
   * @param {Object} audioEngine - Instance of AudioEngine managing audio context and decks.
   */
  constructor(audioEngine) {
    /** @type {Object} Reference to the underlying AudioEngine instance */
    this.engine = audioEngine;

    /** @type {boolean} Toggle for automix automation */
    this.enabled = true;

    /** @type {number} Transition length in musical bars (typical DJ phrases: 4, 8, 16, 32) */
    this.transitionBars = 16;

    /** @type {boolean} Indicates if a crossfade transition is currently active */
    this.isTransitioning = false;

    /** @type {number} Engine timestamp (in seconds) when the transition began */
    this.transitionStartTime = 0;

    /** @type {number} Total duration of the current transition in seconds */
    this.transitionDuration = 0;

    /** @type {number|null} requestAnimationFrame loop identifier */
    this.monitorId = null;

    /** @type {boolean} Flag indicating if EQ crossfade profile should be executed */
    this.useEQCrossfade = true;

    // Callbacks for UI updates and state management
    /** @type {(() => void)|null} Callback triggered when a transition starts */
    this.onTransitionStart = null;

    /** @type {((progress: number) => void)|null} Callback reporting transition progress (0.0 to 1.0) */
    this.onTransitionUpdate = null;

    /** @type {(() => void)|null} Callback triggered when a transition finishes */
    this.onTransitionEnd = null;

    /** @type {(() => void)|null} Callback triggered when active track ends without next track */
    this.onTrackEnd = null;

    /** @type {((currentTime: number, duration: number) => void)|null} Callback reporting active track playback position */
    this.onPlaybackUpdate = null;

    // Internal state tracking for pause/resume and edge case handling
    /** @private @type {boolean} Flag to prevent multiple firings of onTrackEnd */
    this._trackEndFired = false;

    /** @private @type {boolean} Tracks pause status during transition */
    this._isPaused = false;

    /** @private @type {number} Engine timestamp when pause occurred */
    this._pauseTimestamp = 0;

    /** @private @type {number|null} Inactive deck playback rate adjusted during transition */
    this._adjustedPlaybackRate = null;
  }

  /**
   * Calculate transition duration in seconds based on BPM and musical bar length.
   * At 128 BPM with 16 bars: 16 * 4 * (60 / 128) = 30 seconds.
   *
   * @param {number|null|undefined} bpm - Beats per minute of the track.
   * @returns {number} Transition duration in seconds.
   */
  calcTransitionDuration(bpm) {
    if (!bpm || bpm <= 0 || !Number.isFinite(bpm)) {
      return DEFAULT_TRANSITION_DURATION; // Default 10 seconds fallback
    }
    const secondsPerBeat = 60 / bpm;
    return this.transitionBars * DEFAULT_BEATS_PER_BAR * secondsPerBeat;
  }

  /**
   * Set transition length in musical bars.
   * Supports standard DJ phrasing: 4, 8, 16, 32 bars.
   *
   * @param {number} bars - Number of musical bars for transitions.
   */
  setTransitionBars(bars) {
    const parsed = parseInt(bars, 10);
    if (!isNaN(parsed) && parsed > 0) {
      this.transitionBars = parsed;
    }
  }

  /**
   * Get the current transition length in musical bars.
   * @returns {number}
   */
  getTransitionBars() {
    return this.transitionBars;
  }

  /**
   * Enable or disable automix functionality.
   * @param {boolean} enabled - True to enable automix, false to disable.
   */
  setEnabled(enabled) {
    this.enabled = Boolean(enabled);
  }

  /**
   * Get the current transition progress (0.0 to 1.0).
   * @returns {number}
   */
  getProgress() {
    if (!this.isTransitioning || this.transitionDuration <= 0) return 0;
    const elapsed = this._getEngineTime() - this.transitionStartTime;
    return Math.min(Math.max(elapsed / this.transitionDuration, 0), 1);
  }

  /**
   * Start monitoring playback position to trigger transitions and report playback updates.
   */
  startMonitoring() {
    if (this.monitorId !== null) return; // Already running

    const monitor = () => {
      this.monitorId = requestAnimationFrame(monitor);
      this.update();
    };
    this.monitorId = requestAnimationFrame(monitor);
  }

  /**
   * Stop the monitoring loop.
   */
  stopMonitoring() {
    if (this.monitorId !== null) {
      cancelAnimationFrame(this.monitorId);
      this.monitorId = null;
    }
  }

  /**
   * Main update loop called every animation frame.
   * Inspects playback state, triggers transitions, and drives active transitions.
   * Highly optimized to avoid object allocations in hot path.
   */
  update() {
    const activeDeck = this.engine?.getActiveDeck?.();

    // If active deck is not playing or has no loaded buffer, handle pause states
    if (!activeDeck || !activeDeck.playing || !activeDeck.buffer) {
      if (this.isTransitioning && !this._isPaused) {
        this._isPaused = true;
        this._pauseTimestamp = this._getEngineTime();
      }
      return;
    }

    const now = this._getEngineTime();

    // If we resumed playback while mid-transition, offset transitionStartTime
    if (this.isTransitioning && this._isPaused) {
      const pausedDuration = now - this._pauseTimestamp;
      if (pausedDuration > 0) {
        this.transitionStartTime += pausedDuration;
      }
      this._isPaused = false;
    }

    // Retrieve playback position and duration
    const currentTime = typeof activeDeck.getCurrentTime === 'function'
      ? activeDeck.getCurrentTime()
      : (activeDeck.currentTime || 0);

    const duration = activeDeck.buffer?.duration || activeDeck.duration || 0;
    if (duration <= 0) return;

    const remaining = duration - currentTime;

    // Report playback position to UI (pass primitives directly to avoid GC allocations)
    if (typeof this.onPlaybackUpdate === 'function') {
      this.onPlaybackUpdate(currentTime, duration);
    }

    // Edge Case: If user scrubbed back significantly while in a transition, cancel transition
    if (this.isTransitioning && remaining > this.transitionDuration + 2.0) {
      this.cancelTransition();
      return;
    }

    // Check if we should automatically begin a transition
    if (this.enabled && !this.isTransitioning) {
      let transitionDuration = this.calcTransitionDuration(
        activeDeck.trackInfo?.bpm
      );

      // Edge Case: Guard against very short tracks where transition would exceed 40% of track length
      if (duration > 0 && transitionDuration >= duration * 0.4) {
        transitionDuration = Math.max(2, duration * 0.4);
      }

      if (remaining <= transitionDuration && remaining > 0.5) {
        this.beginTransition(transitionDuration);
      }
    }

    // Update ongoing transition
    if (this.isTransitioning) {
      this.updateTransition();
    }

    // Check if track ended without transition (e.g. no next track loaded or automix disabled)
    if (currentTime >= duration - 0.1 && !this.isTransitioning) {
      if (!this._trackEndFired) {
        this._trackEndFired = true;
        if (typeof this.onTrackEnd === 'function') {
          this.onTrackEnd();
        }
      }
    } else if (currentTime < duration - 1.0) {
      // Reset track end flag once playback is safely before the end
      this._trackEndFired = false;
    }
  }

  /**
   * Begin a transition to the next track on the inactive deck.
   *
   * @param {number} duration - Transition duration in seconds.
   */
  beginTransition(duration) {
    const inDeck = this.engine?.getInactiveDeck?.();
    const isLoaded = inDeck && (
      typeof inDeck.isLoaded === 'function'
        ? inDeck.isLoaded()
        : Boolean(inDeck.buffer || inDeck.isLoaded)
    );

    // If no next track is loaded, let current track finish naturally
    if (!inDeck || !isLoaded) {
      return;
    }

    this.isTransitioning = true;
    this.useEQCrossfade = true;
    this.transitionDuration = Math.max(0.5, duration);
    this.transitionStartTime = this._getEngineTime();
    this._isPaused = false;
    this._trackEndFired = false;

    // Calculate tempo adjustment for incoming track
    const outDeck = this.engine?.getActiveDeck?.();
    const outBPM = outDeck?.trackInfo?.bpm;
    const inBPM = inDeck?.trackInfo?.bpm;

    if (outBPM && inBPM && inBPM > 0) {
      let ratio = outBPM / inBPM;

      // Handle octave harmonic BPM matching (e.g., 70 BPM vs 140 BPM)
      if (ratio >= 1.84 && ratio <= 2.16) {
        ratio /= 2;
      } else if (ratio >= 0.46 && ratio <= 0.54) {
        ratio *= 2;
      }

      // Only adjust if within ±8% (professional DJ pitch bending range)
      if (ratio >= 0.92 && ratio <= 1.08 && Math.abs(ratio - 1) > 0.001) {
        if (typeof inDeck.setPlaybackRate === 'function') {
          inDeck.setPlaybackRate(ratio);
          this._adjustedPlaybackRate = ratio;
        }
      }
    }

    // Start incoming track with EQ fully cut and volume at 0
    this._setDeckVolume(inDeck, 0, PARAM_RAMP_TIME);
    this._setDeckEQ(inDeck, 'low', -30, PARAM_RAMP_TIME);
    this._setDeckEQ(inDeck, 'mid', -18, PARAM_RAMP_TIME);
    this._setDeckEQ(inDeck, 'high', -24, PARAM_RAMP_TIME);

    if (typeof inDeck.play === 'function') {
      inDeck.play();
    }

    if (typeof this.onTransitionStart === 'function') {
      this.onTransitionStart();
    }
  }

  /**
   * Update the active transition (called every frame).
   * Computes equal-power crossfader gains and phased EQ profiles.
   */
  updateTransition() {
    const elapsed = this._getEngineTime() - this.transitionStartTime;
    const progress = this.transitionDuration > 0
      ? Math.min(Math.max(elapsed / this.transitionDuration, 0), 1)
      : 1;

    const outDeck = this.engine?.getActiveDeck?.();
    const inDeck = this.engine?.getInactiveDeck?.();

    if (!outDeck || !inDeck) {
      this.completeTransition();
      return;
    }

    // Equal-power crossfade (cos/sin curves maintain constant ~0.707 power at 50% crossover)
    const outVolume = Math.cos(progress * HALF_PI);
    const inVolume = Math.sin(progress * HALF_PI);
    this._setDeckVolume(outDeck, outVolume, PARAM_RAMP_TIME);
    this._setDeckVolume(inDeck, inVolume, PARAM_RAMP_TIME);

    if (this.useEQCrossfade) {
      // Phased EQ Crossfade:
      // 1. Outgoing Highs: 0dB -> -24dB over 0% to 60%
      const outHighProgress = Math.min(progress / 0.6, 1);
      this._setDeckEQ(outDeck, 'high', -24 * outHighProgress, PARAM_RAMP_TIME);

      // 2. Outgoing Mids: 0dB -> -18dB over 20% to 75%
      const outMidProgress = Math.max(0, Math.min((progress - 0.2) / 0.55, 1));
      this._setDeckEQ(outDeck, 'mid', -18 * outMidProgress, PARAM_RAMP_TIME);

      // 3. Bass Swap (Crucial DJ mixing moment - basses NEVER play simultaneously):
      // - Outgoing cuts rapidly from 0dB to -30dB between 40% and 50%
      const outLowProgress = Math.max(0, Math.min((progress - 0.4) / 0.1, 1));
      this._setDeckEQ(outDeck, 'low', -30 * outLowProgress, PARAM_RAMP_TIME);

      // - Incoming opens rapidly from -30dB to 0dB between 50% and 60%
      const inLowProgress = Math.max(0, Math.min((progress - 0.5) / 0.1, 1));
      this._setDeckEQ(inDeck, 'low', -30 * (1 - inLowProgress), PARAM_RAMP_TIME);

      // 4. Incoming Highs: -24dB -> 0dB over 0% to 40%
      const inHighProgress = Math.min(progress / 0.4, 1);
      this._setDeckEQ(inDeck, 'high', -24 * (1 - inHighProgress), PARAM_RAMP_TIME);

      // 5. Incoming Mids: -18dB -> 0dB over 25% to 70%
      const inMidProgress = Math.max(0, Math.min((progress - 0.25) / 0.45, 1));
      this._setDeckEQ(inDeck, 'mid', -18 * (1 - inMidProgress), PARAM_RAMP_TIME);
    }

    if (typeof this.onTransitionUpdate === 'function') {
      this.onTransitionUpdate(progress);
    }

    // Transition complete
    if (progress >= 1) {
      this.completeTransition();
    }
  }

  /**
   * Complete the transition, reset outgoing deck, finalize incoming deck,
   * and swap the active deck in the audio engine.
   */
  completeTransition() {
    const outDeck = this.engine?.getActiveDeck?.();
    const inDeck = this.engine?.getInactiveDeck?.();

    // Stop and reset the outgoing track
    if (outDeck) {
      if (typeof outDeck.stop === 'function') outDeck.stop();
      if (typeof outDeck.reset === 'function') outDeck.reset();
    }

    // Ensure incoming track is at full volume and flat EQ
    if (inDeck) {
      this._setDeckVolume(inDeck, 1, PARAM_RAMP_TIME);
      this._setDeckEQ(inDeck, 'low', 0, PARAM_RAMP_TIME);
      this._setDeckEQ(inDeck, 'mid', 0, PARAM_RAMP_TIME);
      this._setDeckEQ(inDeck, 'high', 0, PARAM_RAMP_TIME);

      // Reset playback rate to original tempo (1.0)
      if (typeof inDeck.setPlaybackRate === 'function') {
        inDeck.setPlaybackRate(1);
      }
    }

    // Swap the active deck on the AudioEngine
    if (typeof this.engine?.swapDecks === 'function') {
      this.engine.swapDecks();
    }

    this.isTransitioning = false;
    this.useEQCrossfade = true;
    this._adjustedPlaybackRate = null;
    this._trackEndFired = false;
    this._isPaused = false;

    if (typeof this.onTransitionEnd === 'function') {
      this.onTransitionEnd();
    }
  }

  /**
   * Cancel an ongoing transition and restore the active deck to full volume & flat EQ.
   */
  cancelTransition() {
    if (!this.isTransitioning) return;

    const outDeck = this.engine?.getActiveDeck?.();
    const inDeck = this.engine?.getInactiveDeck?.();

    // Restore outgoing deck to full volume and flat EQ
    if (outDeck) {
      this._setDeckVolume(outDeck, 1, PARAM_RAMP_TIME);
      this._setDeckEQ(outDeck, 'low', 0, PARAM_RAMP_TIME);
      this._setDeckEQ(outDeck, 'mid', 0, PARAM_RAMP_TIME);
      this._setDeckEQ(outDeck, 'high', 0, PARAM_RAMP_TIME);
    }

    // Stop and reset incoming deck
    if (inDeck) {
      if (typeof inDeck.stop === 'function') inDeck.stop();
      if (typeof inDeck.reset === 'function') inDeck.reset();
      this._setDeckVolume(inDeck, 0, PARAM_RAMP_TIME);
      if (typeof inDeck.setPlaybackRate === 'function') {
        inDeck.setPlaybackRate(1);
      }
    }

    this.isTransitioning = false;
    this.useEQCrossfade = true;
    this._adjustedPlaybackRate = null;
    this._isPaused = false;
  }

  /**
   * Force skip to the next track with an immediate short crossfade.
   * Works even when automix is disabled.
   *
   * @returns {Promise<void>}
   */
  async skipToNext() {
    if (this.isTransitioning) {
      // Speed up the current transition to complete cleanly within ~0.5s
      this.transitionDuration = 1;
      this.transitionStartTime = this._getEngineTime() - 0.5;
      return;
    }

    const inDeck = this.engine?.getInactiveDeck?.();
    const isLoaded = inDeck && (
      typeof inDeck.isLoaded === 'function'
        ? inDeck.isLoaded()
        : Boolean(inDeck.buffer || inDeck.isLoaded)
    );

    if (!inDeck || !isLoaded) return;

    // Quick 2-second crossfade
    this.isTransitioning = true;
    this.useEQCrossfade = false; // Keep EQs flat for fast skip crossfade
    this.transitionDuration = 2;
    this.transitionStartTime = this._getEngineTime();
    this._isPaused = false;
    this._trackEndFired = false;

    // Initialize incoming deck with flat EQ and volume 0
    this._setDeckVolume(inDeck, 0, PARAM_RAMP_TIME);
    this._setDeckEQ(inDeck, 'low', 0, PARAM_RAMP_TIME);
    this._setDeckEQ(inDeck, 'mid', 0, PARAM_RAMP_TIME);
    this._setDeckEQ(inDeck, 'high', 0, PARAM_RAMP_TIME);

    if (typeof inDeck.play === 'function') {
      inDeck.play();
    }

    if (typeof this.onTransitionStart === 'function') {
      this.onTransitionStart();
    }
  }

  /**
   * Clean up and release monitoring resources.
   */
  destroy() {
    this.stopMonitoring();
    if (this.isTransitioning) {
      this.cancelTransition();
    }
    this.onTransitionStart = null;
    this.onTransitionUpdate = null;
    this.onTransitionEnd = null;
    this.onTrackEnd = null;
    this.onPlaybackUpdate = null;
  }

  /**
   * Safely obtain the current audio time from the engine or performance clock.
   * @private
   * @returns {number} Current time in seconds.
   */
  _getEngineTime() {
    if (typeof this.engine?.getCurrentTime === 'function') {
      return this.engine.getCurrentTime();
    }
    if (this.engine?.audioContext?.currentTime !== undefined) {
      return this.engine.audioContext.currentTime;
    }
    return performance.now() / 1000;
  }

  /**
   * Set deck volume safely using smooth parameter ramping to avoid clicks.
   * @private
   * @param {Object} deck - Deck instance.
   * @param {number} volume - Linear volume gain (0.0 to 1.0).
   * @param {number} [rampTime=0.02] - Ramping time constant in seconds.
   */
  _setDeckVolume(deck, volume, rampTime = PARAM_RAMP_TIME) {
    if (!deck) return;

    if (typeof deck.setVolume === 'function') {
      deck.setVolume(volume, rampTime);
    } else if (deck.gainNode?.gain?.setTargetAtTime) {
      const now = this._getEngineTime();
      deck.gainNode.gain.setTargetAtTime(volume, now, rampTime);
    }
  }

  /**
   * Set deck EQ band gain safely using smooth parameter ramping to avoid clicks.
   * @private
   * @param {Object} deck - Deck instance.
   * @param {'low'|'mid'|'high'} band - EQ frequency band.
   * @param {number} gainDb - Gain in decibels.
   * @param {number} [rampTime=0.02] - Ramping time constant in seconds.
   */
  _setDeckEQ(deck, band, gainDb, rampTime = PARAM_RAMP_TIME) {
    if (!deck) return;

    if (typeof deck.setEQ === 'function') {
      deck.setEQ(band, gainDb, rampTime);
    } else if (deck.eqNodes && deck.eqNodes[band]?.gain?.setTargetAtTime) {
      const now = this._getEngineTime();
      deck.eqNodes[band].gain.setTargetAtTime(gainDb, now, rampTime);
    } else if (deck.filters && deck.filters[band]?.gain?.setTargetAtTime) {
      const now = this._getEngineTime();
      deck.filters[band].gain.setTargetAtTime(gainDb, now, rampTime);
    }
  }
}
