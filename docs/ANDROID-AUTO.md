# Android Auto — v0.13 preview

The Android app now exposes its existing player as a media browser service.
The car can browse Liked songs, Recently played, Playlists and Queue, select a
song, use voice search, and use play/pause, previous/next, seek and the Like action. Layout and
available buttons are controlled by the head unit. Each list is limited to 100
items. Online songs still need a network connection. Open the phone app once
and save some songs/playlists before connecting.

## Install and test while parked

1. Install `MusicTransi-v0.13-preview.apk` over the existing installation.
2. Open MusicTransi and grant notification permission if requested.
3. Open Android Auto settings on the phone. As this APK is sideloaded, enable
   Android Auto developer mode: tap **Version and permission info** ten times
   and accept the prompt.
4. In Android Auto's overflow menu, open **Developer settings** and enable
   **Unknown sources** for testing sideloaded media apps.
5. Reconnect the car. Check **Customize launcher** in Android Auto settings
   and enable MusicTransi if it is hidden.
6. Check browsing from a cold start, play/pause, skipping, artwork, steering-wheel
   controls, interruptions and reconnecting before using it on a journey.

This is Android Auto phone projection, not an Android Automotive OS standalone
app or Apple CarPlay support. Real car compatibility is not yet verified.

Official references:
- https://developer.android.com/training/cars/media/configure-manifest
- https://developer.android.com/training/cars/testing

## Lyrics and Activity

Liquid glass is replaced with matte translucent frosted surfaces. Lyrics retain
playback and seek controls. Enhanced LRC word markers are supported when supplied;
ordinary line-synced lyrics use **Estimated word follow**, which can be switched
off in Lyrics → Timing. Estimated timings are not vocal alignment. Plain lyrics
remain unsynced.

Activity shows monthly audible listening time, ranked artists and songs, for the
last 24 months (up to 10,000 month/song totals). It starts with this version; old
recent-song history has no duration data and is not backfilled. Data stays on
the device and saves approximately every 15 seconds or on pause; abrupt process
death can lose the last unsaved interval. Clearing Activity does not delete the
library. During an overlap, listening time belongs to the current outgoing song
until the player switches tracks, so overlap is never counted twice.
