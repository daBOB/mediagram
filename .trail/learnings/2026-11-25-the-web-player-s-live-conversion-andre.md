---
title: "The web player's live conversion tone-maps every HDR title with libplacebo, decoding on the CPU"
author: Andre
date: 2026-11-25
branch: fix/web-hdr-tone-map
id: l-edf94d60
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - docs/project-changelog.md
  - docs/running-the-player.md
  - web/package.json
  - web/src/transcode/args.ts
  - web/src/transcode/clip-args.ts
  - web/src/transcode/ffmpeg.ts
  - web/src/transcode/registry.ts
---

## Decided
- Live web conversions (HLS, H.264) tone-map HDR10, HLG and Dolby Vision to SDR with the same libplacebo chain scene clips use for Dolby Vision. Before 0.195.1 they did no colour work at all: HDR10 played grey and washed out, Dolby Vision profile 5 (no HDR10 base, `dv_bl_signal_compatibility_id=0`) purple and green. Found on Captain Marvel (4K DV P5).
- libplacebo for HDR10 too, not the clips' zscale + tonemap chain: that one runs in float on the CPU and is too slow for 4K live.
- Tone-mapped titles decode on the CPU, not VAAPI: libplacebo takes frames in memory with the Dolby Vision metadata on them. Measured on the Ryzen 9800X3D + RX 7900: 4K DV at 3x real time (72 to 88 fps), then h264_vaapi as before.
- Pictures copied untouched (container- or audio-only conversions, negotiated HEVC) are never tone mapped; the browser shows HDR itself.

## Tried and ruled out
- `-init_hw_device vulkan` is not needed: libplacebo makes its own Vulkan device, even next to `-vaapi_device`.

## Watch out
- `/api/sets/<id>/cached-stream` serves only parts already in the chunk cache; seeking past them fails with "Stream ends prematurely". Probe or test a title through `/stream`.
- No startup probe for libplacebo: a player machine without it or without Vulkan fails HDR conversions with the ffmpeg error. This machine and the funstation had libplacebo on 2026-11-13.

In the code: web/src/transcode/picture-filters.ts (TONE_MAP_TO_SDR, FIT_UHD), args.ts (toneMap forces CPU decode), routes.ts (toneMap from the index's `hdr`). Evidence: spieltisch production/qa/evidence/web-dv-tone-map-before-after-2026-11-25.png.
