# Clip a Scene Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Viewers mark scenes on any surface (synced per profile), and the web player exports any A–B stretch as an Instagram-ready MP4.

**Architecture:** A new per-profile `scenes` list rides the existing sync record (TS in `web/src/state/`, Rust twin in `crates/mediagram-core/src/state/`), shaped like My List but keyed by a mark id. The web server gets a clip-export job (ffmpeg, libx264, tone mapping for HDR) behind profile routes. The web player gets a Mark button, a Clip panel built on the existing A–B loop, and a "Marked scenes" shelf on Home; Android gets only the Mark button.

**Tech Stack:** Bun + TypeScript server, vanilla JS client (`web/public`), Rust core + UniFFI, Kotlin/Compose Android (mobile + TV), ffmpeg.

**Spec:** `docs/superpowers/specs/2026-11-13-clip-a-scene-design.md` (approved by the user 2026-11-13 16:02, "yes implement").

## Global Constraints

- Clip is widescreen as the source; no crop, no vertical (user's decision).
- Android marks only; no export, no marks list on Android (user's decision).
- Clip max length **180 s**; output MP4, H.264 libx264, AAC stereo 160k, `+faststart`, fit within **1920×1080**, never upscaled.
- HDR10/HLG tone mapped to SDR BT.709; Dolby Vision via `libplacebo`; DV failure → clear error, never wrong colours.
- One export at a time per server; ffmpeg runs under `nice`; finished files expire after **1 hour**.
- Mark-opened range preset: **from = max(0, at − 15), to = at + 15**.
- Wire key `scenes` on `ProfileState` (camelCase), no `SYNC_FORMAT` bump; row `{ id, setId, at, updatedAt, removed? }`.
- Clip playback writes no position/stats on grown-up profiles; on a kids profile it writes as normal (daily limit is counted from position writes).
- Versioning: media line minor bump **0.178.x → 0.179.0** in `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` versionName, `Cargo.lock` entries; never edit versionCode. Bump with a pattern, not an exact string (another session commits to main).
- Code comments explain the why; never reference plan/phase/task numbers.
- Commit messages: conventional commits, no AI references.

## Review Focus

1. **A row from an older or hostile document** (missing `at`, negative `at`, non-string `id`) — dropped alone, rest of the record kept. Test in Task 1 and Task 2 parsers.
2. **Same mark edited on two devices** (one removes, one re-marks later) — later `updatedAt` wins, tombstone respected on both TS and Rust merges. Test in Tasks 1, 2.
3. **Export asked with bad input** (to ≤ from, length > 180 s, unknown set, profile mismatch on download) — 400/404/403, no ffmpeg spawned. Test in Task 5.
4. **Second export while one runs** — 409 "busy", first job unaffected. Test in Task 5.
5. **Phone tab backgrounded mid-export** — on `visibilitychange` to visible the panel polls again and downloads once, not twice. Test in Task 7 (pure helper `nextClipStep`).

## Phases

| # | Phase | Tasks | Status |
|---|-------|-------|--------|
| 1 | [Synced scene marks](phase-01-synced-scene-marks.md) | 1 web state, 2 Rust core | todo |
| 2 | [Android mark button](phase-02-android-mark-button.md) | 3 | todo |
| 3 | [Clip export server](phase-03-clip-export-server.md) | 4 args, 5 jobs + routes | todo |
| 4 | [Web clip panel and shelf](phase-04-web-clip-panel-and-shelf.md) | 6 mark, 7 panel, 8 shelf | todo |
| 5 | [Parity note, version, verification](phase-05-parity-version-verification.md) | 9 | todo |

Order: Task 1 before 2 (Rust mirrors TS wire shape); 3 after 2; 4–5 independent of 1–3; 6–8 after 1 and 5; 9 last.

## Execution

Branch `feat/clip-a-scene` in worktree `../mediagram-clip` (fresh worktree needs the Rust core built and FFmpeg jniLibs copied before Android builds). Merge to main when done.
