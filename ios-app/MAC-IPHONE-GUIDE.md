# Install MusicTransi on your iPhone using your Mac

Prepared for your macOS Tahoe Mac and iOS 27 iPhone.

This package contains a native iPhone **source preview**, an Xcode project, and a prebuilt Java companion for the Mac. Xcode must compile and sign the app using your Apple Account. No signed IPA is supplied, and the Swift app has not yet been compiled or tested on a Mac in this workspace.

The **0.12 iPhone source update** adds directional artwork changes, a slowly fading album backdrop, cover-image fades, persistent recent-listening exclusions, and selected lyric cards with cover/title/artist above the lyrics. Glass controls use native Liquid Glass on iOS 26+ and a material fallback on older supported systems. The source requires Xcode 26+ to compile. It retains search, albums, likes, playlists, queue editing, local audio, themes, pinned lyric controls and crossfades. The companion is rebuilt with the shared Android suggestion filters.

**This is not Android feature parity:** generated Home/Discover playlists, exported-library CSV/JSON imports, custom playlist-cover uploads, offline online-song downloads, music haptics, beat synchronization and the Vinyl/Studio/Record designs are not included in this iPhone update. Online music still needs the companion Mac awake and reachable; imported audio does not. Xcode compilation and actual iPhone behavior remain unverified here.

## Updating an existing installation

1. Keep your existing app installed. Record its Xcode **Bundle Identifier** and signing **Team** before replacing source files.
2. Extract the new **MusicTransi-Mac-iPhone-v0.12-kit.zip** into a new folder. Keep the previous project as a backup.
3. Open the new kit's `ios-app/MusicTransi.xcodeproj`. Set the **same Bundle Identifier and Team** as the installed app. Do not run the project generator after configuring signing.
4. Run `bash Check-Mac-Build.command` from the new `ios-app` folder, then select your iPhone in Xcode and press **Command-R**. A successful build with the same identity updates the installed app and preserves its local data. Uninstalling or changing the identifier creates a separate/empty library.
5. Stop the old companion with **Control-C**. Start the new kit's `companion-mac/Start-Companion.command` as described below. A fresh folder generates a new private token: paste that token into the app and tap **Save & test connection**. Existing playlists do not need to be recreated.
6. Check next/previous artwork motion; play a song for 30 seconds to populate recent history. Open Lyrics → Share lines, choose up to four lines (280 characters), Preview, then share from the system sheet. Artwork failure uses a note placeholder. Reduced Motion in iPhone Accessibility reduces artwork motion.

## 1. Prepare the Mac

1. On the Mac, choose **Apple menu → About This Mac** and check the full macOS version.
2. For Xcode 27, Apple currently requires **Tahoe 26.6 or later**. If necessary, update through **System Settings → General → Software Update**. See [Apple's current Xcode compatibility table](https://developer.apple.com/xcode/system-requirements) before choosing an Xcode version.
3. Install the full **Xcode** app from the Mac App Store, or from [Apple Developer Downloads](https://developer.apple.com/download/all/). Use an Xcode version supporting your phone's installed iOS 27 version. If the phone runs a beta newer than stable Xcode supports, use the matching Apple Xcode beta.
4. Open Xcode, accept its license, and install the requested iOS platform components. Wait for setup to finish.
5. In **Xcode → Settings → Apple Accounts** (called **Accounts** in some versions), add your Apple Account. A free account can use a Personal Team for your own device. You do not need AltStore for these instructions.

## 2. Transfer and open the project

1. Copy **MusicTransi-Mac-iPhone-v0.12-kit.zip** to the Mac using a USB drive, AirDrop from another Apple device, or your usual file transfer method.
2. Double-click the ZIP in Finder. Keep the whole extracted **MusicTransi-Mac-iPhone-kit** folder together. A convenient location is Downloads.
3. Inside it, open **ios-app → MusicTransi.xcodeproj** in Xcode. Do not open a single Swift file as the project.
4. The project and shared scheme are already included: Python, Android Studio, Gradle and CocoaPods are not required to build the iPhone app.

Optional compile check before signing: open Terminal, type `cd `, drag the extracted **ios-app** folder into Terminal, and press Return. Then run:

```bash
bash Check-Mac-Build.command
```

It writes **Mac-build.log** and builds without signing. Only **BUILD SUCCEEDED** confirms compilation. If it fails, keep the log; the first compiler error is the useful part. This check does not install the app. If it reports the wrong command-line tools, select the full Xcode app in **Xcode → Settings → Locations → Command Line Tools**.

## 3. Set up signing

1. In Xcode's left navigator, select the blue **MusicTransi** project icon.
2. Under **TARGETS**, select **MusicTransi**.
3. Open **Signing & Capabilities** and enable **Automatically manage signing**.
4. Choose your **Personal Team**, or your paid development team if you already have one.
5. Set a unique **Bundle Identifier**, for example `com.yourname.musictransi`. Replace `yourname` with your own letters/numbers. If it is unavailable, choose a different identifier.
6. Keep the same team and identifier for future updates so you update the existing installation. Do not regenerate the project after configuring signing; generation resets project settings.

Xcode manages the development signing assets. See [Apple's device installation instructions](https://help.apple.com/xcode/mac/current/en.lproj/dev5a825a1ca.html).

## 4. Connect the iPhone and install

1. Connect the unlocked iPhone to the Mac using a data-capable USB cable.
2. Accept **Trust This Computer** on the iPhone and enter its passcode. Accept the Mac's accessory connection prompt if shown.
3. In Xcode, open **Window → Devices and Simulators** or **Device Hub**, depending on your version. Select your iPhone and wait for pairing/device preparation to complete.
4. On iPhone, go to **Settings → Privacy & Security → Developer Mode**, turn it on, restart when requested, then confirm enabling it after restart. If the option is absent, pair with Xcode first. [Apple's Developer Mode guide](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)
5. In Xcode's top toolbar, select the **MusicTransi** scheme and your **actual iPhone** as the run destination, not a simulator or generic iOS device.
6. Press **▶ Run** or **Command-R**. Keep the phone unlocked while Xcode builds, signs and installs the app.
7. If the phone explicitly requests developer trust, follow its prompt under **Settings → General → VPN & Device Management**, trust your own development account, then Run again.
8. Once MusicTransi opens, stop the debugger in Xcode and reopen the app from the iPhone Home Screen. You can disconnect the cable. Online playback will still need the companion connection below.

You do not need to export an IPA for your own connected phone. With a free Personal Team, Apple's provisioning profiles expire after **7 days**; reconnect and Run again to refresh the installation. Keep the project, team and bundle identifier unchanged. Apple currently permits three apps per device with this account type. [Apple's Personal Team limits](https://developer.apple.com/help/account/basics/about-your-developer-account)

## 5. First test: local audio

1. Put an MP3 or AAC audio file you can use in the iPhone's **Files** app.
2. Open MusicTransi and tap **Import audio**. Select the file.
3. Check play, pause and seeking. Import a second song to check the queue and crossfade.
4. Lock the phone and check playback and lock-screen controls.

Local imports are copied onto the phone and can play without the Mac. Deleting the app also deletes its local library and imported audio.

## 6. Enable online search and playback

The kit contains the companion's compiled Java libraries. Both Apple Silicon and Intel Macs use the same libraries, with a matching Java installation.

1. Install **JDK 17 or newer** on the Mac. For example, use [Eclipse Temurin's macOS installer](https://adoptium.net/installation/macOS), choosing **aarch64/ARM64** for Apple Silicon or **x64** for Intel. Check **About This Mac** if unsure.
2. Connect the iPhone and Mac to the same trusted Wi-Fi network. Avoid a guest network that isolates devices.
3. In Terminal, type `cd `, drag the kit's **companion-mac** folder into Terminal, and press Return. Run:

   ```bash
   bash Start-Companion.command
   ```

4. If macOS asks whether Java may accept incoming connections, allow it for this companion. Keep the Terminal window open and the Mac awake. The launcher displays a URL such as `http://My-Mac.local:8787` and a private token.
5. In MusicTransi on iPhone, open **Settings → Online music companion**.
6. Enter the **exact URL printed by the launcher** and paste the private token. Do not enter `localhost` or `127.0.0.1`: those would point at the iPhone itself.
7. Tap **Save & test connection**. Allow **Local Network** access if iOS asks. Wait for **Connected**.
8. Open Search, enter a song or album, and submit the search. Tap a song to play. Use its menu to like it, add it to a playlist or queue it next.

To stop the companion, press **Control-C** in its Terminal. Next time, run the same launcher; it reuses the private token stored in `.bridge-token.secret`. Do not share that file or open the companion port to the internet. Streaming away from the Mac's network is not configured by this kit.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Xcode cannot install on Tahoe | Check the full Tahoe point version against Apple's compatibility table; Xcode 27 lists 26.6+. |
| iPhone unavailable or unsupported | Unlock/trust/pair it, finish Xcode device preparation, and use Xcode supporting the installed iOS version. |
| Signing requires a development team | Select your account under the app target's Signing & Capabilities, not just project settings. |
| Bundle identifier cannot be registered | Choose a unique identifier and leave automatic signing enabled. |
| Build failed | Open Xcode's Issue navigator, or run the build-check script. Share the first compiler error or Mac-build.log; do not share credentials or the companion token. |
| App stops opening after a week | Refresh the free development installation by connecting to Xcode and pressing Run. |
| Companion says Java is missing | Install the correct JDK for your Mac; open a new Terminal. |
| Companion reports address already in use | Stop the earlier companion Terminal with Control-C. Only one instance should use port 8787. |
| Connection test fails | Check URL/token, same Wi-Fi, Mac awake, Java firewall permission and iPhone Local Network permission. Use the printed `.local` hostname. |
| Search works but one track fails | Source availability can vary. Try a different song; local playback is a separate check. |
| Music stops when Mac sleeps | Keep the Mac awake while using online music. Imported files do not require it. |

## What has been checked

The project files and package contents were checked on Windows. The companion previously passed live source/search/album/stream-range checks on the JVM. The Mac shell scripts were syntax checked. **Xcode compilation, signing and physical iPhone playback remain to be verified on your Mac.** This is an early iPhone source preview, not complete Android parity or an App Store release.
