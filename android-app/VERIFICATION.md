# Android 0.12 verification — 23 October 2026

- Production/test APK assembly passed after the lyric-card header change. Final lint passed; warnings remain. Earlier combined commands exceeded their 300-second verification deadlines; smaller standalone checks passed without weakening assertions.
- JVM tests: 37 discovered, 33 passed, four opt-in network tests skipped, zero failures/errors. New cases cover familiar/liked-artist ranking and compact altered-upload labels.
- Eleven final Android 11 runtime tests passed: directional artwork motion and settling, four lyric playback/selection UI checks, and six import/search-history/cover/schema/share-card checks. The card test verifies album pixels in the header and the restricted sharing URI. Evidence: v0.12-runtime-tests.txt.
- Album motion is a 520-ms directional slide/fade; Ambient artwork dissolves over 1200 ms and colours interpolate over 1400 ms. Image-loading state is keyed by track, and late images fade in. Runtime motion uses synthetic tracks, not a subjective real-device smoothness benchmark.
- Lyric cards place a square cover, title and artist above the selected lyrics. A four-second cover timeout falls back to a note tile. Cover loading is off the main thread; generated files remain private until explicitly shared.
- Autoplay excludes 30 recently heard songs across sessions, current queue and rejected tracks. Discover uses listening/like affinity while preserving related candidates; provider relevance remains imperfect. No live catalog quality guarantee or exhaustive export-format test.
- Debug-signed development preview, versionCode 12 / 0.12-preview. Physical M30s frame times and haptic latency remain unmeasured. Audio transitions, haptics and iPhone source are unchanged; the prior beat-sync regression remains unresolved.

---

# Android 0.11 preview verification — 23 October 2026

- Twelve Android 11 runtime checks passed (13.894 seconds): CSV/JSON parsing, duplicate-safe atomic import and rollback, separate bounded search-selection history, lyric-card dimensions and private sharing URI, private cover resizing/persistence, schema migration preserving existing playlists, lyric playback controls and selection/preview, library persistence and validation.
- The initial runtime run exposed a missing Share lines toolbar. Added the controls; all twelve tests then passed. A later glass-material-only update does not alter these paths.
- JVM suite: 35 discovered, 31 passed, 4 opt-in network checks skipped, zero errors/failures. Fixtures cover sparse mix expansion to at least ten tracks and separate pulse timing thresholds. Live catalog mix quality and arbitrary provider export formats were not exhaustively tested.
- Imports support recognized CSV/JSON metadata, with explicit match review. No direct provider-account synchronization or Apple XML support. Covers are resized private copies; sharing grants access only to the generated lyric-card cache folder.
- Glass uses cached translucent fills, radial highlights, tinted reflections, inset rims and a soft shadow. It is a lightweight cross-version approximation, not Apple's live refraction renderer. Existing short navigation/artwork animations remain.
- Physical Galaxy M30s motor timing, battery impact and animation frame times remain unmeasured. Timing calibration is provided. The inherited beat-sync regression remains unresolved; the transition engine and iPhone source are unchanged.
- This is a debug-signed development preview, versionCode 11 / versionName 0.11-preview. See INSTALL-v0.11.md for usage and supported export examples.

---

# Android v0.10 preview verification — 22 October 2026

- Production and test APK build passed. Final assembleDebug/assembleDebugAndroidTest/lintDebug passed in 3m36s. APK v2 signature verified. No transition-engine or dependency changes; playback now records a bounded local listening event after 30s of audible time.
- Lint: zero errors, 31 warnings. One new style warning recommends declaring the screenEntrance modifier factory as a Modifier extension.
- Android 11: all three LyricsUiTest cases and category/navigation actions passed. Initial queue test failed because performScrollTo cannot locate an uncomposed lazy row. Corrected the test to scroll the list using performScrollToNode; assertions remain intact. Isolated rerun passed: OK (1 test), 8.384s. Thus five distinct design tests passed across two runs.
- Visually inspected Home and Search screenshots (../dist/v0.10-home.png and ../dist/v0.10-search.png). Confirmed Library shelf and collection actions through the layout tree. Extra live lyrics capture was interrupted by an unresponsive emulator; no completed lyrics screenshot or frame-time benchmark.
- Build/test resource contention required stopping Gradle and restarting the emulator. Physical Galaxy M30s animation smoothness and battery impact remain unmeasured. iPhone source is unchanged.
- Inherited beat-sync regression remains unresolved; no transition improvement is claimed. Full audio/download regression suite was not rerun for this visual update.

## Suggested-playlist extension

- assembleDebug, assembleDebugAndroidTest and testDebugUnitTest passed in 2m51s. JVM suite: 34 discovered, 30 passed, 4 opt-in network tests skipped, 0 failures/errors. New tests cover actual elapsed listening time, per-session counting, mix deduplication/filtering, caching, local-only history, cancellation and failed-network fallback.
- Final build/lint passed in 2m21s; APK v2 signature verified after the playlist changes. Four final Android 11 checks passed in 8.424s: suggested-mix Play/Queue/Save controls after scrolling, durable bounded/clearable history plus atomic mix saving, and both existing library persistence/validation tests. Evidence: v0.10-mix-tests.txt. Nine distinct targeted runtime tests passed across the design and playlist runs.

- Final APK: versionCode 10 / 0.10-preview, debug-signed. Test device upgraded successfully without clearing app data.
- Final emulator startup was initially blocked by low disk space. Removed only the unused project test-device RAM snapshot (2.147GB), preserving userdata and prior APKs; installation and the four final runtime tests then passed.

---

# Android v0.9 verification — finalized 22 October 2026

Implemented: Record-only player (18-second rotation), home suggestions, stable lyric typography/follow behavior, offline collections, and notification/media-session artwork and transport/Like controls.

- JVM suite: 31 discovered, 27 passed, four opt-in network tests skipped, zero failures/errors (19 October).
- Final assembleDebug and assembleDebugAndroidTest passed on 21 October. Separate lintDebug passed: zero errors, 30 baseline warnings. A combined invocation exceeded its 300-second wrapper deadline; the separate checks completed successfully.
- Five distinct targeted Android 11 runtime tests ultimately passed: three LyricsUiTest cases, OnlinePlaybackTest, and OfflineDownloadsTest. Lyrics checks cover stable wrapped-line geometry and accessible controls; online playback checks cover advancing playback, notification artwork/icons/actions and persisted Like toggling.
- The initial download test reached 89% but exceeded its 90-second deadline. Only its deadline was increased to 180 seconds; the targeted rerun passed in 108.811 seconds, checking completed file resolution, reopened store state, deduplication and removal. Assertions were not relaxed. Evidence: ../dist/v0.9-runtime-tests.txt and ../dist/v0.9-download-test.txt.
- APK: versionCode 9, versionName 0.9-preview, 17,708,014 bytes. APK signature v2 verified on 21 October. This is a debug-signed development preview.

## Limits

- The inherited v0.8 beat-sync instrumentation failure remains unresolved. This release does not establish improved beat synchronization, and the full regression suite was not rerun.
- Physical Galaxy M30s smoothness/haptic feel, visual lock-screen placement on vendor devices, actual playback with networking disabled, and download recovery across device reboot remain unverified. No manual v0.9 visual inspection was completed.
- Home suggestions are related tracks, not verified new releases. iPhone source is unchanged.

---

# Android 0.8 preview verification — 18 October 2026

## Release gate update — 19 October 2026

The completed full instrumentation run passed 10 of 11 tests in 89.918 seconds. `BeatPlaybackTest.twoTempoTracksUseBeatSyncAndPromote` failed because it did not observe an active beat-synchronized blend. The other tests, including both lyrics tests, passed. See `../dist/v0.8-instrumentation.txt`. This supersedes any assumption that the full suite passed; transition reliability remains unresolved.

The test now collects status/position diagnostics without relaxing its assertion. Rebuilding this diagnostic test was blocked twice by automatic approval review reporting its model at capacity, before the build command executed. That diagnostic-only source edit has not been compiled. The existing APK was built before this test edit. No final signature check or manual visual check was completed for 0.8. Do not treat this candidate as a verified release.

## Completed checks

- Baseline: 26 JVM tests, zero failures/errors, four opt-in live tests skipped. After edits: 29 JVM tests, zero failures/errors/skips with live sources enabled. New coverage distinguishes unrelated same-title songs, checks artist variety and rejects future decoded beats from the heard grid.
- assembleDebug, assembleDebugAndroidTest and lintDebug passed through the gravitas skill's explicit-command verification runner. Lint remains zero errors, 30 warnings.
- The two new lyrics UI tests passed on Android 11 / API 30 in 7.216 seconds: fixed transport controls, seek-to-halfway with lyric following, previous/next callbacks, and controls remaining available without lyrics.
- The first full instrumentation run was interrupted when the emulator process exited after the beat/library tests. Host free physical memory was about 230 MB. A restarted emulator using hardware graphics and one CPU passed the isolated lyrics tests; this does not establish the original exit's cause.
- The rebuilt companion passed authentication rejection, provider validation, search, artwork metadata, 14 ordered album tracks, recommendations, stream resolution and HTTP-range audio byte checks.
- Physical-phone listening quality, battery usage and haptic feel are not verified here. See ROADMAP-STATUS.md. iPhone changes are source-only pending Xcode/device validation.

# Android 0.7 preview verification — 18 October 2026

- assembleDebug and lintDebug passed. Lint: zero errors, 30 warnings.
- Version code 7 / 0.7-preview. Appearance and gesture changes only; no audio-engine changes or new dependencies/permissions.
- Installed over 0.6 on Android 11 / API 30 without clearing data. Manually verified mini-player swipe-up expansion, artwork swipe-down collapse, artwork swipe-up queue navigation, and left-swipe switching from Blinding Lights to Save Your Tears with actual online playback. Orbit progress advanced with the audio position. Orbit and Liquid glass selections survived force-stop/relaunch.
- Visually inspected glass controls and Orbit cover/progress rendering in ../dist/v0.7-orbit-glass.png and ../dist/v0.7-orbit-playing.png. No physical-device performance measurement or iOS port of these changes was performed.
- The 0.6 audio/instrumentation results below are historical, not a rerun on 0.7.

# Android 0.6 preview verification — 18 October 2026

- APK and instrumentation APK compiled for version code 6 / 0.6-preview.
- All nine instrumentation tests passed on the final APK on Android 11 / API 30 in 92.43 seconds, including live online playback, queue edits, library persistence, PCM handling and the synthetic 120-to-118 BPM transition. Log: ../dist/v0.6-instrumentation.txt.
- Lint completed with zero errors and 30 warnings.
- Manually verified dark settings, cover artwork, the playing vinyl/tonearm and parked tonearm on pause. During online playback, the lyrics view visibly advanced to subsequent timestamped lines, highlighting the active line and scrolling with Follow playback enabled. Screenshots: ../dist/v0.6-dark-settings.png, ../dist/v0.6-vinyl-playing.png, ../dist/v0.6-vinyl-paused.png and ../dist/v0.6-lyrics.png. This confirms line-based visual synchronization; exact perceived audio timing and physical motor feel still require handset testing.
- 22 deterministic JVM tests passed; four opt-in live source tests were skipped in this run. Coverage includes queue ordering, beat confidence/tempo limits, phase wrap/correction direction, fade endpoints, bass envelope and LRC timestamp parsing.
- A separate LRCLIB request returned matching Blinding Lights metadata and 40 synchronized timestamp lines. Lyrics text was not included in test logs or bundled in artifacts.
- Comparison of the saved 0.3 and 0.4 source archives showed no blending-loop changes between them; 0.4 added collection/library functions. This does not establish why a particular song pair sounded worse.
- The 0.5 queue instrumentation regression passed on Android 11 in 19.057 seconds, including Play next, reordering, shuffle, clear-upcoming and persisted queue checks.
- Companion tests and iPhone build limitations are documented separately in ../ios-app/VERIFICATION.md. No signed or compiled iPhone app is claimed.

## Current playback limits

Two independent ExoPlayer sinks are not sample-synchronous. The new phase gate/correction is bounded and can fall back to ordinary blending; no downbeat/phrase, key or vocal analysis is provided. Synthetic tests do not establish subjective musical quality. The reported real-song mismatch and the refined motor envelope need listening/feel validation on the user's phone. Lyrics coverage and timing depend on LRCLIB.

## Historical Android 0.4 preview verification — 17 October 2026

- Built assembleDebug and assembleDebugAndroidTest successfully. Lint: zero errors, 30 warnings.
- 22 JVM tests passed with no failures or skips, including live album search and the ordered 14-track After Hours release, plus existing streaming, artwork, radio, queue and beat-grid coverage.
- Nine instrumentation tests passed on the final APK on Android 11 / API 30 in 98.994 seconds. New tests cover library persistence after reopening, duplicate prevention, reordering/removal, independent collections, invalid/deleted playlist handling, and UI creation/rename/delete/add-to-playlist. Existing tests cover PCM preservation, cover decoding, beat-sync promotion, background playback and actual online playback with vibration-toggle state.
- Manually verified Songs/Albums search, album cover display, the 14-track list beginning with Alone Again and Too Late, saving an album, adding the whole album to a newly created Night drive playlist, liking a song and finding all three collections in Library. Screenshots: ../dist/v0.4-album.png and ../dist/v0.4-library.png.
- Version code 4, version name 0.4-preview, Android 8+ (minSdk 26), targetSdk 36. The APK is debug-signed; signature and archive checks are recorded during packaging. Test console output: ../dist/v0.4-instrumentation.txt.

## Current limits

No physical handset was connected. Vibration strength, Bluetooth latency, subjective transition quality and other Android versions remain unverified on hardware. The library stores local metadata, not downloaded audio or account-synced playlists. Provider availability and recommendation quality can vary. Beat synchronization remains experimental and does not establish Apple AutoMix parity; key, phrase and vocal analysis are not implemented.

## Historical Android 0.3 preview verification — 16 October 2026

- Final build passed: assembleDebug, assembleDebugAndroidTest, testDebugUnitTest and lintDebug, with liveSourceTest=true and configuration cache disabled.
- 21 JVM tests passed, none skipped: cover sampling/fallback, song deduplication/filtering (including instrumental/tribute variants), music-only radio parsing, queue policy, onset detection, gain bounds and beat-grid estimation/tempo limits.
- Live source checks passed for search, decoded cover images, stream bytes, recommendations and recommended-track resolution. English and Hindi radio seeds returned different songs across artists; this is a sample, not a guarantee of recommendation quality.
- Android lint: zero errors, 30 warnings. APK v2 signature verified. Version code 3, version name 0.3-preview, Android 8+ (minSdk 26), targetSdk 36.
- **Five instrumentation tests passed on the final APK on Android 11 / API 30**, in 73.389 seconds: real PCM preservation/pulse timing, bitmap decoding, background two-deck transition with pause/resume and persisted queue, a synthetic 120 BPM → 116 BPM beat-sync handoff, and actual online playback of Blinding Lights (playback position advanced, duration and cover metadata present). The online test also checked vibration toggle state and persistence.
- Actual cover artwork was visually observed in the Android search and player screens. The PCM empty-buffer failure found during runtime inspection was fixed before this final suite.
- The full-player Vibes button visibly changed from off to on on Android 11. See ../dist/v0.3-player-final.png for its layout and working cover image; that screenshot is not proof of online playback.
- A manual online check on 15 October encountered HTTP 403. On 16 October both playback-equivalent JVM requests and actual Android online playback passed after adding offered-URL checks and bounded format fallback, preferring AAC/M4A. Intermittent provider restrictions remain possible.

## Limits of this verification

No physical handset was connected. Motor strength, Bluetooth haptic latency, subjective musical transition quality, manufacturer background policies and other Android versions have not been physically validated. The synthetic tempo test proves that the new beat-sync path runs; it does not establish Apple AutoMix parity or reliable beat tracking on every recording. No key, downbeat/phrase or vocal separation is implemented. APK remains a debug-signed development preview. SHA-256 is supplied beside the APK in dist/MusicTransi-v0.3.sha256.

## Historical 0.2 verification (superseded by the results above)

## Passed

- Final Gradle build: `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -PliveSourceTest=true --console=plain --max-workers=2`.
- Seven unit tests covering transition gains/limits, radio filtering, duplicate uploads, provider ordering, manual queue priority, play-now preservation, and protecting an audible incoming deck: zero failures.
- One live source test covering YouTube Music search/artwork, stream resolution and audio bytes, related-song recommendations, and resolving a recommended song: zero failures. This check used the JVM and the real network, not an Android device. Eight total tests, none skipped.
- Android lint: zero errors, 29 warnings. Warnings remain; this is not a production release.
- APK signature verified with Android SDK apksigner (v2 signature).

## Not verified on a device

The Android instrumentation test compiled, but could not run. The local API 36.1 emulator again failed to establish an authorized ADB connection, including a retry with its supported skip-authorization-dialog option. No visual inspection of the redesigned native UI, background-playback, bass-effect audio, or physical-haptics runtime pass is claimed. The test emulator was stopped after the attempt.

`PlaybackJourneyTest` exercises two local audio decks, background transition, pause/resume during overlap, incoming-deck promotion, stop, and persisted queue state. Run it with a connected authorized device:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Music haptics need a compatible physical Android 12+ handset. Check speaker/headphone/Bluetooth routing, vibration timing, audio focus interruptions, and screen-off playback on that handset. Emulator testing cannot establish motor feel or manufacturer battery-management behavior.

## Artifact

`../dist/MusicTransi-v0.2-preview.apk` is a debug-signed Android 8+ preview (17,034,832 bytes), version code 2. It preserves the previous application ID and signing key for in-place updates.

SHA-256: `A1B9C36C28FD3335B677A5AF34D3CA5DC408451A890430D396A69F070B0B7D92`

This preview implements automatic related-song queues and equal-power crossfades with optional device-dependent bass handoff. Professional beat/phrase matching, tempo adjustment, musical-key analysis, vocal-aware transitions, and loudness matching remain unimplemented. See README.md and RESEARCH.md for scope and source findings.

Final glass update: assembleDebug, assembleDebugAndroidTest and lintDebug passed in 4m25s on 23 October. APK v2 signature verified. Lint has no errors; style/dependency warnings remain.


Final Android 11 install succeeded. Visually inspected dist/v0.11-glass.png: Ambient artwork fades into the lower background, the collapse chevron is centered, and rounded translucent controls render without clipped rims. This is a paused/restored track screenshot, not an audio timing test.
