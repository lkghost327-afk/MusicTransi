- Lyric share cards now include cover/title/artist in a top header, with lyrics below.

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
# 0.10 preview — Artwork-led browsing and immersive playback

- Made for you generates Your rotation and Fresh finds with play, queue and atomic save-as-playlist actions. Bounded local history counts 30 seconds of audible playback and can be cleared.
- Home has large Top Picks cards and Recently Played covers. Search has actionable genre/language tiles; Library has a playlist cover shelf alongside its existing management actions.
- Dark mode uses black/charcoal surfaces and a red accent. Icon tabs sit beneath the compact player; System and Light appearance remain available.
- Ambient playback has a cover backdrop. Lyrics and the new in-player queue share artwork-derived colours and pinned seek/transport controls. Queue Edit opens the full reorder/remove tools.
- Short layer-based screen-entry fades/slides, navigation colour transitions and a gentle artwork resize add motion without new dependencies or perpetual effects. Existing slow Record rotation remains.
- Existing beat-sync issue remains unresolved. iPhone source unchanged.

# 0.9 preview â€” Record, discovery and steadier lyrics

- Added Record: a vinyl-only player with an 18-second rotation, pause/resume and lifecycle-aware animation. Existing Vinyl turntable remains available.
- Added a Suggested for you column on Listen now, seeded by current/recent online listening or likes, with play/queue/library actions and bounded cached results.
- Kept lyric typography stable while animating emphasis; manual dragging pauses follow, long seeks jump directly, and playback controls remain pinned.
- Added offline downloads for playlists, Liked Songs and albums, with Wi-Fi-only defaults, progress, retries, removal and offline-first playback.
- Notification and media-session controls include artwork, previous/play-pause/next and Like/Unlike.
- Existing beat-sync regression remains open; iPhone source is unchanged.
# 0.8 preview â€” Lyrics controls and listening reliability

- Lyrics now has fixed play/pause, previous/next and seeking controls, including missing/loading/error states.
- Autoplay recording identity considers artist and upload duration; radio can vary artists after two consecutive songs.
- Beat estimates exclude not-yet-played decoded events and reuse unchanged event sets.
- Added a dedicated bounded artwork HTTP cache and request concurrency/time limits.
- Updated iPhone source with lyrics controls, Orbit-style artwork, material glass option and gestures; Xcode validation remains pending.

# 0.7 preview â€” Glass, gestures and Orbit

- Added an opt-in, persisted Liquid glass treatment for player controls, mini-player and navigation, using inexpensive translucent gradients and luminous edges.
- Added Orbit, a cover-centered progress dial whose marker follows the actual playback position.
- Added artwork drag feedback and swipe shortcuts: previous/next, queue, collapse; mini-player swipe-up expands. Short/diagonal/cancelled gestures do not navigate. Existing buttons remain available.
- No new dependencies, permissions, audio processing or always-running animation loop.

# 0.6 preview â€” 18 October 2026

- Added persistent System/Light/Dark appearance and themed system bars.
- Vinyl player now draws a turntable, spins only during visible playback, and parks the tonearm on pause.
- Added LRCLIB lyric lookup, synced highlighting/following, tap-to-seek, timing offset and plain-text fallback.
- Tightened beat confidence and tempo limits, added bounded beat-phase correction/fallback, used one gain-envelope timeline, honored configured fallback duration and softened the bass handoff.
- Added a subtle attack/release vibration envelope while keeping the existing onset detector and toggle.

# 0.5 preview â€” Queues and iPhone source port

- Added Play next/Play later, reorder/move next, shuffle upcoming and clear upcoming without stopping the current deck. Audible transitions are protected and Clear upcoming disables automatic refilling.
- Added a private JVM companion sharing the existing source implementation, with token authentication and bounded stream tickets.
- Added an experimental native SwiftUI iPhone source port and Windows/cloud-build installation instructions. Xcode compilation and physical iPhone validation remain outstanding; no signed IPA or beat/haptic parity is claimed.

# 0.2 preview

## Listening
Start with one online song. Autoplay appends related songs, prebuffers the next, and repeats the process as playback advances. Explicit queue choices have priority. Previous queue JSON remains readable. Autoplay and bass settings persist; haptics stay opt-in each session.

## Mixing
Two-deck equal-power fades now have an optional per-deck bass handoff: outgoing low frequencies recede first, incoming bass rises later. Device equalizers can vary or be unavailable; no tempo/beat/phrase synchronization is claimed.

## Interface
Light library/search/queue navigation with rose accents, actual source artwork, mini-player, and a full-screen player using colors sampled from the artwork. Large accessible transport controls, scrollable small-screen layouts, live transition status, and next-song information. No fake waveform, lyrics, or nonfunctional streaming-service controls.

## Validation
Unit coverage for duplicate recording/upload filtering and bounded recommendation order. Live check covers search artwork, stream bytes, related-song lookup, and resolving a recommended song. See VERIFICATION.md for the final build and device-testing outcome.
# 0.3 preview â€” 16 October 2026

- Validate offered stream URLs before playback, prefer broadly supported AAC/M4A and try up to three offered formats on failure. Show playback errors beside the song title. Provider/network restrictions can still make individual songs unavailable.
- Exclude instrumental, acapella, tribute and bootleg variants from automatic radio.

- Fixed album-cover decoding, added bounded retries/fallback and stream artwork refresh.
- Replaced general-video recommendations with music album-track radio; canonical title filtering rejects repeated upload variants and non-song results. Added persistent Not interested feedback.
- Added Android 8+ PCM-onset vibration fallback, intensity/test controls and a persistent full-player toggle.
- Added lightweight confidence-gated BPM/phase estimation, bounded next-track analysis, pitch-preserving tempo matching within Â±6%, beat-aligned entry and gradual tempo release. Uncertain tracks use short blends.
- Added RMS level attenuation and Ambient/Studio/Vinyl styles. Fixed empty PCM buffer handling found during Android runtime testing.

# 0.4 preview â€” Library and albums

- Added persistent liked songs, named playlists, rename/delete, song removal/reordering and duplicate prevention.
- Added Songs/Albums search filters, ordered album track lists, saved albums, full-album playlist addition and collection Play All/Shuffle/Queue.
- Added library actions to the player and search/queue song menus, backed by local SQLite metadata storage.
- Corrected obsolete transition descriptions in settings to describe the existing beat-sync path.
