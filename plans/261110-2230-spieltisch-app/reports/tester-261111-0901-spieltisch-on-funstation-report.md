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

## Not done here

- Kids profile switch: "TV kids" (FSK 12) exists, but switching back out may need a grown-up's PIN; not risked on someone else's box. The lock's "on" path stays covered by tests.
- Frame times during a Spieltisch download: needs a real `#spieltisch-app` publish (user's go).
- Kopfnuss with two phones; the Mali box run (phase 5 proper).

## Found

1. **Phones can't join a night on funstation.** The night advertises and shows WayDroid's bridge address, `192.168.240.112:8080`; the LAN has no route to it (curl from the dev machine fails, from the funstation host gives 200). The same held for the old in-app night (same host code), so this branch did not cause it. Fixing it is funstation setup: forward 8080 from the funstation's LAN address into WayDroid and make the QR/URL carry the LAN address (Android inside WayDroid does not know it), or give WayDroid a LAN address of its own. The test played through an SSH tunnel.
2. **Funstation's media app lost feat/funstation's keyless artwork.** This branch predates that merge. Artwork still works because the stored TMDB key was kept. Goes away when this branch takes main.

## Unresolved

- Who fixes the join address on funstation (its setup script belongs to the funstation work), and which of the two ways.
- Whether Spieltisch's release should also ship x86_64 for funstations (it runs translated today; the media app on funstation is a benchmark build with all ABIs).
