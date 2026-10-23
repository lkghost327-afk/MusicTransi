# Install the MusicTransi iPhone preview from Windows

## What you need to know first

You cannot install the Android APK on an iPhone. Windows cannot run Apple's Xcode iPhone build tools. This package supplies **iOS source and a cloud-build workflow**, not a verified ready-to-install iPhone binary. You do not need to own a Mac for the route below, but GitHub's hosted Mac must successfully compile the app first. If that build fails, stop and share its compiler log for repair.

The iPhone port requires **iOS 17 or newer**. Local audio imports work on the phone. For online search, albums, recommendations and playback, the Windows companion must stay running and reachable; on home Wi-Fi, both devices must be on the same trusted network. Turning the PC off stops online access. Beat synchronization and music-driven vibrations are not included in this iPhone preview.

## Part 1 — Produce the iPhone file using GitHub

1. Extract `MusicTransi-iPhone-source.zip` into a new folder. The root contains `.github`, `ios-app` and `android-app`.
2. Sign into GitHub and create a repository. Choose private if you do not want to publish the source. GitHub's standard macOS runners consume your private-repository Actions allowance; check your account's billing limit before running. Public repositories on standard runners are free. [GitHub's billing rules](https://docs.github.com/en/billing/concepts/product-billing/github-actions).
3. Use GitHub Desktop: **File → Add local repository → Choose**, select the extracted folder, and choose **create a repository here** if prompted. Review the files, commit and **Publish repository** to your account. Alternatively upload the contents using GitHub's website, making sure `.github/workflows/ios-preview.yml` is included. Upload source only: no passwords, bridge token, signing keys or build caches. This package's `.gitignore` excludes them.
4. On the repository website, open **Actions → Build iPhone preview → Run workflow**. If Actions are disabled for the repository, enable them first. The workflow needs no Apple password or signing secrets.
5. Wait until the job is green. It generates the Xcode project, compiles a device Release build with signing disabled, and packages it. A successful workflow verifies compilation, not playback or device behavior.
6. Open that run and download the **MusicTransi-iPhone-unsigned** artifact. Extract the downloaded ZIP; inside is `MusicTransi-iPhone-unsigned.ipa`. Download within seven days, or rerun the workflow.
7. Keep the IPA private. It is **unsigned**, so tapping it on an iPhone will not install it. Continue below to sign it with your own account.

## Part 2 — Install from Windows using AltStore Classic

Follow the current [official AltStore Windows setup guide](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows) if any screens differ. Use **AltStore Classic**, not AltStore PAL, for this IPA workflow.

1. Download AltServer from the official AltStore site. Follow its prerequisites for iTunes and iCloud from Apple, rather than Microsoft Store versions.
2. Connect the unlocked iPhone to the PC by USB. Tap **Trust** on the phone and enable Wi-Fi sync in iTunes if you want wireless refreshes later.
3. Run AltServer on Windows. In its system-tray menu, select **Install AltStore → your iPhone**, then authenticate using your own Apple account as the official tool requests. Enter credentials only in that tool, never in GitHub, source files or this chat.
4. On iPhone, trust your developer profile under **Settings → General → VPN & Device Management** when requested. On supported iOS versions enable **Settings → Privacy & Security → Developer Mode**, restart, and confirm.
5. Open AltStore on the iPhone and sign in as instructed. Keep AltServer running on your PC. Transfer the IPA to iPhone Files (for example via iCloud Drive).
6. Open **AltStore → My Apps → +**, select `MusicTransi-iPhone-unsigned.ipa`, and wait while AltStore signs and installs it. Open MusicTransi afterward.
7. With a free Apple account, apps expire after **seven days**; refresh them in AltStore while AltServer is reachable. There is also a **three active sideloaded apps** limit, including AltStore. [Refresh/expiry](https://faq.altstore.io/altstore-classic/your-altstore), [active app limits](https://faq.altstore.io/altstore-classic/activating-apps).

If installation fails, save the exact AltStore error. Common causes are expired credentials, device trust, missing Developer Mode, firewall/network access, or account app limits. Do not uninstall a working copy merely to refresh it: uninstalling deletes this app's library/imported files. Apple or AltStore changes may affect this route; no successful install is claimed until tested on your phone.

## Part 3 — Run the online-music companion on Windows

1. Extract `MusicTransi-companion-windows.zip`. Install a **Java 17 or newer JDK** if `java -version` is not available. A complete JDK includes the HTTP server module used by the companion.
2. In the extracted folder, right-click `Start-Companion.ps1` and choose **Run with PowerShell**, or open PowerShell there and run `powershell -ExecutionPolicy Bypass -File .\Start-Companion.ps1`. This bypass is for that one trusted local script; it does not change the system execution policy.
3. The script creates a random private token in `.bridge-token.secret`, prints it for you to copy, and starts port **8787**. Keep the window open. Treat the token file as a password and do not upload it to GitHub.
4. If Windows Firewall asks, allow Java on **Private networks only**. Do not forward this port on your router. The companion streams through your computer to keep source requests and playback on the same network; it is not designed as a public service.
5. Run `ipconfig` in another terminal and find your active Wi-Fi adapter's IPv4 address, for example `192.168.1.25`. The phone needs the PC's address, not `localhost` or `127.0.0.1`. If local IP connections are blocked by iOS, use your computer's Bonjour `.local` hostname (for example `DESKTOP-ABC.local`) or a properly configured HTTPS endpoint.
6. On iPhone open **MusicTransi → Settings**. Enter `http://192.168.1.25:8787` (substitute your PC's address), paste the token, then tap **Save & test connection**. Allow Local Network access when iOS asks.
7. Once it says **Connected**, go to Search. Choose Songs or Albums, enter a query and submit. Play a song; use its menu for Play next, Play later, Like or Add to playlist.
8. To stop the companion, press **Ctrl+C** in its window. Imported local files continue to work independently. Using the phone away from home needs a reachable HTTPS/private-network setup; this guide does not open internet ports or configure hosting.

## Part 4 — Try the app and verify behavior

1. Tap **Import audio**, choose a local MP3/M4A file, and verify it plays without the companion. Imported audio is copied to the app's storage.
2. Search for two online songs. Start the first, choose **Play next** on the second, then open Queue. Verify the order and cover art.
3. Use **Play later**, move arrows, **Shuffle upcoming**, and **Clear upcoming**. Clear upcoming turns Autoplay off while keeping the current song (and an already audible transition) intact.
4. Like a song, create a playlist, add an album, close/reopen the app, and check the library.
5. Lock the phone while listening and test play/pause/next from the lock screen. Test headphones separately. The port has not yet passed these physical-device checks.

## If you later use a Mac

See the Xcode steps in [README.md](README.md). Personal-device testing uses your Apple account in Xcode; distributing through TestFlight or the App Store requires the relevant Apple Developer Program setup and review. [Apple account overview](https://developer.apple.com/help/account/basics/about-your-developer-account), [run on a device](https://help.apple.com/xcode/mac/current/en.lproj/dev5a825a1ca.html).
