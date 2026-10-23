# Standalone streaming and haptics: feasibility checkpoint

18 October 2026. This is research, not a working standalone iPhone implementation.

The current companion hosts NewPipeExtractor, a Java/JVM extractor shared with Android. It cannot simply be linked into the native SwiftUI application. Keeping the companion also keeps current search, album and recommendation behavior in one implementation.

A candidate for a native experiment is [YouTubeKit](https://github.com/alexeichhorn/YouTubeKit). Its repository describes audio-only extraction on iOS and an optional remote fallback. It also explicitly warns that it is work in progress and can fail by region. The remote fallback causes device HTTP requests on behalf of a server; it should not be enabled implicitly. A successful extraction alone would not supply the current music-only search, ordered albums or radio pipeline.

Next gate on the Mac: evaluate a pinned version in an isolated prototype with local extraction only; verify native-playable audio, request latency, regional failure behavior and metadata matching on real devices. Then compare the complete search→radio→resolve→background-playback journey against the companion. Adopt only after passing those checks and reviewing its license and dependencies. No dependency or third-party relay was added in this update.

For iPhone music haptics, Core Haptics support must be checked on the device, and the engine can stop when an app is suspended. [Apple's applicationSuspended documentation](https://developer.apple.com/documentation/corehaptics/chhapticengine/stoppedreason/applicationsuspended) describes restarting on return to foreground. This does not establish reliable screen-off music haptics. The current AVPlayer engine has no shared PCM onset pipeline like Android, so an independently timed pulse loop would not meet the music-synchronization requirement. Prototype audio analysis plus clock alignment before offering a music-vibration toggle.

Retain the companion and the existing Android haptics while these device-dependent gates remain unverified.
