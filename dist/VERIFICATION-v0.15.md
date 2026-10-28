# v0.15 preview verification — 27 October 2026

- Audio-quality revision: version remains 0.15-preview / versionCode 15.
- Unit tests: 54 total, 50 passed, 4 optional network tests skipped, no failures.
- App and Android instrumentation sources compiled; assembleDebug succeeded.
- Widget inflation/control-target and square-crop instrumentation tests added,
  but not run because no Android device was connected.
- Manifest: com.example.musictransi, versionCode 15, versionName 0.15-preview,
  minimum API 26 and target API 36.
- apksigner verification passed; certificate SHA256 matches previous versions:
  447a6f1cd093723fce2d59187ad02c9ce91f0337c4970976bd23250d7895f1f4.
- Packaged APK copied from the successful build output. Size: 17,970,171 bytes.
- APK SHA256: A50494D1F220B7A56CFCE2494DB34E413491D92535575B5C93BB9633F865DC8D.
- Matching Android source archive packaged. Prior previews retained.
- git diff --check passed with line-ending notices only.
- Android lint before the audio-quality revision: BUILD SUCCESSFUL; zero errors, 59 warnings. New widget warnings
  include hardcoded English labels and API 31 preferred-cell attributes (older
  devices use the supplied minimum sizes), plus Kotlin style suggestions.
- Lint and instrumentation compilation were not repeated for the quality revision;
  the final app compile, full unit suite, APK assembly and signature check passed.
- Gradle daemon stopped after verification.

Not verified: real-device startup latency, live artist portrait coverage,
home-screen widget appearance/transport behavior, physical car playback and
Samsung notification-card rendering. Supplying square artwork cannot change
the system's own media-card layout. No new GitHub release published.
No lossless/Atmos stream capability is claimed. These depend on source content
and output support, not increasing a bitrate setting. Device Sound settings opens
the system screen; available effects differ by phone.

VERDICT: PASS
