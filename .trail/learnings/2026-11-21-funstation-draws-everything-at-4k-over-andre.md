---
title: "Funstation draws everything at 4K over an 8-bit link, and its exact film refresh rates already exist"
author: Andre
date: 2026-11-21
branch: main
cost: { minutes: 8, tokens: 105183 }
id: l-be6d479e
scope: repo
status: active
src: agent
touches:
  - crates/mediagram-game/client/src/tv/ceremony/ceremony-stage.ts
  - crates/mediagram-game/client/src/tv/table/stage-host.ts
  - android/core/playback/src/main/kotlin/PlayerFactory.kt
  - android/core/playback/src/main/kotlin/VideoRoute.kt
---

## Decided
Nothing is changed yet; these are measurements from 2026-11-21 that the code cannot show, taken read-only over SSH.

- The screen is an HP OMEN 27k monitor on HDMI. At 3840x2160@120 the link runs 8-bit YCbCr 4:2:0 (the chip is HDMI 2.0, 600 MHz), and the monitor's EDID advertises no HDR, so KWin reports "HDR incapable". HDR10 films therefore land on an SDR 8-bit link.
- The monitor lists 4K at 120, 119.88, 100, 60, 59.94, 50 and 30 Hz. 119.88 is 5 x 23.976 and 100 is 4 x 25, so film and PAL cadence need a host mode switch, not a custom mode. VRR range is 48-144 Hz, KWin has it on "Never".
- Android inside WayDroid renders at 3840x2160@120 with one display mode. The media app's UI frames had a median of 46 ms with almost every slow frame counted as "slow draw commands": GPU-bound at 4K. Spieltisch had a median of 22 ms.
- The kernel cannot load amd-pstate: "the _CPC object is not present in SBIOS". It needs the CPPC switch in the BIOS; until then the CPU has three frequency steps under acpi-cpufreq.
- Memory is already dual channel (2 x 8 GiB LPDDR4 at 3733 MT/s), so that lever is not missing.

## Tried and ruled out
- Headless Chromium on the host as a stand-in for the Android WebView: with the GLES and GL ANGLE backends it gets no WebGL context at all; only the Vulkan backend works, and that one exposes BC/S3TC/RGTC only, which says nothing about what the WebView (GLES on Android's own Mesa) exposes.

## Watch out
- Android's Mesa advertises ASTC and ETC2, which this GPU has no hardware decoder for. Whether three.js then picks a format that gets unpacked to full RGBA inside the WebView is still unconfirmed.
- kscreen-doctor answers on wayland-1 in the Bigscreen session, not wayland-0.
- The frame numbers are whatever had accumulated since the apps started that evening, not a controlled run.

In the code:
- crates/mediagram-game/client/src/tv/ceremony/ceremony-stage.ts: RENDER_SCALE 2, MIN_SCALE 1.25, the only stage that lowers its scale when slow
- crates/mediagram-game/client/src/tv/table/stage-host.ts:113 and kopfnuss/arena-engine.ts:40: fixed pixel ratios
- android/core/playback/src/main/kotlin/PlayerFactory.kt:142: frame-rate switching off on TVs
- android/core/playback/src/main/kotlin/VideoRoute.kt:42: effect graph skipped for HDR
- Probes: /sys/kernel/debug/dri/0000:*/crtc-0/amdgpu_current_bpc and amdgpu_current_colorspace; `waydroid shell -- dumpsys gfxinfo <package>`; `journalctl -k -b | grep pstate`
