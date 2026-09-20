# Android foundation (phone, tablet, TV) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A standalone Android app — one APK for phone, tablet and television — that plays a film straight from the Telegram channel with no server running.

**Architecture:** A new Rust crate `mediagram-core` extracts the byte path that `crates/mediagram/src/serve/` already implements, adds the package reader, and exposes one narrow UniFFI surface to Kotlin. Above it, presentation logic is shared and composables are written twice: a touch surface and a 10-foot surface, selected at launch.

**Tech Stack:** Rust (grammers, `mlib-spec`, UniFFI, cargo-ndk) · Kotlin, Jetpack Compose, Material 3 adaptive, `androidx.tv:tv-material`, Media3/ExoPlayer, Hilt, Navigation3.

**Spec:** [`docs/superpowers/specs/2026-09-19-android-foundation-design.md`](../../docs/superpowers/specs/2026-09-19-android-foundation-design.md)

## Global Constraints

- Android pins: AGP `9.3.1`, Kotlin `2.3.21`, KSP `2.3.10`, compileSdk `37`, targetSdk `37`, minSdk `24`, Compose BOM `2026.06.01`, Media3 `1.10.1`, Hilt `2.60.1`, Navigation3 `1.1.4`.
- `androidx.tv:tv-material` is **not** covered by the Android skill; pin it at the current stable in phase 4 and record the version chosen.
- Rust: edition `2024`, rust-version `1.87`. `glass_pumpkin` stays pinned `=2.0.0-rc0`.
- The native `.so` must be **16 KB page-aligned** or it will not load on Android 15+.
- Every source file stays under **200 lines** (`docs/system-architecture.md` §2).
- `grammers_*` must appear nowhere in `crates/mlib-spec/`, or in `mediagram`'s `index/`, `media/`, `metadata/`. Check with the grep in `docs/system-architecture.md` §8.
- Conventional commits. No AI references in commit messages.
- Code comments, test names and file names must **not** reference plan artifacts — no phase numbers, no finding codes. Explain the invariant, not its origin.
- Live gates run by hand against the real channel; there is no channel in CI.

## Phases

| # | Phase | Status |
|---|---|---|
| 0 | [grammers-on-Android spike](phase-00-grammers-android-spike.md) | Complete |
| 1 | [`mediagram-core` and the UniFFI surface](phase-01-mediagram-core-and-uniffi.md) | Complete |
| 2 | [Gradle skeleton, module graph, CI](phase-02-gradle-skeleton-and-ci.md) | Complete |
| 3 | [Login and catalog; the mobile surface](phase-03-login-and-catalog-mobile.md) | Complete |
| 4 | [The TV surface](phase-04-tv-surface.md) | Blocked — needs a television to develop against |
| 5 | [Playback on all three](phase-05-playback-media3.md) | Complete — tasks 1–3 and the mobile half of task 4, including its seek gate |
| 6 | [Byte-truth gate and documentation](phase-06-byte-truth-gate-and-docs.md) | Not started |
| 7 | [First-run setup on the device](phase-07-first-run-setup.md) | Complete |
| 8 | [The catalog from the channel](phase-08-catalog-from-the-channel.md) | Complete |

`android/ui-tv/` holds a Gradle module and no source: the television half of
phase 5's task 4 lands with phase 4, on the same hardware, not before it.

## Key dependencies

**Phase 0 can end this plan.** It answers whether grammers cross-compiles and
runs on Android at all. `crates/mediagram/Cargo.toml:36` documents that
`rusqlite` is deliberately not `bundled` because `grammers-session` statically
links its own sqlite3 and two copies collide; that resolution relies on a
system `libsqlite3` the Android NDK does not expose. If no resolution is
found, the standalone approach fails and the fallback is TDLib — which is a
new brainstorm, not a later phase of this plan.

Nothing under `android/` is created until phase 0 reports PASS.

Phases 4 and 5 are independent of each other and can run in parallel once 3
lands; both are needed for 6.

## Review

_Filled in as phases complete._
