/**
 * @file Analyzer.js
 * @description BPM detection, waveform extraction, and energy analysis module for DJ AutoMix.
 *
 * Implements:
 * 1. BPM Detection: Downsampling, low-pass filtering, RMS framing, spectral flux onset
 *    detection, autocorrelation, sub-sample parabolic interpolation, and octave correction.
 * 2. Waveform Data Extraction: Downsampled min/max amplitude peaks for visualization.
 * 3. Energy Profiling: RMS segmentation and normalization (0.0 - 1.0) for structure analysis.
 * 4. Outro Detection: Finds sustained energy drop in the final 30% of track for DJ transition-out.
 */

// ─── Mathematical & Algorithmic Constants ─────────────────────────────────────
const TARGET_PROCESSING_RATE = 22050; // Resampling target rate (Hz) for fast analysis
const FRAME_SIZE = 1024;              // Frame length for RMS energy analysis
const HOP_SIZE = 512;                 // Hop size between consecutive frames (50% overlap)
const MIN_BPM = 60;                   // Minimum tempo boundary
const MAX_BPM = 200;                  // Maximum tempo boundary
const LOWPASS_CUTOFF = 200;           // Cutoff frequency (Hz) for bass/kick isolation
const DEFAULT_BPM = 120.0;            // Fallback tempo for silent or unidentifiable tracks
const MAX_ANALYSIS_SECONDS = 90;      // Cap analysis window duration to optimize performance

/**
 * Applies a 2nd-order Butterworth low-pass filter to isolate low frequencies (kicks, bass).
 * Uses direct form II transposed / standard difference equation.
 *
 * @param {Float32Array} input - Input signal samples.
 * @param {number} sampleRate - Sample rate of input signal.
 * @param {number} cutoff - Lowpass cutoff frequency in Hz.
 * @returns {Float32Array} Filtered signal.
 */
function applyLowPassFilter(input, sampleRate, cutoff) {
  const len = input.length;
  const output = new Float32Array(len);

  // Biquad filter coefficients calculation (Q = 1 / sqrt(2) ≈ 0.7071)
  const w0 = (2 * Math.PI * cutoff) / sampleRate;
  const cosW0 = Math.cos(w0);
  const sinW0 = Math.sin(w0);
  const alpha = sinW0 / (2 * 0.70710678);

  const b0 = (1 - cosW0) * 0.5;
  const b1 = 1 - cosW0;
  const b2 = (1 - cosW0) * 0.5;
  const a0 = 1 + alpha;
  const a1 = -2 * cosW0;
  const a2 = 1 - alpha;

  // Normalized coefficients
  const nb0 = b0 / a0;
  const nb1 = b1 / a0;
  const nb2 = b2 / a0;
  const na1 = a1 / a0;
  const na2 = a2 / a0;

  let x1 = 0;
  let x2 = 0;
  let y1 = 0;
  let y2 = 0;

  for (let i = 0; i < len; i++) {
    const x0 = input[i];
    const y0 = nb0 * x0 + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2;
    output[i] = y0;
    x2 = x1;
    x1 = x0;
    y2 = y1;
    y1 = y0;
  }

  return output;
}

/**
 * Performs quadratic/parabolic interpolation around a local peak to obtain
 * sub-frame fractional lag accuracy.
 *
 * Given three points (x - 1, a), (x, b), (x + 1, c), where b is the discrete maximum:
 * delta = 0.5 * (a - c) / (a - 2*b + c)
 *
 * @param {number} a - Correlation value at lag - 1
 * @param {number} b - Correlation value at lag
 * @param {number} c - Correlation value at lag + 1
 * @returns {number} Fractional delta offset (-1 < delta < 1)
 */
function parabolicInterpolation(a, b, c) {
  const denom = a - 2 * b + c;
  if (Math.abs(denom) < 1e-12) return 0;
  const delta = (0.5 * (a - c)) / denom;
  return Math.abs(delta) < 1 ? delta : 0;
}

/**
 * Detect BPM of an audio buffer using onset-detection and autocorrelation.
 *
 * Steps:
 * 1. Preprocessing: Resamples/decimates to ~22050Hz and applies lowpass filter.
 * 2. Energy Calculation: Frames (1024 samples, 512 hop), RMS energy, spectral flux,
 *    and half-wave rectification.
 * 3. Autocorrelation: Unbiased autocorrelation over 60-200 BPM lag range, peak picking,
 *    and parabolic interpolation for high precision.
 * 4. Post-processing: Octave correction (doubling < 80, halving > 160) and rounding to 0.5 BPM.
 *
 * @param {AudioBuffer} audioBuffer - Input audio buffer to analyze.
 * @returns {Promise<number>} Detected tempo in BPM rounded to nearest 0.5.
 */
export async function detectBPM(audioBuffer) {
  // Input validation
  if (!audioBuffer || audioBuffer.length === 0 || audioBuffer.duration === 0) {
    return DEFAULT_BPM;
  }

  const sampleRate = audioBuffer.sampleRate;
  const numChannels = audioBuffer.numberOfChannels;
  const totalSamples = audioBuffer.length;
  const duration = audioBuffer.duration;

  // Efficiency optimization: Analyze up to 60-90s from an active portion of track
  // Skips quiet intros (first 10% or 10-15s)
  let startSec = 0;
  let analyzeSec = duration;

  if (duration > 45) {
    startSec = Math.min(15, duration * 0.1);
    analyzeSec = Math.min(MAX_ANALYSIS_SECONDS, duration - startSec);
  }

  const startSample = Math.floor(startSec * sampleRate);
  const sampleCount = Math.floor(analyzeSec * sampleRate);

  // 1. Preprocessing: Resample / decimate to ~22050Hz
  const decimation = Math.max(1, Math.round(sampleRate / TARGET_PROCESSING_RATE));
  const procSampleRate = sampleRate / decimation;
  const procLength = Math.floor(sampleCount / decimation);

  if (procLength < FRAME_SIZE * 2) {
    return DEFAULT_BPM; // Insufficient audio length for framing
  }

  const ch0 = audioBuffer.getChannelData(0);
  const ch1 = numChannels > 1 ? audioBuffer.getChannelData(1) : null;

  // Extract and downsample to Float32Array (stereo downmixed to mono)
  const downsampled = new Float32Array(procLength);
  let totalEnergy = 0;

  if (ch1) {
    for (let i = 0; i < procLength; i++) {
      const idx = startSample + i * decimation;
      const sample = (ch0[idx] + ch1[idx]) * 0.5;
      downsampled[i] = sample;
      totalEnergy += sample * sample;
    }
  } else {
    for (let i = 0; i < procLength; i++) {
      const idx = startSample + i * decimation;
      const sample = ch0[idx];
      downsampled[i] = sample;
      totalEnergy += sample * sample;
    }
  }

  // Handle silence or extremely quiet audio
  if (totalEnergy < 1e-6) {
    return DEFAULT_BPM;
  }

  // Conceptual low-pass filter by processing low-frequency energy (kick/bass isolation)
  const filtered = applyLowPassFilter(downsampled, procSampleRate, LOWPASS_CUTOFF);

  // Check if filtered signal retained enough energy; if track lacks sub-bass (e.g. acoustic),
  // fall back to wideband signal so beat detection remains effective.
  let filteredEnergy = 0;
  for (let i = 0; i < procLength; i++) {
    filteredEnergy += filtered[i] * filtered[i];
  }
  const procSignal = (filteredEnergy > totalEnergy * 0.05) ? filtered : downsampled;

  // 2. Energy Calculation:
  // Split into frames of 1024 samples with 512-sample hop
  const numFrames = Math.floor((procLength - FRAME_SIZE) / HOP_SIZE);
  if (numFrames < 16) {
    return DEFAULT_BPM;
  }

  const rmsValues = new Float32Array(numFrames);
  for (let f = 0; f < numFrames; f++) {
    const frameStart = f * HOP_SIZE;
    let sumSq = 0;
    for (let i = 0; i < FRAME_SIZE; i++) {
      const s = procSignal[frameStart + i];
      sumSq += s * s;
    }
    rmsValues[f] = Math.sqrt(sumSq / FRAME_SIZE);
  }

  // Calculate spectral flux (difference between consecutive frames' energy)
  // and half-wave rectify (only positive changes = onsets)
  const onset = new Float32Array(numFrames);
  onset[0] = 0;
  for (let f = 1; f < numFrames; f++) {
    const diff = rmsValues[f] - rmsValues[f - 1];
    onset[f] = diff > 0 ? diff : 0;
  }

  // Mean-center onset strength signal for zero-mean autocorrelation
  let onsetSum = 0;
  for (let f = 0; f < numFrames; f++) {
    onsetSum += onset[f];
  }
  const onsetMean = onsetSum / numFrames;

  const centeredOnset = new Float32Array(numFrames);
  for (let f = 0; f < numFrames; f++) {
    centeredOnset[f] = onset[f] - onsetMean;
  }

  // 3. Autocorrelation:
  // Search for peaks in the lag range corresponding to 60-200 BPM
  // BPM = 60 * sampleRate / (hopSize * lag)
  // => lag = 60 * sampleRate / (hopSize * BPM)
  const minLag = Math.floor((60 * procSampleRate) / (HOP_SIZE * MAX_BPM));
  const maxLag = Math.ceil((60 * procSampleRate) / (HOP_SIZE * MIN_BPM));

  // Compute unbiased sample autocorrelation for lag range
  const corr = new Float32Array(maxLag + 3);
  for (let lag = Math.max(1, minLag - 2); lag <= maxLag + 2; lag++) {
    let sum = 0;
    const count = numFrames - lag;
    for (let n = 0; n < count; n++) {
      sum += centeredOnset[n] * centeredOnset[n + lag];
    }
    corr[lag] = count > 0 ? sum / count : 0;
  }

  // Search for local peaks in [minLag, maxLag]
  const peaks = [];
  for (let lag = minLag; lag <= maxLag; lag++) {
    if (corr[lag] > corr[lag - 1] && corr[lag] > corr[lag + 1]) {
      peaks.push({ lag, val: corr[lag] });
    }
  }

  let bestLag = -1;
  let maxCorr = -Infinity;

  if (peaks.length > 0) {
    peaks.sort((a, b) => b.val - a.val);
    bestLag = peaks[0].lag;
    maxCorr = peaks[0].val;
  } else {
    // If no distinct local peak, take global max in range
    for (let lag = minLag; lag <= maxLag; lag++) {
      if (corr[lag] > maxCorr) {
        maxCorr = corr[lag];
        bestLag = lag;
      }
    }
  }

  // Guard against uninformative / negative correlation
  if (bestLag === -1 || maxCorr <= 0) {
    return DEFAULT_BPM;
  }

  // Parabolic interpolation around the peak for sub-frame fractional lag accuracy
  let exactLag = bestLag;
  if (bestLag > minLag && bestLag < maxLag) {
    const delta = parabolicInterpolation(corr[bestLag - 1], corr[bestLag], corr[bestLag + 1]);
    exactLag = bestLag + delta;
  }

  // Convert lag to raw BPM: BPM = 60 * sampleRate / (hopSize * lag)
  const rawBpm = (60 * procSampleRate) / (HOP_SIZE * exactLag);
  let bpm = rawBpm;

  // 4. Post-processing:
  // - If BPM < 80, try doubling it (e.g. 2-beat measure detection of 120-160 BPM music)
  // - If BPM > 160, try halving it (e.g. 16th note hi-hat detection of 80-100 BPM music)
  if (bpm < 80) {
    const halfLag = Math.round(exactLag * 0.5);
    if (halfLag >= minLag && halfLag <= maxLag) {
      const isPeak = corr[halfLag] > (corr[halfLag - 1] || 0) && corr[halfLag] > (corr[halfLag + 1] || 0);
      const hasSupport = corr[halfLag] > maxCorr * 0.35;
      if (isPeak || hasSupport || bpm < 70) {
        bpm *= 2;
      }
    } else if (bpm * 2 <= 160) {
      bpm *= 2;
    }
  } else if (bpm > 160) {
    const doubleLag = Math.round(exactLag * 2);
    if (doubleLag >= minLag && doubleLag <= maxLag) {
      const isPeak = corr[doubleLag] > (corr[doubleLag - 1] || 0) && corr[doubleLag] > (corr[doubleLag + 1] || 0);
      const hasSupport = corr[doubleLag] > maxCorr * 0.65;
      if (isPeak && hasSupport) {
        bpm /= 2;
      }
    }
  }

  // Round to nearest 0.5 BPM
  const roundedBpm = Math.round(bpm * 2) / 2;
  return Math.min(MAX_BPM, Math.max(MIN_BPM, roundedBpm));
}

/**
 * Generate waveform overview data for visualization.
 * Downsamples the AudioBuffer into N segments, calculating the peak positive
 * and negative amplitude for each segment.
 *
 * @param {AudioBuffer} audioBuffer - Input audio buffer.
 * @param {number} [numSamples=800] - Number of overview data points to generate.
 * @returns {Array<{min: number, max: number}>} Array of peak amplitude objects {min, max}.
 */
export function generateWaveformData(audioBuffer, numSamples = 800) {
  if (!audioBuffer || audioBuffer.length === 0) {
    return [];
  }

  const count = Math.max(1, Math.floor(numSamples || 800));
  const totalSamples = audioBuffer.length;
  const numChannels = audioBuffer.numberOfChannels;
  const ch0 = audioBuffer.getChannelData(0);
  const ch1 = numChannels > 1 ? audioBuffer.getChannelData(1) : null;

  const result = new Array(count);
  const blockSize = totalSamples / count;

  for (let i = 0; i < count; i++) {
    const start = Math.floor(i * blockSize);
    const end = Math.min(totalSamples, Math.floor((i + 1) * blockSize));

    let minVal = 0;
    let maxVal = 0;

    if (start < end) {
      if (ch1) {
        // Multi-channel / stereo: inspect extreme amplitudes across both channels
        for (let j = start; j < end; j++) {
          const s0 = ch0[j];
          const s1 = ch1[j];

          if (s0 > maxVal) maxVal = s0;
          if (s0 < minVal) minVal = s0;
          if (s1 > maxVal) maxVal = s1;
          if (s1 < minVal) minVal = s1;
        }
      } else {
        // Mono
        for (let j = start; j < end; j++) {
          const s = ch0[j];
          if (s > maxVal) maxVal = s;
          if (s < minVal) minVal = s;
        }
      }
    }

    // Clamp peaks to standard audio range [-1.0, 1.0]
    result[i] = {
      min: Math.max(-1.0, Math.min(0.0, minVal)),
      max: Math.min(1.0, Math.max(0.0, maxVal)),
    };
  }

  return result;
}

/**
 * Analyze energy profile of a track throughout its duration.
 * Divides the track into uniform time segments, computes the RMS energy of each,
 * and normalizes values to the 0.0 - 1.0 range.
 *
 * Helps identify intros, breakdowns, drops, builds, and outros.
 *
 * @param {AudioBuffer} audioBuffer - Input audio buffer.
 * @param {number} [segmentDuration=0.5] - Duration of each segment in seconds.
 * @returns {Array<number>} Array of normalized energy values in [0.0, 1.0].
 */
export function analyzeEnergy(audioBuffer, segmentDuration = 0.5) {
  if (!audioBuffer || audioBuffer.length === 0 || audioBuffer.duration === 0) {
    return [];
  }

  const durationSec = segmentDuration > 0 ? segmentDuration : 0.5;
  const sampleRate = audioBuffer.sampleRate;
  const totalSamples = audioBuffer.length;
  const segmentSamples = Math.max(1, Math.floor(durationSec * sampleRate));
  const numSegments = Math.ceil(totalSamples / segmentSamples);

  const numChannels = audioBuffer.numberOfChannels;
  const ch0 = audioBuffer.getChannelData(0);
  const ch1 = numChannels > 1 ? audioBuffer.getChannelData(1) : null;

  const rawEnergies = new Float32Array(numSegments);
  let maxEnergy = 0;

  for (let i = 0; i < numSegments; i++) {
    const start = i * segmentSamples;
    const end = Math.min(totalSamples, start + segmentSamples);
    const count = end - start;

    let sumSq = 0;
    if (count > 0) {
      if (ch1) {
        // Stereo acoustic power average
        for (let j = start; j < end; j++) {
          const s0 = ch0[j];
          const s1 = ch1[j];
          sumSq += (s0 * s0 + s1 * s1) * 0.5;
        }
      } else {
        // Mono
        for (let j = start; j < end; j++) {
          const s = ch0[j];
          sumSq += s * s;
        }
      }
      const rms = Math.sqrt(sumSq / count);
      rawEnergies[i] = rms;
      if (rms > maxEnergy) {
        maxEnergy = rms;
      }
    } else {
      rawEnergies[i] = 0;
    }
  }

  // Normalize to 0.0 - 1.0
  const normalized = new Array(numSegments);
  const scale = maxEnergy > 0 ? 1.0 / maxEnergy : 0;

  for (let i = 0; i < numSegments; i++) {
    normalized[i] = Math.min(1.0, Math.max(0.0, rawEnergies[i] * scale));
  }

  return normalized;
}

/**
 * Find the best transition-out point (where the outro begins).
 *
 * Algorithm:
 * 1. Inspects the last 30% of the track's energy profile.
 * 2. Identifies where energy consistently drops below 70% of the track's average energy.
 * 3. Returns that time point in seconds.
 * 4. If no clear outro is found, returns trackDuration - 30 (default 30s before end).
 *
 * @param {Array<number>} energyProfile - Array of normalized energy values from analyzeEnergy.
 * @param {number} segmentDuration - Duration of each segment in seconds.
 * @param {number} trackDuration - Total track duration in seconds.
 * @returns {number} Time in seconds where outro begins.
 */
export function findOutroStart(energyProfile, segmentDuration, trackDuration) {
  // Default fallback: 30 seconds before the end of the track
  const defaultOutro = Math.max(0, (trackDuration || 0) - 30);

  if (!energyProfile || energyProfile.length === 0 || !trackDuration || trackDuration <= 0) {
    return defaultOutro;
  }

  // 1. Calculate overall track average energy
  let totalEnergy = 0;
  for (let i = 0; i < energyProfile.length; i++) {
    totalEnergy += energyProfile[i];
  }
  const avgEnergy = totalEnergy / energyProfile.length;
  const threshold = avgEnergy * 0.7;

  // 2. Look at the last 30% of the track's energy profile
  const startIndex = Math.floor(energyProfile.length * 0.7);

  // Require energy to stay below threshold for a sustained duration (at least ~2 seconds)
  // or until the end of the track to avoid false positives on momentary quiet moments.
  const stepSec = segmentDuration > 0 ? segmentDuration : 0.5;
  const minSustainedSegments = Math.max(3, Math.round(2.0 / stepSec));

  for (let i = startIndex; i < energyProfile.length; i++) {
    if (energyProfile[i] < threshold) {
      const remainingSegments = energyProfile.length - i;
      const checkWindow = Math.min(remainingSegments, minSustainedSegments);

      let sustainedBelowCount = 0;
      for (let j = i; j < i + checkWindow; j++) {
        if (energyProfile[j] < threshold) {
          sustainedBelowCount++;
        }
      }

      // Check if sustained throughout the check window
      if (sustainedBelowCount >= checkWindow) {
        // Also verify the remaining tail average stays below threshold
        let tailSum = 0;
        for (let j = i; j < energyProfile.length; j++) {
          tailSum += energyProfile[j];
        }
        const tailAvg = tailSum / remainingSegments;

        if (tailAvg < threshold) {
          const outroTime = i * stepSec;
          return Math.min(outroTime, trackDuration);
        }
      }
    }
  }

  // If no clear outro is found, return default
  return defaultOutro;
}
