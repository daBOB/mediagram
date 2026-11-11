# Spieltisch on funstation (2026-11-11)

User: "test it on the funstation". Funstation: CachyOS at 192.168.0.43, Android TV 16 in WayDroid (x86_64, `libndk_translation` for ARM). The funstation session (mediagram-spieltisch-16) was told first and said go; its cache server, web player and config were not touched.

## What went on the box

| App | Before | After | Build |
|---|---|---|---|
| `com.mediagram.android` | 0.163.0 benchmark (feat/funstation) | 0.175.0 benchmark, this branch (data kept: Telegram session, profiles, TMDB key) | `:app:assembleBenchmark`, all ABIs, release key (on this machine the debug key is the release key) |
| `com.mediagram.spieltisch` | none | 0.175.0 release | `scripts/release-spieltisch.sh --no-publish`, arm64/armv7 only, so it runs under ARM translation there |

Installed with `waydroid shell -- pm install -r` (no adb on funstation; Android's adb is off and stayed off). No settings changed. Active profile stayed "test".

## Results

| Check | Result |
|---|---|
| Games row → Spieltisch | Opens via the leanback intent (from the media app's uid); Spieltisch drawn in 342 ms. `funstation-media-app-rail-games.png`, `funstation-games-row-opens-spieltisch-shelf.png` |
| Night on the first press (cold: the new media app had never built one) | host announced ~3 s after the press |
| Night with both apps force-stopped, Spieltisch started alone | media app started for the provider 0.13 s in; night announced 1.1 s after Spieltisch's start; films answered (no "unavailable" note). Drawn in 319 ms |
| Spieltisch's update check through the media app | "up to date · checked just now", both starts |
| Poster-Raten with library posters | Round 1 pixelated then revealed a library poster (The Suicide Squad, German key art). The image path, unseen on the emulator, works. `funstation-poster-raten-library-poster-*.png` |
| Titles from the library on the phone | four library titles as answers |
| Kids lock (profile "test", not a kids profile) | unlocked: the phone offers "Matratzensport (ab 18)". `funstation-phone-controller-unlocked.png` |
| Back | game → lobby (player kept) → shelf → media app on the page it left (Home); night ended (mDNS withdrawn, host closed). `funstation-back-*.png` |
| Spieltisch frames during a round (gfxinfo) | 17 frames (the page redraws only on each sharpness step); p50 6 ms, p90 23 ms; 4 slow ones are "slow issue draw commands" (poster changes); no slow UI thread, no missed vsync |
| ARM-only Spieltisch under translation | runs: JNA and the Rust host load and serve |

## Second run: phones on the LAN (09:21)

The funstation session put WayDroid on the home network (bridge `br0`; Android at 192.168.0.44, the host keeps .43) and re-pointed the media app's LAN cache address to .43.

| Check | Result |
|---|---|
| Night address | mDNS and the QR now carry `http://192.168.0.44:8080/`; the dev machine reaches it over the LAN (200), no tunnel |
| Kopfnuss, two phones | Anna over the LAN, Testa through a tunnel (a second origin, since the browser tool has one localStorage per origin); started from the remote, played to round 3 with points. `funstation-kopfnuss-*.png` |
| Frames over about a minute of Kopfnuss (gfxinfo) | 2,421 frames, 0.37 % janky, p50 5 ms, p90 15 ms, p99 36 ms, 1 missed vsync, 0 slow UI thread (last Kopfnuss box run: 2.5 % slow, p99 36 ms) |
| Leaving | Back ×3: game → lobby → shelf → out; host closed |

## Not done here

- Kids profile switch: "TV kids" (FSK 12) exists, but switching back out may need a grown-up's PIN; not risked on someone else's box. The lock's "on" path stays covered by tests.
- Frame times during a Spieltisch download: needs a real `#spieltisch-app` publish (user's go).
- The Mali box run (phase 5 proper).

## Found

1. **Phones couldn't join a night on funstation (fixed in funstation's setup, 09:17).** The night showed WayDroid's NAT address, `192.168.240.112:8080`, which the LAN had no route to. Not from this branch: the old in-app night had it too. The funstation session bridged WayDroid onto the LAN; see the second run.
2. **Funstation's media app lost feat/funstation's keyless artwork.** This branch predates that merge. Artwork still works because the stored TMDB key was kept. Goes away when this branch takes main.

## Unresolved

- Whether Spieltisch's release should also ship x86_64 for funstations (it runs translated today; the media app on funstation is a benchmark build with all ABIs).
