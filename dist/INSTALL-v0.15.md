# MusicTransi v0.15 preview — Android

Install MusicTransi-v0.15-preview.apk over your existing app using Update. Do not
uninstall or clear storage; your saved library and activity should remain intact.

## Included

- Audio quality: open the app's settings and find Streaming audio quality.
  Choose Low, Medium, High or Very high. These select available streams, not
  guaranteed bitrates; changes apply to newly loaded streams. Existing downloads
  and already prepared songs retain their quality. Lossless streaming is not
  available from this source. Local lossless files are not a bit-perfect output
  promise because the mixer/device can process decoded audio.
- Dolby: a shortcut opens device Sound settings for supported system effects.
  Genuine Atmos mixes are not supplied by this source and are not synthesized.

This revision still uses versionCode 15 / 0.15-preview as requested; Android
will show the same version number when updating the earlier v0.15 APK.

- Startup: stream probes no longer hold the shared extractor lock. Recently
  resolved audio URLs are reused for up to two minutes, kept only in memory,
  and invalidated on player errors. Next-track preparation waits for the current
  song to become ready before fetching, reducing startup network contention.
  Network/provider waits can still occur; no real-device latency measurement yet.
- Activity: circular artist-catalogue portraits with a seven-day metadata cache.
  Ambiguous/missing matches use initials. A provider's artist image can be a logo
  rather than a photo; album covers are not substituted for portraits.
- Home-screen widgets: Compact and Artwork, both with previous, play/pause, next,
  like and tap-to-open. Long-press your home screen, choose Widgets, find
  MusicTransi and drag either widget onto the screen. Pick a song in the app first.
  They update on playback events with no periodic polling. After process loss,
  saved title/artist remain and artwork reloads when playback resumes.
- Notifications and media-session artwork use a centre-cropped square bitmap.
  Your Android/Samsung system controls the surrounding media-card layout.

Android Auto's existing browser and audio/haptic engine are preserved. This build
does not add typed dashboard search or the remaining listening-data migration.
Please test startup, rapid song changes, pause/resume from both widgets, liking,
artist photos and notification artwork. Car checks should be performed parked.
