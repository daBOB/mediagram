---
title: "Scene clips are cut by the web player's server with a re-encode, and Android marks reach it through the sync record"
author: Andre
date: 2026-11-13
branch: feat/clip-a-scene
id: l-1a4609d6
scope: repo
status: active
src: agent
touches:
  - docs/superpowers/specs/2026-11-13-clip-a-scene-design.md
  - web/src/transcode/args.ts
  - web/src/state/stats-recorder.ts
---

## Decided
- 2026-11-13, user: Android only marks a scene; the web player exports. Clips stay widescreen as the film is (no vertical crop). Purpose: upload to Instagram.
- Export re-encodes on the web player's machine with ffmpeg (libx264 + AAC stereo, fit 1080p, HDR tone mapped to SDR, max 180 s), one job at a time under nice, files kept 1 h.
- Marks are a per-profile `scenes` list on the existing sync record (TS and Rust twins), keyed by a mark id, tombstoned like My List. No format bump.
- The clip range is the web player's existing A-B loop; the Clip panel adds touch buttons so phones can set it.
- Clip playback writes no position on grown-up profiles, but does on kids profiles: the web counts a kid's daily-limit minutes from position writes, so skipping them would bypass the limit.

## Tried and ruled out
- Stream copy (-c copy): cuts snap to keyframes and HEVC/HDR/DTS sources come out in forms Instagram rejects.
- Recording in the browser (MediaRecorder, ffmpeg.wasm): real time at best, WebM, very slow on phones.
- Android posting marks to the web player's HTTP API: only works while that player is up and reachable.
- Web Share to Instagram from the page: needs a secure context, and the player is plain HTTP on the LAN, so the phone gets a download.

## Watch out
- ffmpeg ignores options placed after the last output path; put `-progress pipe:1 -nostats` before the clip arguments.
- Dolby Vision without an HDR10 base needs libplacebo; if it fails, the export must say so rather than produce wrong colours.

In the code: spec docs/superpowers/specs/2026-11-13-clip-a-scene-design.md, plan plans/261113-1602-clip-a-scene/; read-rate constants READ_RATE/READ_BURST_SECONDS in web/src/transcode/args.ts; position-based daily limit in web/src/state/stats-recorder.ts.
