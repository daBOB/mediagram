---
title: "Android: recover when the hardware video decoder stalls"
description: "Detect a video decoder that stops producing frames and rebuild the player at the same position on the other decoder, remembered per codec on the device."
status: closed
priority: P1
effort: 6h
branch: fix/android-decoder-stall-recovery
tags: [android, player, tv, codec, bug]
created: 2026-09-28
---

# Android — hardware decoder stall recovery

## The bug (reproduced 2026-09-28, TV box 192.168.0.35, Skyworth HPR302, Realtek)

"Der Astronaut – Project Hail Mary" (8.1 GB, 1080p, AV1, HDR10), direct Play (no preload):
~10 s fine → pause → Back (hides controls) → Back → the display goes black and stays black.
No crash, no ANR, no PlaybackException. logcat: `RTKC2Vdec` (Realtek hardware AV1 decoder)
spinning — `kStatusRetry count` climbing ~300 per line; `C2BqBuffer: last successful dequeue
was 27689670 us ago`, then 32 s. Home → relaunch recovers. H.264 films (Green Mile 12 GB,
Der Pate 30 GB, Chihiro, Crime 101) play cleanly on the same box. The user reported it as
"display went off".

## Decision (user, 2026-09-28)

**Retry with the other decoder:** when the video decoder stops producing frames while the
player should be playing, rebuild at the same position preferring the other decoder for that
codec on this device (e.g. the platform software `c2.android.av1.decoder` instead of the
vendor hardware one), remember that for next time, and show an error only if the retry also
fails. Not a black screen.

## Scope

- Diagnose first: which call stalls (decode vs. surface detach/attach on pause/Back — is the
  UI thread blocked?), is it AV1-only / HDR-only / this file; does 0.69.3 (main before the
  preload work) do it too. Evidence in the report.
- Stall watchdog in the player (both surfaces share `core/playback` `PlayerFactory` + the
  player view models): READY + playWhenReady + no new rendered video frame (decoder
  counters / analytics) for a few seconds → recovery.
- Recovery: rebuild the renderers with a `MediaCodecSelector` that skips the decoder that
  stalled for that MIME, re-prepare at the saved position, keep audio/subtitle choices;
  persist "skip decoder X for MIME Y" per device (small prefs); a second stall or no other
  decoder → the player's existing error screen with a clear message.
- Also make sure no player call on the main thread can block the UI on a stuck codec
  (surface detach timeouts / release) — if that is part of the black screen.
- Phone/tablet use the same path; verify nothing changes for H.264/HEVC.

## Closed 2026-09-28 — no code shipped

The premise did not hold: the "stalled" decoder log lines are what any Codec2 decoder writes while
paused, and the black screen after pause → Back → Back was a debug-build-only Compose cost, absent
on the benchmark build. The watchdog built for it was removed. Details:
`reports/debugger-260928-0200-decoder-freeze-rootcause-and-closeout-report.md`. Its Part 3 (Home
rebuilt from scratch on every return) is carried by `plans/260929-0215-tv-web-look-chrome-home-departments`
phase 04.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 01 | Diagnose (code + TV box when free), watchdog + fallback + persistence, tests | pending |
| 02 | TV box verification with the AV1 film, docs, version | pending |
