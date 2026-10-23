# Improvement roadmap: implementation and remaining gates

## This update

Release gate (19 October): 10/11 Android runtime tests passed; the synthetic beat-sync test failed. Added failure diagnostics are awaiting a rebuild because automatic approval review was unavailable (model capacity). Resolve this before calling 0.8 verified or claiming improved transition reliability.

- Android: full-height lyrics with fixed transport/seek footer and regression coverage for synced and missing lyrics.
- Shared recommendations: artist-aware recording comparison, duration-aware decorated-upload matching, and variety when radio offers another artist.
- Android timing: outgoing grid uses heard events only and caches unchanged eligible event sets. Existing bounded tempo correction and safe fallback remain.
- Artwork: bounded dedicated disk HTTP cache and concurrent-request limit; no audio caching.
- iPhone source: synced lyrics, reusable transport controls, scrubbing on release, Orbit progress ring, material glass option and player/queue gestures. Added continuation when a pending radio request finishes after reaching the queue end.
- Mac companion: rebuilt from the shared recommendation source before packaging.

## Must be verified on the user's devices

The current host is Windows. Having a Mac available to the user does not give this session access to macOS. Run the supplied Check-Mac-Build.command and then sign/install through Xcode. This remains a build gate, not an assumed pass.

On Galaxy M30s / Android 11 and the iPhone, check a 30-minute playlist, screen-off playback, Bluetooth, pause/resume, phone interruption and network loss. Compare at least 12 fixed song pairs: close tempos, distant tempos, quiet introductions, sparse percussion, live/variable tempo and vocal overlap. Record the pair and the point where a blend feels wrong. Preserve the prior APK for comparison; do not uninstall merely to update.

Measure startup time, memory and battery under the same brightness, network, audio route and duration with glass/lyrics open versus closed. No physical battery or motor-feel improvement is claimed from cache changes alone. Existing Android haptic shaping is retained to preserve the feel already approved by the user.

## Remaining engine work

Phrase/downbeat detection, vocal-overlap avoidance, and a shared sample clock are not implemented. Reliable beat-phase estimates are a prerequisite; adding heavy separation models before handset profiling would undermine the lightweight target. The current fix is evidence-based timing correction, not Apple AutoMix parity.

## Remaining iPhone work

Compile and run 0.8 first. Full Vinyl/Studio parity, reliable music-driven haptics and the beat engine remain outstanding. Standalone extraction feasibility and its acceptance gates are documented in ../ios-app/STANDALONE-RESEARCH.md. No unverified remote extraction relay is enabled.

## Release acceptance checklist

- Lyrics controls remain visible on a small screen after scrolling and during missing/error states.
- Scrubbing changes position only on release; lyric following resumes at that position.
- Existing queue, library, PCM, online playback and synthetic beat-transition checks pass.
- Recommendation regressions cover repeated uploads, different artists with identical titles and available artist variety.
- Build, lint, signature and archive checks have fresh evidence after final relevant edits.
- iPhone source validation is clearly separated from unperformed Xcode/device validation.
