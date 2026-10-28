# v0.15 implementation

Policy: planning=deep; context_depth=dependency; verification_depth=impact;
delegation=none. Scope: Android playback startup, artist portraits, two widgets,
square system artwork, associated tests and signed preview packaging.

User confirms Android Auto working on v0.14. Preserve its browser and dual decks.
Decision: release extractor lock before stream probes; cache resolved streams
briefly with invalidation on playback errors; postpone next-song fetching until
the current player is ready. Keep the existing playback buffer thresholds.

Portraits use the music-artist catalogue with exact normalized name matching and
a bounded local cache. Missing/ambiguous results use initials, never album art
passed off as a portrait. Widgets use platform RemoteViews and event updates,
not polling. Use the existing protected media commands for widget transport.

Validation: unit suite, widget/artwork instrumentation compilation, APK build
and signature/version inspection. Real device/car latency and rendering require
runtime checks; do not claim measured improvements without them.

Implemented: 2-minute/eight-item in-memory resolved-stream cache, invalidation on
active/standby errors, stream probes outside the extractor mutex, and waiting for
the active deck to be ready before fetching the next track. Existing 1-second
startup/15–30-second buffering thresholds and mixer/haptic logic are unchanged.

Two RemoteViews providers share protected commands and event-driven state.
Artwork is centre-cropped to a maximum 256×256 bitmap for media metadata/widgets.
Portrait lookup uses a single exact normalized music-artist match, cached for
seven days (negative matches one hour), capped at 200 names. Portrait lookup
still serializes extractor access; a future priority scheduler may improve this.

References checked:
- https://developer.android.com/develop/ui/views/appwidgets
- https://raw.githubusercontent.com/TeamNewPipe/NewPipeExtractor/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeMusicSearchExtractor.java

Build and unit verification: 48 passed, 4 optional network tests skipped; app and
instrumentation sources compiled; assembleDebug succeeded. Device list empty,
so widget layout/crop instrumentation is compiled but not executed. APK signing
certificate matches v0.14. See dist/VERIFICATION-v0.15.md for final checks.

## Same-version audio-quality revision

User explicitly requested retaining version 0.15/versionCode 15. Streaming
preferences are Low (48 kbps target), Medium (128), High (256), and Very high
(highest offered bitrate). Rank available streams at/below target first, then
ascending above target; unknown bitrates last. Cache keys include the preference,
and playback errors invalidate all cached qualities for that song. Persist the
preference in session settings. Existing/preloaded/offline audio is unchanged.

Lossless is visibly unavailable for streaming, with local-file explanation. No
fake lossless transcoding or Atmos toggle. Added system Sound settings shortcut
for device effects; genuine Atmos playback is not claimed by this revision.
Sources: https://support.google.com/youtubemusic/answer/9076559 and
https://professionalsupport.dolby.com/s/article/Enabling-Dolby-Atmos-in-Android-Mobile-media-apps

Validation: deterministic ranking/fallback/preference tests, full unit suite,
app build, then replace v0.15 APK/source/hash together. Prior lint pertains to
the pre-quality revision unless explicitly rerun in the verification report.
