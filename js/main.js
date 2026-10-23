// main.js — Application controller
// Coordinates AudioEngine, Analyzer, AutoMixer, Visualizer, and the UI

import AudioEngine from './AudioEngine.js';
import { detectBPM, generateWaveformData, analyzeEnergy, findOutroStart } from './Analyzer.js';
import AutoMixer from './AutoMixer.js';
import Visualizer from './Visualizer.js';

// ─── Application State ──────────────────────────────────────────────────────
const state = {
  queue: [],           // Array of { file, name, buffer, bpm, duration, waveform, energy }
  currentIndex: -1,    // Index of currently playing track in queue
  isPlaying: false,
  isLoading: false,
};

let engine = null;
let mixer = null;
let visualizer = null;

// ─── DOM References ──────────────────────────────────────────────────────────
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

// ─── Initialize Application ──────────────────────────────────────────────────
async function init() {
  // Audio engine
  engine = new AudioEngine();
  await engine.init();

  // Visualizer
  const canvas = $('#waveform-canvas');
  if (canvas) {
    visualizer = new Visualizer(canvas);
    visualizer.init();
    visualizer.startAnimation();
  }

  // AutoMixer
  mixer = new AutoMixer(engine);
  mixer.onPlaybackUpdate = handlePlaybackUpdate;
  mixer.onTransitionStart = handleTransitionStart;
  mixer.onTransitionUpdate = handleTransitionUpdate;
  mixer.onTransitionEnd = handleTransitionEnd;
  mixer.onTrackEnd = handleTrackEnd;
  mixer.startMonitoring();

  // Bind UI events
  bindEvents();

  // Update initial UI state
  updateControlsUI();
  updateQueueUI();
  updateNowPlayingUI();

  console.log('MusicTransi initialized');
}

// ─── Event Binding ───────────────────────────────────────────────────────────
function bindEvents() {
  // Drop zone
  const dropZone = $('#drop-zone');
  const fileInput = $('#file-input');

  if (dropZone) {
    dropZone.addEventListener('dragover', (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropZone.classList.add('drag-over');
    });

    dropZone.addEventListener('dragleave', (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropZone.classList.remove('drag-over');
    });

    dropZone.addEventListener('drop', (e) => {
      e.preventDefault();
      e.stopPropagation();
      dropZone.classList.remove('drag-over');
      const files = Array.from(e.dataTransfer.files).filter(f => f.type.startsWith('audio/'));
      if (files.length > 0) {
        addFiles(files);
      }
    });

    dropZone.addEventListener('click', () => {
      if (fileInput) fileInput.click();
    });
  }

  if (fileInput) {
    fileInput.addEventListener('change', (e) => {
      const files = Array.from(e.target.files);
      if (files.length > 0) {
        addFiles(files);
        fileInput.value = ''; // Reset so same files can be re-added
      }
    });
  }

  // Transport controls
  const playBtn = $('#play-btn');
  if (playBtn) {
    playBtn.addEventListener('click', togglePlayPause);
  }

  const skipBtn = $('#skip-btn');
  if (skipBtn) {
    skipBtn.addEventListener('click', skipToNext);
  }

  // Crossfade duration
  const crossfadeSelect = $('#crossfade-select');
  if (crossfadeSelect) {
    crossfadeSelect.addEventListener('change', (e) => {
      const bars = parseInt(e.target.value, 10);
      if (mixer && !isNaN(bars)) {
        mixer.setTransitionBars(bars);
      }
    });
  }

  // Volume slider
  const volumeSlider = $('#volume-slider');
  if (volumeSlider) {
    volumeSlider.addEventListener('input', (e) => {
      const vol = parseFloat(e.target.value) / 100; // HTML range is 0-100, engine needs 0-1
      if (engine && !isNaN(vol)) {
        engine.setMasterVolume(vol);
      }
      // Update display
      const volumeValue = $('#volume-value');
      if (volumeValue) volumeValue.textContent = `${Math.round(vol * 100)}%`;
      // Update icon
      const volumeIcon = $('#volume-icon');
      if (volumeIcon) {
        volumeIcon.className = vol === 0 ? 'fa-solid fa-volume-xmark'
          : vol < 0.5 ? 'fa-solid fa-volume-low'
          : 'fa-solid fa-volume-high';
      }
    });
  }

  // Mute button
  const muteBtn = $('#mute-btn');
  let preMuteVolume = 0.8;
  if (muteBtn && volumeSlider) {
    muteBtn.addEventListener('click', () => {
      const currentVol = parseFloat(volumeSlider.value) / 100;
      if (currentVol > 0) {
        preMuteVolume = currentVol;
        volumeSlider.value = 0;
        engine.setMasterVolume(0);
      } else {
        volumeSlider.value = preMuteVolume * 100;
        engine.setMasterVolume(preMuteVolume);
      }
      volumeSlider.dispatchEvent(new Event('input'));
    });
  }

  // Automix toggle
  const automixToggle = $('#automix-toggle');
  if (automixToggle) {
    automixToggle.addEventListener('change', (e) => {
      if (mixer) {
        mixer.enabled = e.target.checked;
      }
    });
  }

  // Clear queue
  const clearBtn = $('#clear-queue-btn');
  if (clearBtn) {
    clearBtn.addEventListener('click', clearQueue);
  }

  // Waveform click-to-seek
  const waveformCanvas = $('#waveform-canvas');
  if (waveformCanvas) {
    waveformCanvas.addEventListener('click', (e) => {
      if (!state.isPlaying && state.currentIndex < 0) return;
      const rect = waveformCanvas.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const progress = clickX / rect.width;
      seekTo(progress);
    });
  }

  // Keyboard shortcuts
  document.addEventListener('keydown', (e) => {
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT') return;
    switch (e.code) {
      case 'Space':
        e.preventDefault();
        togglePlayPause();
        break;
      case 'ArrowRight':
        e.preventDefault();
        skipToNext();
        break;
    }
  });
}

// ─── File Loading & Analysis ────────────────────────────────────────────────
async function addFiles(files) {
  const loadingOverlay = $('#loading-overlay');
  const loadingText = $('#loading-text');

  for (const file of files) {
    try {
      // Show loading state
      state.isLoading = true;
      if (loadingOverlay) loadingOverlay.classList.add('visible');
      if (loadingText) loadingText.textContent = `Analyzing: ${file.name}`;

      // Decode audio
      const audioBuffer = await engine.loadFile(file);

      // Detect BPM
      let bpm = 0;
      try {
        bpm = await detectBPM(audioBuffer);
      } catch (err) {
        console.warn(`BPM detection failed for ${file.name}:`, err);
        bpm = 120; // Fallback BPM
      }

      // Generate waveform data
      const waveform = generateWaveformData(audioBuffer, 800);

      // Analyze energy
      const energy = analyzeEnergy(audioBuffer, 0.5);

      // Create track entry
      const track = {
        file,
        name: cleanTrackName(file.name),
        buffer: audioBuffer,
        bpm: Math.round(bpm * 2) / 2, // Round to nearest 0.5
        duration: audioBuffer.duration,
        waveform,
        energy,
      };

      state.queue.push(track);
      updateQueueUI();

      // If nothing is playing, auto-start
      if (state.currentIndex < 0) {
        await loadAndPlayTrack(0);
      } else {
        // Pre-load next track on inactive deck if it's the next in queue
        preloadNextTrack();
      }
    } catch (err) {
      console.error(`Failed to load ${file.name}:`, err);
      showNotification(`Failed to load: ${file.name}`, 'error');
    }
  }

  state.isLoading = false;
  if (loadingOverlay) loadingOverlay.classList.remove('visible');
}

// Clean up filename for display
function cleanTrackName(filename) {
  return filename
    .replace(/\.[^/.]+$/, '')           // Remove extension
    .replace(/[-_]/g, ' ')               // Replace dashes/underscores with spaces
    .replace(/\b\w/g, c => c.toUpperCase()); // Title case
}

// ─── Playback Control ───────────────────────────────────────────────────────
async function loadAndPlayTrack(index) {
  if (index < 0 || index >= state.queue.length) return;

  const track = state.queue[index];
  const activeDeck = engine.getActiveDeck();

  // Load track onto active deck
  activeDeck.load(track.buffer, {
    name: track.name,
    bpm: track.bpm,
    duration: track.duration,
  });

  // Set waveform on visualizer
  if (visualizer) {
    visualizer.setWaveformData('A', track.waveform);
    visualizer.setWaveformData('B', null);
    visualizer.setTransitionState(-1, 0);
    visualizer.setPlayheadPosition(0);
  }

  state.currentIndex = index;

  // Start playback
  activeDeck.play();
  state.isPlaying = true;

  // Preload next track
  preloadNextTrack();

  updateNowPlayingUI();
  updateControlsUI();
  updateQueueUI();
}

function preloadNextTrack() {
  const nextIndex = state.currentIndex + 1;
  if (nextIndex >= state.queue.length) return;

  const nextTrack = state.queue[nextIndex];
  const inactiveDeck = engine.getInactiveDeck();

  inactiveDeck.load(nextTrack.buffer, {
    name: nextTrack.name,
    bpm: nextTrack.bpm,
    duration: nextTrack.duration,
  });

  updateNextTrackUI(nextTrack);
}

function togglePlayPause() {
  if (state.queue.length === 0) return;

  // Resume audio context on first interaction (autoplay policy)
  if (engine.context && engine.context.state === 'suspended') {
    engine.context.resume();
  }

  const activeDeck = engine.getActiveDeck();

  if (state.isPlaying) {
    activeDeck.pause();
    // Also pause inactive deck if it's playing (during transition)
    const inactiveDeck = engine.getInactiveDeck();
    if (inactiveDeck.playing) inactiveDeck.pause();
    state.isPlaying = false;
  } else {
    if (state.currentIndex < 0 && state.queue.length > 0) {
      // Nothing has played yet — start from the top
      loadAndPlayTrack(0);
      return;
    }
    activeDeck.resume();
    // Also resume inactive deck if it was paused during a transition
    if (mixer.isTransitioning) {
      const inactiveDeck = engine.getInactiveDeck();
      if (inactiveDeck.isLoaded() && !inactiveDeck.playing) {
        inactiveDeck.resume();
      }
    }
    state.isPlaying = true;
  }

  updateControlsUI();
}

function skipToNext() {
  if (state.currentIndex + 1 >= state.queue.length) {
    showNotification('No more tracks in queue', 'info');
    return;
  }

  if (mixer) {
    // If next track isn't preloaded yet, load it now
    const inactiveDeck = engine.getInactiveDeck();
    if (!inactiveDeck.isLoaded()) {
      preloadNextTrack();
    }
    mixer.skipToNext();
  }
}

function seekTo(progress) {
  const activeDeck = engine.getActiveDeck();
  if (!activeDeck.buffer) return;

  const offset = progress * activeDeck.buffer.duration;
  const wasPlaying = activeDeck.playing;

  activeDeck.stop();
  if (wasPlaying) {
    activeDeck.play(0, offset);
    state.isPlaying = true;
  } else {
    activeDeck.pauseOffset = offset;
  }
}

function clearQueue() {
  // Keep current track, clear the rest
  if (state.currentIndex >= 0) {
    const currentTrack = state.queue[state.currentIndex];
    state.queue = [currentTrack];
    state.currentIndex = 0;
  } else {
    state.queue = [];
    state.currentIndex = -1;
  }

  // Reset inactive deck
  engine.getInactiveDeck().reset();

  updateQueueUI();
  updateNextTrackUI(null);
  showNotification('Queue cleared', 'info');
}

// ─── AutoMixer Callbacks ────────────────────────────────────────────────────
function handlePlaybackUpdate(currentTime, duration) {
  if (!state.isPlaying) return;

  const progress = duration > 0 ? currentTime / duration : 0;

  // Update visualizer playhead
  if (visualizer) {
    visualizer.setPlayheadPosition(progress);
  }

  // Update progress bar
  updateProgressUI(currentTime, duration);
}

function handleTransitionStart() {
  const nextIndex = state.currentIndex + 1;
  if (nextIndex < state.queue.length) {
    const nextTrack = state.queue[nextIndex];

    // Set transition zone on visualizer
    if (visualizer) {
      const activeDeck = engine.getActiveDeck();
      const transitionDuration = mixer.transitionDuration;
      const trackDuration = activeDeck.buffer ? activeDeck.buffer.duration : 1;
      const zoneStart = Math.max(0, 1 - (transitionDuration / trackDuration));

      visualizer.setWaveformData('B', nextTrack.waveform);
      visualizer.setTransitionState(0, zoneStart);
    }
  }

  updateNowPlayingUI();
  showNotification('Mixing...', 'info');
}

function handleTransitionUpdate(progress) {
  if (visualizer) {
    visualizer.transitionProgress = progress;
  }
}

function handleTransitionEnd() {
  // Advance to next track
  state.currentIndex++;

  // Move current waveform to deck A slot
  if (state.currentIndex < state.queue.length) {
    const currentTrack = state.queue[state.currentIndex];
    if (visualizer) {
      visualizer.setWaveformData('A', currentTrack.waveform);
      visualizer.setWaveformData('B', null);
      visualizer.setTransitionState(-1, 0);
      visualizer.setPlayheadPosition(0);
    }
  }

  // Preload next track
  preloadNextTrack();

  updateNowPlayingUI();
  updateQueueUI();
  showNotification('Transition complete', 'info');
}

function handleTrackEnd() {
  // Current track ended without a transition (automix might be off or no next track)
  const nextIndex = state.currentIndex + 1;

  if (nextIndex < state.queue.length) {
    // Load and play next track directly
    state.currentIndex = nextIndex;
    const track = state.queue[nextIndex];
    const activeDeck = engine.getActiveDeck();

    activeDeck.load(track.buffer, {
      name: track.name,
      bpm: track.bpm,
      duration: track.duration,
    });
    activeDeck.play();

    if (visualizer) {
      visualizer.setWaveformData('A', track.waveform);
      visualizer.setWaveformData('B', null);
      visualizer.setTransitionState(-1, 0);
      visualizer.setPlayheadPosition(0);
    }

    preloadNextTrack();
    updateNowPlayingUI();
    updateQueueUI();
  } else {
    // Queue exhausted
    state.isPlaying = false;
    updateControlsUI();
    showNotification('Queue finished', 'info');
  }
}

// ─── UI Updates ──────────────────────────────────────────────────────────────
function updateNowPlayingUI() {
  const currentTrack = state.currentIndex >= 0 ? state.queue[state.currentIndex] : null;

  // Update Deck A card
  const titleEl = $('#current-title');
  const bpmEl = $('#current-bpm');
  const durationEl = $('#current-duration');
  const totalEl = $('#current-total');

  if (titleEl) {
    titleEl.textContent = currentTrack ? currentTrack.name : 'No track loaded';
    titleEl.classList.toggle('empty', !currentTrack);
  }
  if (bpmEl) {
    bpmEl.innerHTML = currentTrack
      ? `<i class="fa-solid fa-bolt"></i> ${currentTrack.bpm} BPM`
      : `<i class="fa-solid fa-bolt"></i> -- BPM`;
  }
  if (durationEl) {
    durationEl.innerHTML = currentTrack
      ? `<i class="fa-regular fa-clock"></i> ${formatTime(currentTrack.duration)}`
      : `<i class="fa-regular fa-clock"></i> --:--`;
  }
  if (totalEl) {
    totalEl.textContent = currentTrack ? formatTime(currentTrack.duration) : '--:--';
  }

  // Artist field — use filename-derived name since we don't have tags
  const artistEl = $('#current-artist');
  if (artistEl) {
    artistEl.textContent = currentTrack ? 'Local File' : '';
  }
}

function updateNextTrackUI(track) {
  const titleEl = $('#next-title');
  const bpmEl = $('#next-bpm');
  const durationEl = $('#next-duration');
  const artistEl = $('#next-artist');
  const cueTimeEl = $('#next-cue-time');

  if (titleEl) {
    titleEl.textContent = track ? track.name : 'Add tracks to queue';
    titleEl.classList.toggle('empty', !track);
  }
  if (bpmEl) {
    bpmEl.innerHTML = track
      ? `<i class="fa-solid fa-bolt"></i> ${track.bpm} BPM`
      : `<i class="fa-solid fa-bolt"></i> -- BPM`;
  }
  if (durationEl) {
    durationEl.innerHTML = track
      ? `<i class="fa-regular fa-clock"></i> ${formatTime(track.duration)}`
      : `<i class="fa-regular fa-clock"></i> --:--`;
  }
  if (artistEl) {
    artistEl.textContent = track ? 'Local File' : '';
  }
  if (cueTimeEl && track) {
    // Calculate where the mix-in starts
    const activeDeck = engine.getActiveDeck();
    if (activeDeck && activeDeck.buffer) {
      const transitionDur = mixer.calcTransitionDuration(activeDeck.trackInfo?.bpm);
      const cueTime = activeDeck.buffer.duration - transitionDur;
      cueTimeEl.textContent = `${formatTime(Math.max(0, cueTime))} of Deck A`;
    }
  }

  // Update tempo match display
  const pitchEl = document.querySelector('.pitch-value');
  if (pitchEl && track) {
    const activeDeck = engine.getActiveDeck();
    const currentBPM = activeDeck?.trackInfo?.bpm;
    if (currentBPM && track.bpm) {
      const diff = ((currentBPM / track.bpm) - 1) * 100;
      pitchEl.textContent = `${diff >= 0 ? '+' : ''}${diff.toFixed(1)}%`;
    }
  }
}

function updateQueueUI() {
  const listEl = $('#queue-list');
  if (!listEl) return;

  if (state.queue.length === 0) {
    listEl.innerHTML = '<div class="queue-empty">Drop audio files above to get started</div>';
    return;
  }

  listEl.innerHTML = state.queue.map((track, index) => {
    const isCurrent = index === state.currentIndex;
    const isPast = index < state.currentIndex;
    const statusClass = isCurrent ? 'current' : isPast ? 'past' : '';

    return `
      <div class="track-item ${statusClass}" data-index="${index}">
        <div class="track-number">${index + 1}</div>
        <div class="track-info">
          <div class="track-name">${escapeHtml(track.name)}</div>
          <div class="track-details">
            <span class="bpm-badge small">${track.bpm} BPM</span>
            <span class="duration">${formatTime(track.duration)}</span>
          </div>
        </div>
        ${isCurrent ? '<div class="now-playing-indicator"><i class="fas fa-volume-up"></i></div>' : ''}
        ${!isCurrent && !isPast ? `<button class="remove-track-btn" data-index="${index}" title="Remove"><i class="fas fa-times"></i></button>` : ''}
      </div>
    `;
  }).join('');

  // Bind remove buttons
  listEl.querySelectorAll('.remove-track-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const idx = parseInt(btn.dataset.index, 10);
      removeTrack(idx);
    });
  });

  // Bind click-to-play on future tracks
  listEl.querySelectorAll('.track-item:not(.current):not(.past)').forEach(item => {
    item.addEventListener('dblclick', () => {
      const idx = parseInt(item.dataset.index, 10);
      if (idx > state.currentIndex) {
        // Stop current, jump to this track
        engine.getActiveDeck().stop();
        engine.getInactiveDeck().stop();
        mixer.isTransitioning = false;
        loadAndPlayTrack(idx);
      }
    });
  });
}

function updateControlsUI() {
  const playBtn = $('#play-btn');
  if (playBtn) {
    const icon = playBtn.querySelector('i');
    if (icon) {
      icon.className = state.isPlaying ? 'fas fa-pause' : 'fas fa-play';
    }
    playBtn.classList.toggle('playing', state.isPlaying);
  }
}

function updateProgressUI(currentTime, duration) {
  const progressBar = $('#progress-bar');
  if (progressBar) {
    const pct = duration > 0 ? (currentTime / duration) * 100 : 0;
    progressBar.style.width = `${pct}%`;
  }

  const timeDisplay = $('#time-display');
  if (timeDisplay) {
    timeDisplay.textContent = `${formatTime(currentTime)} / ${formatTime(duration)}`;
  }

  // Also update the deck card's time counter
  const currentTimeEl = $('#current-time');
  if (currentTimeEl) {
    currentTimeEl.textContent = formatTime(currentTime);
  }
}

function removeTrack(index) {
  if (index <= state.currentIndex || index < 0 || index >= state.queue.length) return;

  state.queue.splice(index, 1);
  updateQueueUI();

  // Re-preload next track if needed
  preloadNextTrack();
}

// ─── Notifications ───────────────────────────────────────────────────────────
function showNotification(message, type = 'info') {
  const container = $('#notifications');
  if (!container) return;

  const notification = document.createElement('div');
  notification.className = `notification ${type}`;
  notification.textContent = message;
  container.appendChild(notification);

  // Animate in
  requestAnimationFrame(() => {
    notification.classList.add('show');
  });

  // Auto-remove after 3 seconds
  setTimeout(() => {
    notification.classList.remove('show');
    setTimeout(() => notification.remove(), 300);
  }, 3000);
}

// ─── Utilities ───────────────────────────────────────────────────────────────
function formatTime(seconds) {
  if (!isFinite(seconds) || seconds < 0) return '0:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

// ─── Start Application ──────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  init().catch(err => {
    console.error('Failed to initialize MusicTransi:', err);
  });
});
