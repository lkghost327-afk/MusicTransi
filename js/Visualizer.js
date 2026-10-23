/**
 * @file Visualizer.js
 * @description Professional DJ software-inspired Canvas waveform visualizer module.
 * Provides high-DPI waveform rendering with bar-style visualization, mirror reflection,
 * playhead tracking, dual-deck transition overlapping, equal-power crossfade curves,
 * and real-time live frequency spectrum analyzer displays.
 */

export default class Visualizer {
  /**
   * Creates a new Visualizer instance.
   * @param {HTMLCanvasElement} canvas - Target HTML canvas element for rendering.
   */
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas ? canvas.getContext('2d') : null;

    // Waveform data: array of peak objects [{ min: number, max: number }, ...] or numbers
    this.waveformDataA = null;  // Deck A (outgoing / primary track)
    this.waveformDataB = null;  // Deck B (incoming track)

    // Playhead playback position (normalized 0.0 to 1.0)
    this.playheadPosition = 0;

    // Transition state
    // transitionProgress: -1 indicates no transition active; 0.0 to 1.0 indicates mix progress
    this.transitionProgress = -1;
    // transitionZoneStart: normalized position (0.0 to 1.0) along Track A where overlap begins
    this.transitionZoneStart = 0;

    // Live audio frequency data (e.g. Uint8Array from Web Audio AnalyserNode)
    this.analyserDataA = null;  // Deck A live frequency spectrum
    this.analyserDataB = null;  // Deck B live frequency spectrum

    // Animation frame handle
    this.animationId = null;

    // Display & DPI metrics
    this.dpr = typeof window !== 'undefined' ? (window.devicePixelRatio || 1) : 1;
    this.width = 0;
    this.height = 0;

    // Bound resize listener for safe attachment & removal
    this._boundResize = this.resize.bind(this);
  }

  /**
   * Initializes the visualizer, sets up DPI-scaled dimensions,
   * and attaches the window resize event listener.
   */
  init() {
    this.resize();
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', this._boundResize);
    }
  }

  /**
   * Handles canvas resizing with devicePixelRatio awareness for crisp high-DPI rendering.
   * Gracefully handles zero-dimension containers (e.g. when hidden or before layout pass).
   */
  resize() {
    if (!this.canvas) return;

    const rect = this.canvas.getBoundingClientRect();
    // Guard against zero-sized canvas element (e.g., display: none or unmounted)
    if (rect.width === 0 || rect.height === 0) {
      return;
    }

    this.dpr = typeof window !== 'undefined' ? (window.devicePixelRatio || 1) : 1;
    this.width = rect.width;
    this.height = rect.height;

    // Set internal pixel buffer size scaled by device pixel ratio
    this.canvas.width = Math.round(rect.width * this.dpr);
    this.canvas.height = Math.round(rect.height * this.dpr);

    // Apply coordinate transformation so drawing operations use CSS pixels
    if (this.ctx) {
      this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    }
  }

  /**
   * Sets pre-analyzed waveform peak data for a specified deck.
   * Accepts arrays of `{ min, max }` peak objects or raw normalized amplitude numbers.
   * @param {'A'|'B'|string} deck - 'A' for Deck A / outgoing track, 'B' for Deck B / incoming track.
   * @param {Array<{min: number, max: number}>|Array<number>} data - Array of waveform peak samples.
   */
  setWaveformData(deck, data) {
    const deckKey = String(deck).toUpperCase();
    if (deckKey === 'A') {
      this.waveformDataA = data;
    } else {
      this.waveformDataB = data;
    }
  }

  /**
   * Updates current playhead playback position.
   * @param {number} position - Normalized progress (0.0 to 1.0).
   */
  setPlayheadPosition(position) {
    this.playheadPosition = Math.max(0, Math.min(1, position || 0));
  }

  /**
   * Updates transition state parameters.
   * @param {number} progress - Transition progress: -1 for inactive, or 0.0 to 1.0 during crossfade.
   * @param {number} [zoneStart] - Normalized start position of transition zone (0.0 to 1.0).
   */
  setTransitionState(progress, zoneStart) {
    this.transitionProgress = typeof progress === 'number' ? progress : -1;
    if (typeof zoneStart === 'number' && !isNaN(zoneStart)) {
      this.transitionZoneStart = Math.max(0, Math.min(1, zoneStart));
    }
  }

  /**
   * Sets real-time frequency analysis data from Web Audio AnalyserNode.
   * @param {'A'|'B'|string} deck - 'A' or 'B'.
   * @param {Uint8Array|Float32Array|Array<number>|null} data - Frequency data array.
   */
  setAnalyserData(deck, data) {
    const deckKey = String(deck).toUpperCase();
    if (deckKey === 'A') {
      this.analyserDataA = data;
    } else {
      this.analyserDataB = data;
    }
  }

  /**
   * Primary render method. Designed for high efficiency at 60 FPS.
   * Renders background grid, waveforms, transition zone, crossfade curves, playhead,
   * and live frequency analyzers.
   */
  render() {
    const ctx = this.ctx;
    if (!ctx) return;

    // Verify canvas dimensions; attempt resize if dimensions are zero
    if (this.width <= 0 || this.height <= 0) {
      this.resize();
      if (this.width <= 0 || this.height <= 0) return;
    }

    const w = this.width;
    const h = this.height;

    // Clear previous frame (background is transparent, letting CSS handle dark UI theme)
    ctx.clearRect(0, 0, w, h);

    // Draw background grid and time marker guidelines
    this.drawGrid(ctx, w, h);

    // Dual-track view during active crossfade transition when incoming track is loaded
    const isTransitionActive = this.transitionProgress >= 0 && Boolean(this.waveformDataB);

    if (isTransitionActive) {
      // Dual waveform view showing Track A and Track B overlapping
      this.drawDualWaveform(ctx, w, h);
      // X-shaped equal-power crossfade curves
      this.drawCrossfadeCurves(ctx, w, h);
    } else {
      // Normal single waveform overview
      this.drawSingleWaveform(ctx, w, h);
    }

    // Draw playhead indicator line with glow
    this.drawPlayhead(ctx, w, h);

    // Draw real-time live frequency spectrum HUD if analyser data is available
    this.drawLiveFrequencyBars(ctx, w, h);
  }

  /**
   * Draws subtle DJ-style background grid with vertical division markers and center axis.
   * @param {CanvasRenderingContext2D} ctx - Rendering context.
   * @param {number} w - Canvas width.
   * @param {number} h - Canvas height.
   */
  drawGrid(ctx, w, h) {
    ctx.save();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.03)';
    ctx.lineWidth = 1;

    // Vertical grid lines every 10% with subtle subdivision tick marks
    for (let i = 0; i <= 10; i++) {
      const x = Math.round((i / 10) * w);
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, h);
      ctx.stroke();

      // Top and bottom tick accents every 5%
      if (i < 10) {
        const midX = Math.round(((i + 0.5) / 10) * w);
        ctx.beginPath();
        ctx.moveTo(midX, 0);
        ctx.lineTo(midX, 4);
        ctx.moveTo(midX, h - 4);
        ctx.lineTo(midX, h);
        ctx.stroke();
      }
    }

    // Horizontal center alignment guide
    ctx.beginPath();
    ctx.moveTo(0, Math.round(h / 2));
    ctx.lineTo(w, Math.round(h / 2));
    ctx.stroke();

    ctx.restore();
  }

  /**
   * Draws single-track waveform overview with bar-style rendering and mirror reflection.
   * Outgoing track waveform uses amber/gold gradient (#e8a838 to #c67b14).
   * @param {CanvasRenderingContext2D} ctx - Rendering context.
   * @param {number} w - Canvas width.
   * @param {number} h - Canvas height.
   */
  drawSingleWaveform(ctx, w, h) {
    const data = this.waveformDataA;

    // Highlight scheduled transition zone if defined (even before active crossfade begins)
    if (this.transitionZoneStart > 0 && this.transitionZoneStart < 1) {
      const zoneX = this.transitionZoneStart * w;
      const zoneW = w - zoneX;

      ctx.save();
      // Transition zone background
      ctx.fillStyle = 'rgba(255, 255, 255, 0.03)';
      ctx.fillRect(zoneX, 0, zoneW, h);

      // Transition start boundary line
      ctx.strokeStyle = 'rgba(232, 168, 56, 0.25)';
      ctx.setLineDash([4, 4]);
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(zoneX, 0);
      ctx.lineTo(zoneX, h);
      ctx.stroke();

      // Subtle badge indicating transition zone
      ctx.fillStyle = 'rgba(232, 168, 56, 0.4)';
      ctx.font = '9px sans-serif';
      ctx.fillText('TRANSITION ZONE', zoneX + 6, 14);
      ctx.restore();
    }

    if (!data || data.length === 0) {
      // Subtle placeholder line if no waveform data is loaded
      ctx.save();
      ctx.strokeStyle = 'rgba(232, 168, 56, 0.15)';
      ctx.setLineDash([6, 6]);
      ctx.beginPath();
      ctx.moveTo(0, h / 2);
      ctx.lineTo(w, h / 2);
      ctx.stroke();
      ctx.restore();
      return;
    }

    const centerY = h / 2;
    const amplitude = h * 0.4; // Utilize 80% total height (40% up, 40% down)

    // Render bar-style waveform with upper peaks and mirror reflection
    this.drawWaveformBars(
      ctx,
      data,
      0,
      w,
      centerY,
      amplitude,
      '#e8a838', // Amber top
      '#c67b14', // Amber/gold bottom
      0.65       // Reflection scale
    );

    // Dim the portion of the track that has already played
    if (this.playheadPosition > 0) {
      ctx.save();
      ctx.fillStyle = 'rgba(10, 10, 15, 0.4)';
      ctx.fillRect(0, 0, this.playheadPosition * w, h);
      ctx.restore();
    }
  }

  /**
   * Draws dual-track waveform view during active transitions.
   * Track A (outgoing) occupies the upper area in amber (#e8a838 to #c67b14).
   * Track B (incoming) occupies the lower area in blue (#4a9eff to #2563eb).
   * The transition overlap zone is highlighted with a clear visual alignment.
   * @param {CanvasRenderingContext2D} ctx - Rendering context.
   * @param {number} w - Canvas width.
   * @param {number} h - Canvas height.
   */
  drawDualWaveform(ctx, w, h) {
    const dataA = this.waveformDataA;
    const dataB = this.waveformDataB;
    if (!dataA) return;

    const zoneStart = Math.max(0, Math.min(1, this.transitionZoneStart));
    const zoneWidth = Math.max(0.01, 1 - zoneStart);
    const startX = zoneStart * w;
    const overlapW = zoneWidth * w;

    // 1. Highlight Transition Zone Background
    ctx.save();
    ctx.fillStyle = 'rgba(255, 255, 255, 0.05)';
    ctx.fillRect(startX, 0, overlapW, h);

    // Transition zone border line
    ctx.strokeStyle = 'rgba(74, 158, 255, 0.4)';
    ctx.lineWidth = 1;
    ctx.setLineDash([3, 3]);
    ctx.beginPath();
    ctx.moveTo(startX, 0);
    ctx.lineTo(startX, h);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.restore();

    // 2. Track A (Outgoing): upper 45% of canvas (center ~30%, amplitude ~22%)
    const centerA = h * 0.3;
    const ampA = h * 0.22;
    this.drawWaveformBars(
      ctx,
      dataA,
      0,
      w,
      centerA,
      ampA,
      '#e8a838', // Amber top
      '#c67b14', // Gold bottom
      0.5        // Mirror reflection scale
    );

    // 3. Track B (Incoming): lower 45% of canvas (center ~70%, amplitude ~22%)
    // Aligns starting from the transition zone start
    if (dataB && dataB.length > 0) {
      const centerB = h * 0.7;
      const ampB = h * 0.22;
      this.drawWaveformBars(
        ctx,
        dataB,
        startX,
        overlapW,
        centerB,
        ampB,
        '#4a9eff', // Blue top
        '#2563eb', // Blue bottom
        0.5        // Mirror reflection scale
      );
    }

    // 4. Track deck badges
    ctx.save();
    ctx.font = '10px monospace';
    // Deck A label
    ctx.fillStyle = '#e8a838';
    ctx.fillText('DECK A (OUTGOING)', 8, 16);
    // Deck B label
    if (dataB) {
      ctx.fillStyle = '#4a9eff';
      ctx.fillText('DECK B (INCOMING)', startX + 8, h - 8);
    }
    ctx.restore();

    // 5. Dim the portion that has already played
    if (this.playheadPosition > 0) {
      ctx.save();
      ctx.fillStyle = 'rgba(10, 10, 15, 0.35)';
      ctx.fillRect(0, 0, this.playheadPosition * w, h);
      ctx.restore();
    }
  }

  /**
   * High-performance bar-style waveform renderer with mirror reflection.
   * Groups peak samples into crisp vertical bars (small rectangles) with gradients.
   *
   * @param {CanvasRenderingContext2D} ctx - Rendering context.
   * @param {Array<{min: number, max: number}>|Array<number>} data - Waveform peak array.
   * @param {number} startX - Horizontal start position in pixels.
   * @param {number} width - Render width in pixels.
   * @param {number} centerY - Vertical centerline position in pixels.
   * @param {number} amplitude - Vertical peak amplitude in pixels.
   * @param {string} colorTop - Gradient top color hex.
   * @param {string} colorBottom - Gradient bottom color hex.
   * @param {number} [mirrorScale=0.6] - Reflection height scaling factor.
   */
  drawWaveformBars(ctx, data, startX, width, centerY, amplitude, colorTop, colorBottom, mirrorScale = 0.6) {
    if (!data || data.length === 0 || width <= 1) return;

    ctx.save();

    // Bar dimensions: 2px bar with 1px spacing for a high-density, professional look
    const barPitch = 3;
    const numBars = Math.max(1, Math.floor(width / barPitch));
    const barWidth = Math.max(1, (width / numBars) - 1);
    const dataLen = data.length;

    // Cache pre-computed peak values per bar slice
    const upperHeights = new Float32Array(numBars);
    const mirrorHeights = new Float32Array(numBars);

    for (let i = 0; i < numBars; i++) {
      const sliceStart = Math.floor((i / numBars) * dataLen);
      const sliceEnd = Math.max(sliceStart + 1, Math.floor(((i + 1) / numBars) * dataLen));

      let maxVal = 0;
      let minVal = 0;

      for (let j = sliceStart; j < sliceEnd && j < dataLen; j++) {
        const item = data[j];
        if (typeof item === 'number') {
          const val = Math.abs(item);
          if (val > maxVal) {
            maxVal = val;
            minVal = -val;
          }
        } else if (item && typeof item === 'object') {
          if (typeof item.max === 'number' && item.max > maxVal) maxVal = item.max;
          if (typeof item.min === 'number' && item.min < minVal) minVal = item.min;
        }
      }

      // Clamp peaks
      maxVal = Math.min(1, Math.max(0, maxVal));
      minVal = Math.min(0, Math.max(-1, minVal));

      // Calculate bar heights (ensure minimum 1px visibility for quiet sections)
      upperHeights[i] = Math.max(1, maxVal * amplitude);
      mirrorHeights[i] = Math.max(1, Math.abs(minVal) * amplitude * mirrorScale);
    }

    // Pass 1: Render upper positive peak bars using gradient fill
    const gradientTop = ctx.createLinearGradient(0, centerY - amplitude, 0, centerY);
    gradientTop.addColorStop(0, colorTop);
    gradientTop.addColorStop(1, colorBottom);
    ctx.fillStyle = gradientTop;

    for (let i = 0; i < numBars; i++) {
      const barH = upperHeights[i];
      const x = startX + (i / numBars) * width;
      ctx.fillRect(x, centerY - barH, barWidth, barH);
    }

    // Pass 2: Render lower mirror/reflection bars with translucent fade
    const gradientMirror = ctx.createLinearGradient(0, centerY, 0, centerY + amplitude * mirrorScale);
    gradientMirror.addColorStop(0, colorBottom + '99'); // ~60% alpha at center
    gradientMirror.addColorStop(1, colorBottom + '1a'); // ~10% alpha at bottom edge
    ctx.fillStyle = gradientMirror;

    for (let i = 0; i < numBars; i++) {
      const mirrorH = mirrorHeights[i];
      const x = startX + (i / numBars) * width;
      ctx.fillRect(x, centerY, barWidth, mirrorH);
    }

    ctx.restore();
  }

  /**
   * Draws the X-shaped equal-power crossfade curves during transitions.
   * Amber curve shows Deck A fading out; Blue curve shows Deck B fading in.
   * Includes dashed position marker tracking current crossfade progress.
   * @param {CanvasRenderingContext2D} ctx - Rendering context.
   * @param {number} w - Canvas width.
   * @param {number} h - Canvas height.
   */
  drawCrossfadeCurves(ctx, w, h) {
    if (this.transitionProgress < 0) return;

    const zoneStart = Math.max(0, Math.min(1, this.transitionZoneStart));
    const zoneWidth = Math.max(0.01, 1 - zoneStart);
    const startX = zoneStart * w;
    const curveW = zoneWidth * w;

    // Center area for crossfade curves
    const curveH = Math.min(44, Math.max(26, h * 0.22));
    const curveY = (h / 2) - (curveH / 2);

    ctx.save();

    // Subtle backdrop panel for crossfade curves
    ctx.fillStyle = 'rgba(12, 14, 20, 0.7)';
    ctx.fillRect(startX, curveY - 4, curveW, curveH + 8);
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
    ctx.lineWidth = 1;
    ctx.strokeRect(startX, curveY - 4, curveW, curveH + 8);

    // 1. Outgoing Track Volume Curve (Amber, fading out with equal power cosine)
    ctx.beginPath();
    ctx.strokeStyle = '#e8a838';
    ctx.lineWidth = 2;
    for (let i = 0; i <= 60; i++) {
      const p = i / 60;
      const x = startX + p * curveW;
      const vol = Math.cos(p * Math.PI / 2); // Equal power fade-out
      const y = curveY + curveH * (1 - vol);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // 2. Incoming Track Volume Curve (Blue, fading in with equal power sine)
    ctx.beginPath();
    ctx.strokeStyle = '#4a9eff';
    ctx.lineWidth = 2;
    for (let i = 0; i <= 60; i++) {
      const p = i / 60;
      const x = startX + p * curveW;
      const vol = Math.sin(p * Math.PI / 2); // Equal power fade-in
      const y = curveY + curveH * (1 - vol);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // 3. Current transition progress vertical indicator marker
    const pClamped = Math.max(0, Math.min(1, this.transitionProgress));
    const markerX = startX + pClamped * curveW;

    ctx.beginPath();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.7)';
    ctx.lineWidth = 1.5;
    ctx.setLineDash([3, 3]);
    ctx.moveTo(markerX, curveY - 4);
    ctx.lineTo(markerX, curveY + curveH + 4);
    ctx.stroke();
    ctx.setLineDash([]);

    // Volume level intersection dots on curves
    const volA = Math.cos(pClamped * Math.PI / 2);
    const volB = Math.sin(pClamped * Math.PI / 2);
    const dotYA = curveY + curveH * (1 - volA);
    const dotYB = curveY + curveH * (1 - volB);

    // Dot A (Amber)
    ctx.fillStyle = '#e8a838';
    ctx.beginPath();
    ctx.arc(markerX, dotYA, 3.5, 0, Math.PI * 2);
    ctx.fill();

    // Dot B (Blue)
    ctx.fillStyle = '#4a9eff';
    ctx.beginPath();
    ctx.arc(markerX, dotYB, 3.5, 0, Math.PI * 2);
    ctx.fill();

    // Percentage badge above crossfade marker
    const pctText = `${Math.round(pClamped * 100)}%`;
    ctx.font = '9px monospace';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.85)';
    ctx.textAlign = 'center';
    ctx.fillText(pctText, markerX, curveY - 7);
    ctx.textAlign = 'start';

    ctx.restore();
  }

  /**
   * Draws the playhead vertical cursor line with high-intensity glow and top pointer.
   * @param {CanvasRenderingContext2D} ctx - Rendering context.
   * @param {number} w - Canvas width.
   * @param {number} h - Canvas height.
   */
  drawPlayhead(ctx, w, h) {
    const x = Math.round(this.playheadPosition * w);

    ctx.save();

    // Glowing white line effect
    ctx.shadowColor = '#ffffff';
    ctx.shadowBlur = 8;
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 2;

    ctx.beginPath();
    ctx.moveTo(x, 0);
    ctx.lineTo(x, h);
    ctx.stroke();

    // Top indicator arrow / marker
    ctx.fillStyle = '#ffffff';
    ctx.beginPath();
    ctx.moveTo(x - 5, 0);
    ctx.lineTo(x + 5, 0);
    ctx.lineTo(x, 8);
    ctx.closePath();
    ctx.fill();

    // Inverted bottom indicator arrow
    ctx.beginPath();
    ctx.moveTo(x - 5, h);
    ctx.lineTo(x + 5, h);
    ctx.lineTo(x, h - 8);
    ctx.closePath();
    ctx.fill();

    ctx.restore();
  }

  /**
   * Draws a real-time live frequency spectrum analyzer HUD.
   * Visualizes live frequency energy using data from AnalyserNodes.
   * Shows Deck A frequencies (amber) and/or Deck B frequencies (blue).
   * @param {CanvasRenderingContext2D} ctx - Rendering context.
   * @param {number} w - Canvas width.
   * @param {number} h - Canvas height.
   */
  drawLiveFrequencyBars(ctx, w, h) {
    const hasA = Boolean(this.analyserDataA && this.analyserDataA.length > 0);
    const hasB = Boolean(this.analyserDataB && this.analyserDataB.length > 0);

    // If neither deck has live frequency data, skip rendering the HUD
    if (!hasA && !hasB) return;

    ctx.save();

    const panelW = Math.min(160, Math.max(100, w * 0.22));
    const panelH = Math.min(38, Math.max(26, h * 0.2));
    const panelX = w - panelW - 12;
    const panelY = 10;

    // Translucent HUD background
    ctx.fillStyle = 'rgba(10, 12, 18, 0.8)';
    ctx.fillRect(panelX, panelY, panelW, panelH);
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.lineWidth = 1;
    ctx.strokeRect(panelX, panelY, panelW, panelH);

    // Determine how many channels to display
    const channels = [];
    if (hasA) channels.push({ data: this.analyserDataA, color: '#e8a838', name: 'A' });
    if (hasB) channels.push({ data: this.analyserDataB, color: '#4a9eff', name: 'B' });

    const numBands = 16;
    const chanHeight = (panelH - 6) / channels.length;

    channels.forEach((chan, cIdx) => {
      const topY = panelY + 3 + cIdx * chanHeight;
      const barSlotW = (panelW - 8) / numBands;
      const barW = Math.max(1, barSlotW - 1);
      const dataLen = chan.data.length;

      ctx.fillStyle = chan.color;

      for (let b = 0; b < numBands; b++) {
        // Logarithmic grouping for frequency distribution (favoring low-mid frequencies)
        const logFactor = Math.pow(b / (numBands - 1), 1.8);
        const dataIdx = Math.min(dataLen - 1, Math.floor(logFactor * (dataLen * 0.75)));
        let val = chan.data[dataIdx] || 0;

        // Normalize value (support standard 0-255 uint8 or 0.0-1.0 floats)
        const normalized = val > 1 ? Math.min(1, val / 255) : Math.min(1, Math.max(0, val));
        const barH = Math.max(1, normalized * (chanHeight - 2));
        const barX = panelX + 4 + b * barSlotW;
        const barY = topY + chanHeight - barH - 1;

        ctx.fillRect(barX, barY, barW, barH);
      }
    });

    // Subtle label in top-left of spectrum panel
    ctx.font = '8px monospace';
    ctx.fillStyle = 'rgba(255, 255, 255, 0.4)';
    ctx.fillText('LIVE SPECTRUM', panelX + 4, panelY - 3);

    ctx.restore();
  }

  /**
   * Starts the 60 FPS requestAnimationFrame animation loop.
   */
  startAnimation() {
    if (this.animationId !== null) return;

    const animate = () => {
      this.render();
      this.animationId = requestAnimationFrame(animate);
    };

    this.animationId = requestAnimationFrame(animate);
  }

  /**
   * Stops the ongoing animation loop.
   */
  stopAnimation() {
    if (this.animationId !== null) {
      cancelAnimationFrame(this.animationId);
      this.animationId = null;
    }
  }

  /**
   * Cleans up resources, animation loops, and event listeners.
   */
  destroy() {
    this.stopAnimation();
    if (typeof window !== 'undefined') {
      window.removeEventListener('resize', this._boundResize);
    }
    this.waveformDataA = null;
    this.waveformDataB = null;
    this.analyserDataA = null;
    this.analyserDataB = null;
  }
}
