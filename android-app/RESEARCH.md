# Android implementation decisions

## Bloomee comparison

Inspected the user-provided Bloomee 3.0.4+202 ARM64 APK statically and read the matching public release source. It packages Flutter, Rust and mpv. Its separately updated .bex plugins resolve audio streams; its main application plays those streams itself. Spotify/Apple Music importers supply metadata rather than those services' audio.

MusicTransi Android uses the same source/resolver/player separation, implemented in Kotlin with NewPipeExtractor v0.26.5 (YouTube Music search and YouTube audio resolution) and AndroidX Media3 1.9.3 (two ExoPlayer decks). It does not run Bloomee's .bex format, copy the APK, capture another app, or connect to Spotify/Apple Music.

## sony/fxnorm-automix

https://github.com/sony/fxnorm-automix

MIT-licensed research on mixing individual instrument recordings into one finished song using effect normalization and neural models. This is studio stem mixing, not sequencing finished songs into DJ transitions. Its Python/PyTorch workflow and training assumptions do not fit the first lightweight Android player. Potential later reference for loudness/EQ research, not included in the APK.

## congnghetinhtu/SongMix

https://github.com/congnghetinhtu/SongMix

MIT license file; Python/librosa offline whole-file analysis and rendering. Relevant ideas: tempo/energy compatibility, transition boundaries and equal-power fades. The implementation uses heuristic intro/outro detection, and its reported key is the strongest mean chroma bin rather than a complete major/minor key estimator. These should not be treated as proven professional-quality analysis. No Python runtime or model is embedded; no source code from this repository is copied.

The Android engine uses a bounded, equal-power overlap with headroom. Version 0.2 adds per-deck bass attenuation through Android Equalizer, trading low frequencies during the overlap where the effect is supported. It does not yet infer beat grids, downbeats, musical key, vocal activity or phrase boundaries, or time-stretch tracks for DJ matching. The redesigned player displays transition progress without presenting a synthetic plot as a measured waveform.

## Autoplay in 0.2

NewPipe StreamInfo provides related items alongside stream resolution. A bounded metadata cache avoids fetching the same details again for recommendations. Provider order is retained, and queue/history IDs and normalized artist/title pairs exclude repeated recordings. Candidates are limited to 1–15 minutes. If no eligible related items remain, YouTube Music artist search supplies fallback candidates. These are source recommendations, not Spotify/Apple account personalization or measured audio similarity.

The foreground service appends a small batch, prebuffers the next deck, and refills as tracks advance. Manual queue choices precede automatic picks; a deck already audible in a blend stays in place. Load/stop generation checks and coroutine cancellation prevent stale lookups from reviving stopped playback.

## Music haptics

User explicitly selected vibrations following the music, not button feedback.

Android HapticGenerator (API 31+) attaches to each deck's audio session. Media3 audio attributes unmute haptic channels. A capability check disables the setting on unsupported phones; failure to enable the audio effect is surfaced. This generates haptics from playing audio, not a timer/metronome and not Apple's proprietary song-specific haptic tracks. Physical output and Bluetooth behavior require testing on a compatible handset.

References:
- https://developer.android.com/reference/android/media/audiofx/HapticGenerator
- https://developer.apple.com/documentation/mediaaccessibility/music-haptics
- https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5
- https://github.com/HemantKArya/BloomeeTunes/tree/v3.0.4%2B202

## Limits

Unofficial YouTube extraction is subject to upstream changes and regional/network restrictions; the app does not claim guaranteed availability. Source adapters can be replaced independently in code, but this preview does not download executable plugins or auto-update itself. Full-song DJ analysis needs a deliberate CPU/network/cache budget and should not block the playback thread.
# 0.3 implementation notes

Velune 1.1.2 (the supplied universal APK) was inspected as a design reference: Kotlin/Compose, configurable player presentation, minimum Android 8. The Ambient, Studio and Vinyl layouts here are original implementations; no Velune code or assets were copied.

MusicTransi now uses an original onset-based tempo/phase estimator. One 16-second muted incoming preview is analyzed ahead of playback, with a 25-second wall-clock budget. The estimator retains at most 96 onset times over 24 seconds and searches 70–170 BPM. Confidence and ±6% tempo-ratio gates prevent forced matching of incompatible tracks. Media3 performs pitch-preserving speed adjustment. This is not phrase, key or vocal analysis, and perceptual quality depends on the music. Sony's studio stem-mixing research remains a different task from this two-song streaming transition engine.

Radio now accepts YouTube Music album-track entries (ATV), rejects generic video entries, and deduplicates normalized song titles across uploader names. An artist song-search fallback remains available. This unofficial provider interface may change; no account personalization is claimed.

# Library feature references (0.4)

Reviewed the official [Velune repository](https://github.com/nikhilvishwakarma00/Velune) and [BloomeeTunes repository/changelog](https://github.com/HemantKArya/BloomeeTunes/blob/main/CHANGELOG.md) for library and discovery conventions: saved collections, playlists and album browsing. MusicTransi uses an original Compose implementation and Android SQLite for local metadata. No Velune or Bloomee source/assets were copied, and their account sync or offline-download features are not implied here.
