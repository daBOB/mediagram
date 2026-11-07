# Game night controller apps (Android + iPhone) — design

Date: 2026-11-07. Branch `feat/controller-apps`, cut from `feat/spieltisch` (game night is not on
`main`; the user deferred that merge).

## Intent

At game night the TV box hosts the game and every player's phone is the controller. Today a phone
joins by scanning the QR code on the TV and gets the phone page in its browser
(`http://<box>:8080/`, served by `crates/mediagram-game`). That page has hit the limits of a
browser:

- **The screen goes dark mid-game.** Plain HTTP on the LAN is not a secure context, so there is no
  Wake Lock; NoSleep.js and a hidden looping video both failed on the real phones (2026-11-06).
  The lobby now only tells players to switch off their screen lock.
- **No vibration on iPhones** (Safari has no Vibration API).
- **The drawing game is next** (game night backlog), and a browser page fights a finger drawing on
  it: pinch zoom, scrolling, bounce, text selection, long-press menus.

The user, asked why: *"to have more control over the device. awake, vibration, drawing on device"*.

So: a native app on Android and on iPhone that is the better way into the same game.

## Who

The household **and guests**, on Android phones and iPhones. Household iPhones get apps by
sideloading. The household also has an **iPad mini kept specially for drawing games** (user).

## Goals

1. **An Android app and an iPhone/iPad app** that show the box's phone page. Android is a
   **separate controller app**, not a screen inside the media app (user). The iOS app also runs
   on the household's iPad mini, the drawing-games device.
2. **Find the box on the Wi-Fi by itself and join the lobby** (user). Tap the icon → lobby; nobody
   types an address or scans anything.
3. **The screen stays on** while the app is in the foreground.
4. **The phone vibrates** on (user): **your turn**, **buzzer accepted**, **wrong answer**,
   **game won**.
5. **Drawing-ready surface**: no zoom, no scroll or bounce, no text selection, no long-press
   callout, no system edge-swipe stealing strokes where the platform allows it — every touch
   reaches the page. On the iPad mini, an Apple Pencil reaches the page as a pen (pointer events
   with `pointerType: "pen"` and pressure), and iPadOS Scribble does not turn strokes into text.

## Non-goals

- Redrawing phone screens natively (see "How", my call).
- The drawing game itself — its own project from the backlog. This project only makes the app a
  surface it can run on.
- App Store / Play Store publishing.
- Self-update for the controller apps. The screens load live from the box, so an app reinstall is
  needed only when the native shell itself changes.
- A guest install path (e.g. the box serving the APK from the lobby). Guests use the browser as
  today; a later idea.

## Anti-goals (would be a failure even if it works)

- **The browser path changes for guests.** QR → browser page must keep working exactly as now.
- **Phone screens differ between browser and apps.** One set of screens, written once.
- **Anything that needs a Mac.** Everything builds on this Linux machine.

## Constraints and facts

- **iPhone build: xtool** (user, <https://github.com/xtool-org/xtool>, 1.24.0 from 2026-10-26).
  It builds a SwiftPM package into an iOS app on Linux, signs it with the user's Apple ID and
  installs it over USB (`xtool dev`, `xtool install`). Setup needs a Swift toolchain and a one-time
  Xcode `.xip` download for the Darwin SDK; neither Swift nor xtool is installed on this machine
  yet (`idevice_id` is). **Sideloadly** is the backup for installing the `.ipa` (user: "maybe
  sideloadly").
- **The Apple ID is a free one** (user). Free signing expires after **7 days**: an expired app
  does not open at all, so it cannot warn anyone itself. Free accounts also allow only 3
  sideloaded apps per device at once and 10 new App IDs per 7 days, so the bundle id must stay
  fixed across rebuilds. The plan needs a **one-command weekly refresh** (re-sign + reinstall
  every household iPhone and the iPad mini from this machine with xtool) and should say when to
  run it, e.g. before game night. Whether xtool can refresh over Wi-Fi instead of USB is for the
  builder to find out.
- **iPads have no vibration motor**: on the iPad mini the four moments are silent.
- **The page is plain HTTP on the LAN.** Android needs cleartext allowed (network security config);
  iOS needs ATS `NSAllowsLocalNetworking`. iOS discovery needs `NSLocalNetworkUsageDescription`
  and `NSBonjourServices` in Info.plist.
- **The host**: `crates/mediagram-game/src/host.rs` (`preferred_port: 8080`,
  `join_url = http://{lan_ip}:{port}/`); started by the TV app in
  `android/feature/games/src/main/kotlin/GameNightViewModel.kt` via `core.startGameNight`
  (`crates/mediagram-core/src/api/game_night.rs`). The phone page connects its WebSocket to
  `location.host`, so loading the page from the box URL is all the connection setup it needs.
- **Phone page**: `crates/mediagram-game/client/src/phone/` — `PhoneApp.tsx` renders the screens
  the host sends (`protocol/PhoneScreen.ts`: `lobby`, `wait`, `choice`, `sheet`, `outcome` with
  `tone: good|bad|neutral`, `cards`, `final` with `rank`/`of`); `phone-fullscreen.ts` requests
  fullscreen on the join tap; `PhoneLobby.tsx` shows the screen-lock hint.
- **The TV box** (Realtek, Android TV, Wi-Fi). Known network history: the box once failed to find
  the cache server by mDNS (other direction, same box), and a phone VPN once blocked the LAN for a
  player. Discovery must be proven on the real box early.
- UI language of game night is **German**.
- **Versioning**: `CLAUDE.md` § Versioning names three manifests that move in step. Two new apps
  add manifests; proposing that `CLAUDE.md` edit needs the user's sign-off (CLAUDE.md §4).
- Other game night work keeps landing on `feat/spieltisch` (cards, Bauherr, Durak and Schwimmen
  are merged as of `027b1d0e`). Keep changes to shared files (`PhoneApp.tsx`, `PhoneLobby.tsx`,
  host) small and merge `feat/spieltisch` in before testing.
- **Testing on the real box** works as every game-night round so far: sideload a release build of
  this branch to the box only (`installRelease` after `build-android-core.sh`), with a version
  above both `main` and `feat/spieltisch` so it is not mistaken for an older build. The phone page
  is embedded in the native core (`server.rs`, `#[folder = "client/dist"]`), so page changes need
  a core rebuild before the box sees them.
- **Sideloadly runs on Windows and macOS only**, so the backup install happens on whatever computer
  the user already sideloads from; xtool is the Linux path.

## How — decided by the user

- Find the box automatically on the Wi-Fi and join the lobby.
- Vibrate on your turn, buzzer accepted, wrong answer, game won.
- iPhone app built with xtool; Sideloadly as the backup installer.
- Free Apple ID (so: 7-day signing, weekly refresh).
- Android: a separate controller app.
- The iPad mini is the drawing-games device and runs the iOS app.

## How — my calls (the user has not confirmed these)

1. **Thin app around the same phone page.** Each app is a full-screen web view on the box's URL
   plus a small native layer. Every game's phone screen stays written once in the React client and
   behaves the same in the browser and both apps; game changes on the box need no reinstall. The
   user was told this recommendation and did not object, but did not explicitly confirm it.
2. **Native bridge, page-driven.** The app exposes a small JS object to the page (name is the
   builder's, e.g. `window.mediagramApp`) with at least `vibrate(kind)` where `kind` is one of the
   four moments. The page decides *when*; the app decides *how it feels* (pattern / haptic style).
   The page detects the app by the bridge's presence.
3. **Where the four moments are detected: in the phone page**, from screen changes it already
   receives — no protocol change unless the builder finds a moment the screens can't tell:
   - *your turn* — the screen becomes one that asks this player to act (`choice`, `sheet` with
     options, `cards` when it is this player's turn);
   - *buzzer accepted* — the player's own pick is acknowledged by the host (`picked` set);
   - *wrong answer* — `outcome` with `tone: "bad"`;
   - *game won* — `final` with `rank === 1`.
   In plain Android browsers the page may also call `navigator.vibrate` for the same moments (it
   works over HTTP after a tap); iPhone browsers stay silent. Builder may drop this if it misfires.
4. **Discovery: DNS-SD/Bonjour.** The TV box announces a service (e.g. `_mediagram-game._tcp`, port
   = the host's real port) while game night runs; the apps browse for it (Android `NsdManager`,
   iOS `NWBrowser`). Where the announcement lives (Android `NsdManager` in `feature/games`, or the
   Rust host) is the builder's call. The app remembers the last box URL and tries it first.
5. **When nothing is found:** the app shows a German waiting screen ("Warte auf den Fernseher …")
   and keeps looking, joining as soon as the TV opens game night. A button there scans the TV's QR
   code with the camera as the fallback (wrong Wi-Fi, VPN, discovery failure).
6. **In the app, the page skips browser workarounds:** no fullscreen request
   (`phone-fullscreen.ts`), no screen-lock hint in the lobby.
7. **Android app shape** (separate app is the user's decision): a new application module in the existing `android/` Gradle project reusing the
   convention plugins, own `applicationId` (e.g. `com.mediagram.controller`), no Rust core, no
   Telegram, no Hilt unless it pays for itself. Distributed by APK/adb like the household phones.
8. **iOS: a new top-level `ios/`** SwiftPM package in xtool's layout, one universal app for
   iPhone and iPad. SwiftUI + `WKWebView`; `isIdleTimerDisabled` for keep-awake; UIKit haptics
   for vibration. Both orientations allowed on the iPad; the page already lays itself out by
   width.
9. **Name on the home screen: "Spieltisch"**; a simple icon.
10. **The player's memory survives a box address change.** The page keeps name, seat and avatar in
   `localStorage`, which belongs to the box's address; the box's DHCP address has moved several
   times. Inside the app the page keeps that memory through the bridge instead, so a new address
   does not send a player back to typing their name.
11. **Version**: the apps carry the project version like the other three manifests.

## Success

On a real game night, verified on the real TV box, a real Android phone and a real iPhone:

- Household phones open the app and are in the lobby within a few seconds, with no typing or
  scanning.
- Screens stay on through a whole game.
- Phones buzz at the four moments and at no others.
- A guest in the browser sees no change.
- A finger drawing on a test canvas inside the app moves nothing but the stroke, and on the iPad
  mini an Apple Pencil draws with pressure (a throwaway test page is fine; the drawing game comes
  later).
- The weekly iOS refresh is one command, written down where the household will find it.

## Left to the builder

Bridge name and shape, vibration patterns per moment, where the box announces itself, the QR
scanning library or platform API, the icon, the waiting-screen wording, how the four moments are
wired into `PhoneApp.tsx` without colliding with the parallel game worktrees.
