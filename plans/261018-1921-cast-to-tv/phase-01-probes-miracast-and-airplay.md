# Phase 01 — Probes: Miracast display, AirPlay from Safari

**Spec:** §6, §7, §12. **Status:** pending. **Kind:** spike. Nothing is committed except the report.

## Why

Two routes rest on facts this project has not measured:
1. Whether Xiaomi's Cast, connected to the Hisense's AnyView Cast, shows Android a **presentation display** an app can draw on, or only mirrors.
2. Whether Safari AirPlays our streams to the Hisense: a direct `/api/sets/<id>/stream`, and a transcoded title. For the transcoded one, whether hls.js over `ManagedMediaSource` offers AirPlay once the playlist is added as an alternate `<source>`. That is WebKit's route for MMS; the fallback is native HLS. Also whether subtitles arrive.

Both need the user's hands, so **ask before starting**, then guide step by step.

## Task 1.1: Miracast probe (tablet `caad49da`)

- [ ] Ask the user to switch on the Hisense, enable AnyView Cast, and connect the tablet from its Cast tile.
- [ ] Run `ANDROID_SERIAL=caad49da adb shell dumpsys display | grep -iE "Display Devices|mDisplayId|FLAG_PRESENTATION|type WIFI|name="`.
  - **Positive:** a `WIFI` display with `FLAG_PRESENTATION`, and its name.
- [ ] Run `adb shell cmd display get-displays` (Android 16) and record the display id and name.
- [ ] Ask the user to disconnect.

## Task 1.2: AirPlay probe (the user's Apple device)

- [ ] Make sure the live web player is reachable on its LAN address. It is `bun --watch` on :8770 per memory; do not start the real player for this.
- [ ] Prepare a throw-away page in the session scratchpad, not the repo: `<video controls x-webkit-airplay="allow">`.
  - Case A: `src` set to the LAN URL of `/api/sets/<a direct set>/stream`.
  - Case B: `src` set to a transcode playlist got from `POST /api/sets/<id>/transcode`.
  - Case C: hls.js from `/lib/hls.mjs` over `ManagedMediaSource`, with `<source type="application/x-mpegURL" src=playlist>` added as the alternate.
  - Each case with a `<track>` from `/api/sets/<id>/subtitles/<track>.vtt`.
  - Serve it from the player's own static path? No: use `bun -e` serving the scratchpad file on another port, with the same host.
- [ ] With the user, for each case, record:
  - does the AirPlay button appear;
  - does the Hisense play;
  - are the subtitles shown;
  - what the web player's log shows the TV requested (range requests, user agent).

## Task 1.3: Report

- [ ] Write `plans/261018-1921-cast-to-tv/reports/probe-261018-miracast-and-airplay-report.md`:
  - Miracast: positive or negative, the display name, and the flags;
  - AirPlay: a table of cases × (button, plays, subtitles), plus the TV's requests;
  - what changes for phases 09 and 10.

## Success criteria and what follows

- **Miracast negative** (mirror only): phase 09 shrinks to "keep the player full screen while mirrored". Stop and tell the user before building it.
- **AirPlay:**
  - Case C works → phase 10 uses the alternate source.
  - Only Case B works → phase 10 uses native HLS in Safari when AirPlay is available.
  - Neither works → stop and tell the user.
- **No subtitles over AirPlay** → note it in spec §7 as a known limit; do not build around it.
