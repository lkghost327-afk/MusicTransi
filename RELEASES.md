# v0.12 preview

Publish as a **prerelease**. Android and iPhone have different readiness levels.

## Android

- Left/right artwork transitions, cover fades and gradual background colours.
- Recent-history exclusions and improved recommendation filtering.
- Shared lyric cards with cover, title and artist above the lyrics.
- Build/lint and signature checks passed; 33 unit tests and 11 targeted Android runtime tests passed. Four optional network tests skipped.

Assets: `MusicTransi-v0.12-preview.apk`, `MusicTransi-v0.12.sha256`, `MusicTransi-v0.12-source.zip`, `INSTALL-v0.12.md`.

## iPhone — Mac source/build kit

- Updated native SwiftUI source, artwork transitions, recent-history radio exclusions and lyric cards.
- Native Liquid Glass on supported systems, material fallback on older iOS.
- Rebuilt Java companion and installation/update guide.
- Project/package and shell checks passed. **Not compiled or tested with Xcode; no signed installable IPA.**

Assets: `MusicTransi-Mac-iPhone-v0.12-kit.zip`, matching `.sha256`, `IPHONE-INSTALL-v0.12.md`.

The known audio beat-sync issue remains unresolved. See each platform's verification document. The tagged repository source and third-party notices accompany the preview binaries.
