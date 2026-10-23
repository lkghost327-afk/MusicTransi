# Android preview archive

Historical Android builds are collected here for testing, comparison and rollback. These are debug-signed development previews, not Play Store releases. For normal use, install the [latest v0.12 build](https://github.com/lkghost327-afk/MusicTransi/releases/tag/v0.12).

## Included APKs

| Version | Main milestone |
|---|---|
| v0.2 | Two-deck playback, autoplay, bass handoff and artwork-led player |
| v0.3 | Stream fallback, recommendation filtering, haptics and initial beat alignment |
| v0.4 | Liked songs, playlists, albums and persistent library |
| v0.6 | Dark theme, Vinyl turntable, synced lyrics and refined vibration |
| v0.7 | Optional glass controls, gestures and Orbit player |
| v0.9 | Record player, suggested songs, offline downloads and notification controls |
| v0.10 | Personal mixes, listening history and redesigned Home/Search/Library |
| v0.11 | Library-file imports, custom playlist covers and lyric-card sharing |

Versions v0.5 and v0.8 are not included because their APK files are unavailable. Their features were carried into later builds.

Every included APK was checked for its embedded version name, APK Signature Scheme v2 signature and SHA-256 digest before upload. Use `ANDROID-ARCHIVE-SHA256.txt` to verify downloaded files.

Older online features can stop working when upstream services change. Installing an older build over a newer one may require uninstalling the current app, which can remove local MusicTransi data. Back up anything important first.
