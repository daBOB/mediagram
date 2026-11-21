---
title: SIMD is already used wherever it matters on the Ryzen machine; only AV1 inside Android misses it
author: Andre
date: 2026-11-21
branch: main
cost: { minutes: 7, tokens: 58306 }
id: l-cf11b4a0
scope: repo
status: active
src: agent
touches:
  - plans/reports/research-261121-2320-ryzen-3-4300u-datasheet-and-optimization-techniques-report.md
  - scripts/build-android-ffmpeg.sh
  - web/src/transcode/clip-args.ts
---

## Decided
2026-11-21, asked "any SIMD usage that would help us?" for the Ryzen 3 4300U (AVX2, FMA, AES-NI, SHA-NI; no AVX-512). Answer from disassembling the installed libraries: nothing to hand-write. The decoders and our Telegram crypto already use the chip's instructions, on x86_64 and on arm64.

## Tried and ruled out
- Compiler flags for a newer CPU baseline on the Rust binaries: they are I/O-bound, and the crypto crates pick AES and SHA instructions at run time anyway.
- Assembly in our own FFmpeg audio library for x86_64: it is a fallback behind the device's decoders and audio decoding is cheap.

## Watch out
- The one gap: the dav1d inside WayDroid's Android FFmpeg looks compiled without its assembly, so AV1 in the Android app on funstation is plain C. Only 11 of 29,086 sets were AV1 that day. This is from instruction counts, not a timed decode.
- The host's ffmpeg and dav1d are full AVX2, so a slow AV1 title in the app is not evidence the chip cannot decode it.
- HDR10 clip export tone-maps on the CPU in float; Dolby Vision already uses the GPU path.

In the code:
- scripts/build-android-ffmpeg.sh:83 has --disable-asm for x86_64
- web/src/transcode/clip-args.ts:56 is the zscale + tonemap chain, :66 the libplacebo one
- Method: `objdump -d <lib> | grep -c ymm`; for crypto count aesenc / sha256rnds2 (x86_64) and aese / sha256h (arm64)
- Android's decoder library on funstation: rootfs of WayDroid, lib64 of the hardware partition, libavcodec.so
- Codec counts: `SELECT vcodec, COUNT(*) FROM sets GROUP BY 1` on the channel index library.db
