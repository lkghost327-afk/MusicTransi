# 0.12 preview — Artwork motion and suggestions

- Directional, fading artwork transitions: next exits left, previous exits right. Ambient artwork dissolves and album colours gradually blend, including lyrics/queue backgrounds. Late covers fade in and image-loading state is keyed to its own track.
- Autoplay excludes thirty recently heard recordings across sessions. Discover ranking uses liked/listened artists while retaining provider-related candidates. Extra upload filters reject compact bass/speed edits, fan/TikTok edits and concert variants.
- No changes to the audio transition engine or haptic scheduling. Physical animation frame times are unmeasured; the previous audio beat-sync issue remains unresolved.

# 0.11 preview — Library imports and sharing

- Import CSV/JSON playlists, likes and saved-album metadata with match preview, unmatched reporting, cancellation and atomic duplicate-safe saving. Exported files only; no direct account connection.
- Multi-song playlist search/add, create-and-add in one picker, and private resized custom covers. Existing library schema upgrades preserve playlists.
- Select up to four lyric lines (280 characters), preview a card and open Android sharing.
- Selected-song search history; Recently Played second on Home; Discover and expanded mixes with a ten-track publication minimum.
- Faded player artwork, softer glass edges, aligned collapse control and bounded existing animations.
- Faster audio-pulse haptic scheduling and configurable timing; physical motor timing remains unmeasured. Beat-sync regression remains unresolved; iPhone unchanged.
See ../dist/INSTALL-v0.11.md for import formats and usage.

# MusicTransi Android — development preview

## Version 0.10 — Artwork-led browsing

Made for you creates playable and saveable mixes from bounded local listening history and likes. Home cards, search categories, a playlist cover shelf, icon navigation and a compact bottom player follow the supplied visual references. Ambient playback, lyrics and the in-player queue use artwork-derived backgrounds. Short screen-entry animations and stable lyric geometry keep motion bounded. Existing player styles, library actions and downloads remain. See VERIFICATION.md and ../dist/INSTALL-v0.10.md.

## Version 0.9 — Record, discovery and offline collections

Adds a slowly rotating disc-only Record player, listening-based home suggestions, stable lyric highlighting, playlist/liked-song/album downloads, and notification artwork, transport and Like controls. See VERIFICATION.md for completed checks and remaining limits, including the inherited beat-sync failure.

## Version 0.8 — Lyrics controls and listening reliability

Lyrics keeps transport and scrubbing controls visible in a fixed footer. Recommendation deduplication now accounts for artist/duration and available radio variety. Outgoing beat estimation uses only heard onsets and reuses unchanged evidence; cover loading gains a bounded disk HTTP cache and concurrency limits. See `../dist/INSTALL-v0.8.md`, `ROADMAP-STATUS.md` and `VERIFICATION.md` for remaining device gates.

## Version 0.7 — Glass, gestures and Orbit

Mix settings now offer optional Liquid glass styling across the player controls, mini-player and navigation. This lightweight Android interpretation uses translucent gradients and highlights, not Apple's proprietary renderer. The new Orbit design places album art inside an actual playback-progress dial. Swipe artwork left/right to change songs, up for queue or down to collapse; swipe the mini-player up to expand. Buttons remain available. See `../dist/INSTALL-v0.7.md`.

## Version 0.6 — Appearance, lyrics and conservative beat alignment

Persistent System/Light/Dark appearance, a spinning vinyl deck with animated tonearm, on-demand LRCLIB synchronized/plain lyrics, bounded beat-phase correction and stricter tempo eligibility. Transition envelopes now use the outgoing audio timeline; ordinary blends respect the selected length, and complementary bass shaping avoids the former deep dip. Vibration changes are limited to short attack/release shaping on amplitude-capable motors and suppressing the weakest pulses. See `../dist/INSTALL-v0.6.md` and VERIFICATION.md. Real-song listening and physical haptic feel still require handset validation.

## Version 0.5 — Queue controls and companion

Song menus now offer Play next and Play later. Queue supports moving an existing entry next, up/down reordering, shuffling upcoming songs, clearing upcoming songs without stopping current playback, and Stop & clear all. Clear upcoming disables Autoplay; audible incoming tracks are protected during a transition. The new `bridge` JVM module shares the music-source code for the experimental iPhone port and is not required for Android playback. See `../dist/INSTALL-v0.5.md` and `../ios-app/WINDOWS-IPHONE-GUIDE.md`.

## Version 0.4 — Library and albums

- Like/unlike from the player or song menu; liked songs form a persistent collection.
- Create, rename and delete local playlists. Add individual songs or full albums; duplicate track IDs are ignored. Remove or move tracks up/down without changing other collections.
- Library includes liked songs, playlists and saved albums. Play All, Shuffle and Queue are available for collections. Shuffle changes only the playback queue, not saved playlist order.
- Search has separate Songs and Albums filters. Open an album to load its ordered track list, including continuation pages, save it to Library or add its songs to a playlist.
- Library metadata is stored using Android's built-in SQLite on a background dispatcher. No new runtime dependency, account or backend is required. Clearing the listening queue does not clear the library.
- This is local storage, not YouTube/Spotify/Apple account sync. Saving metadata does not download audio; online tracks and album details still need internet. Uninstalling or clearing app data removes the library.

Install the 0.4 APK over the previous preview to retain queue/settings. See `../dist/INSTALL-v0.4.md`.

## Version 0.3

- Stream resolution checks the offered audio URL, prefers AAC/M4A and tries up to three formats. Individual provider failures remain possible; errors appear beside the song title.

- Music-only radio uses YouTube Music album-track recommendations. Song-title deduplication blocks lyric/video reuploads across channels; trailers, remixes and bass-boosted variants are filtered. This is provider radio, not a personal listening-profile model.
- Fixed a divide-by-zero in cover decoding. Images have bounded memory, retries, and video-thumbnail fallback; resolved streams refresh artwork.
- Full-player **Vibes on/off** button, Android 8+ audio-onset vibration fallback, intensity and motor test in settings. Native audio haptics remain available on supported Android 12+ hardware.
- Experimental beat matching: bounded 16-second muted preview of the next song, onset-based BPM/phase estimation, confidence gate, pitch-preserving speed adjustment limited to ±6%, beat-aligned start, bass handoff and gradual return to normal speed. Incompatible or uncertain tracks use a short blend. No heavyweight model or analysis server.
- Ambient, Studio and Vinyl player styles, and a Not interested action.

## Previous foundation

- Autoplay is on by default: choose one online song and related tracks are added and prebuffered automatically. Provider ranking is preserved; repeated IDs/recordings and short clips/long mixes are filtered. Artist search is a fallback, not account-personalized recommendations.
- Manually queued tracks go before automatic suggestions (after an already-audible transition). Playing a new search result removes old future automatic picks. Autoplay can be disabled or retried from Queue.
- Artwork-led light library/search screens, a persistent mini-player, and a full-screen player with cover-derived background colors, large controls, and a smaller cover when paused.
- Optional bass handoff attenuates low frequencies on each deck during the overlap using the device equalizer. Unsupported devices retain ordinary crossfading. This is not beat synchronization.

Native Android 8+ app. The original HTML/JavaScript prototype in the parent directory is preserved.

## Included

- YouTube Music song search through the independent NewPipeExtractor adapter.
- Audio-only stream resolution and native Media3 playback; no server or API key to configure.
- Two-deck equal-power crossfade, 0–16 seconds, prebuffering and overlap limited for short tracks.
- Queue persistence, local audio import via Android's file picker, seeking, previous/next, and queue removal.
- Foreground media service, media notification, lock-screen/headset controls, audio focus and pause on headphone disconnection.
- Optional music-synchronized haptics with native or audio-pulse modes depending on hardware.
- Live transition progress in the full-screen player.

## Build

Use JDK 17+ and Android SDK 36. `local.properties` points to the SDK on this machine and is not portable; regenerate it or set `ANDROID_HOME` on another machine.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed development build, not a Play Store release.

Opt-in live source check (makes a music search, resolves one result and reads a stream prefix):

```powershell
.\gradlew.bat :app:testDebugUnitTest -PliveSourceTest=true --tests '*SourceSmokeTest'
```

## Try it

Search for an artist/song, then tap a result to play immediately or **+** to queue it. Autoplay adds related tracks for online songs. Tap the mini-player for the full-screen player. **Mix** controls overlap, bass handoff, Autoplay, and **Feel the music**. **Add files** plays audio you already have; local files need a manual queue. Playback stays in the service when the Activity is hidden. Stop playback from the notification or settings.

Haptics are off by default. Toggle **Vibes off** at the top of the full player to enable them. Settings includes intensity and **Test vibration**. Android 11 uses detected audio pulses; devices without amplitude control use their default motor strength. Android settings and manufacturer background policies can suppress vibration. Physical feel and Bluetooth timing require real-phone testing.

## Scope and known limits

- Beat matching is experimental and works best with steady, clearly accented music at similar tempos. It does not detect musical key, downbeats/phrases or vocals, and is not equivalent to Apple's AutoMix. RMS attenuation is not calibrated LUFS normalization. A small scheduling error is possible with two independent players. Short tracks or late queue additions skip advance analysis.
- YouTube extraction is unofficial, requires internet, and may fail due to provider changes or network/region restrictions. It does not grant streaming/mixing rights.
- No Spotify/Apple Music streaming or playlist import in this preview. No background interception of other apps.
- An Android media foreground service is visible to the system; it cannot guarantee survival after force-stop or vendor battery restrictions.
- Source code and research findings are in this directory. See RESEARCH.md for the two user-provided repositories.

## Third-party software

NewPipeExtractor v0.26.5 is GPL-3.0-or-later. Any distribution incorporating it must satisfy the applicable GPL terms, including corresponding source; retaining this editable project is important. Media3, AndroidX and OkHttp use Apache-2.0. Bloomee, Sony FxNorm and SongMix code has not been copied into this app. See THIRD_PARTY.md.
