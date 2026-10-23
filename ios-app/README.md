# MusicTransi iPhone 0.12 source preview

Start with [MAC-IPHONE-GUIDE.md](MAC-IPHONE-GUIDE.md). It contains first-install and existing-install update steps, the current Apple compatibility links, signing, Developer Mode, companion setup and troubleshooting.

The native SwiftUI app includes search/albums, likes/playlists, queue editing, local audio import, background playback, crossfades, lyrics with pinned playback controls and optional glass controls. This update adds directional/fading covers, an album backdrop, recent-history Autoplay filtering and selected lyric cards with cover/title/artist above the lyrics. On iOS 26+, glass uses Apple's native glassEffect; older supported devices use material. Use Xcode 26+ to compile, and Xcode supporting your phone's OS (Xcode 27 for iOS 27).

Open the included MusicTransi.xcodeproj directly; do not regenerate after setting your signing team and bundle identifier. Run `bash Check-Mac-Build.command` on the Mac for an unsigned compilation check. No signed IPA is included. Windows validation cannot compile SwiftUI/UIKit; Xcode/device checks are still required.

Not Android parity: Home/Discover mixes, exported-library metadata import, custom playlist covers, online downloads, beat synchronization, music haptics and Vinyl/Studio/Record are not included. Online playback needs the companion Mac awake on the same network. Local audio works independently. Keep the same bundle ID/team and do not uninstall when updating to preserve local data.

See [VERIFICATION.md](VERIFICATION.md) for checked items and remaining limitations. The older Windows-only installation alternatives remain in WINDOWS-IPHONE-GUIDE.md.
