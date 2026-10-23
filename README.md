# MusicTransi

A personal music-player project for Android, with an early native iPhone source preview.

## Choose your platform

| Platform | v0.12 download | Status |
|---|---|---|
| Android 8+ | `MusicTransi-v0.12-preview.apk` | Debug-signed development preview; tested on Android 11 emulator |
| iPhone / Mac | `MusicTransi-Mac-iPhone-v0.12-kit.zip` | Xcode source and Java companion; build and sign on your Mac. No signed IPA |

Release assets are intended for GitHub Releases, not the source tree. This repository does not contain subscription audio or provider account credentials.

### Android

See [Android documentation](android-app/README.md) and [verification results](android-app/VERIFICATION.md). Includes playlists/likes, queue tools, lyrics and lyric-card sharing, artwork transitions, suggestions, optional glass styling and music vibrations. Install updates over the existing preview to retain local data.

### iPhone

Follow the [Mac installation and update guide](ios-app/MAC-IPHONE-GUIDE.md). Keep the same signing team and bundle ID when updating. Online playback requires the companion computer; local audio imports work independently.

The iPhone source includes directional artwork, lyric sharing and recent-history radio exclusions, but is **not Android feature parity**. Xcode compilation, signing and physical iPhone behavior have not been verified in the Windows development environment. See [iPhone limitations](ios-app/VERIFICATION.md).

## Known limitations

- Audio beat synchronization still has an unresolved regression; this is not Apple AutoMix equivalence.
- Physical-device haptic timing, battery impact and animation frame times are not benchmarked.
- Online source availability and recommendation quality vary.
- iPhone does not yet include Android Home/Discover mixes, metadata-library imports, custom playlist covers, online downloads or music haptics.

## Development and dependencies

Android: `android-app/`. iPhone: `ios-app/`. The JVM companion shares Android catalog-source code. The root HTML/CSS/JS files are the original browser prototype, not the installable mobile app.

Read [third-party notices](android-app/THIRD_PARTY.md). The tagged source and GPL-3.0 license accompany the Android preview release. No affiliation with Apple, Spotify, Amazon, YouTube or other music providers is claimed.
