# Android player transport controls — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Android player play/pause, a position readout, skip by ten seconds either way, and a scrubber — and prove, on hardware, that seeking across a part boundary works before any control that seeks is committed.

**Architecture:** media3's own Compose state holders own transport; `PlayerViewModel` and `PlayerHandle` keep the library. `PlayerScreen` already takes the `Player` and drives `rememberPresentationState` from it, so the controls follow that pattern rather than introducing a second one. Position never flows through the ViewModel, so `PlayerUiState` loses fields that have never ticked.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 `1.4.0`, Media3 `1.10.1` (`media3-ui-compose`), Robolectric, MockK.

**Spec:** [`docs/superpowers/specs/2026-09-20-android-player-transport-controls-design.md`](../../docs/superpowers/specs/2026-09-20-android-player-transport-controls-design.md)

## Global Constraints

- Worktree `mediagram-android`, branch `android-foundation`. Not `main` — `android/` does not exist there.
- Every source file stays under **200 lines** (`docs/system-architecture.md` §2). `PlayerScreen.kt` is at 167.
- Media3 `1.10.1`, Compose BOM `2026.06.01`, Material3 `1.4.0`, minSdk `24`. Pins, not floors.
- **No Material icons dependency exists and none is added.** `PlayerScreen` draws its back arrow with `Text("←")`; the controls use text glyphs the same way.
- Every file touching a media3 extension API carries `@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)`.
- Conventional commits. No AI references in commit messages.
- Code comments, test names and file names must **not** reference plan artifacts — no phase numbers, no finding codes. Explain the invariant, not its origin.
- Nothing transcodes. If a title will not play, record the codec; it is a finding, not a reason to convert.
- The live gate runs by hand against the real channel; there is no channel in CI.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [Seek increments and the trimmed state](phase-01-seek-increments-and-trimmed-state.md) | Complete |
| 2 | [Play, pause and the position readout](phase-02-play-pause-and-the-position-readout.md) | Complete |
| 3 | [Seeking, proven then shipped](phase-03-seeking-proven-then-shipped.md) | Complete — phone only, as phase 3 intends |

## Key dependencies

Phase 1 is groundwork in `core:playback` and `feature:player` with no UI in it;
phases 2 and 3 both build on the trimmed `PlayerUiState`.

Phase 2 ships controls that **do not seek** — play, pause, and the clock. It is
useful on its own: today the player cannot be paused at all.

**Phase 3 holds the gate.** `PlayerScreen.kt:44` forbids shipping a seek control
before a seek across a part boundary is proven, and phase 5 task 4 step 5 of the
android-foundation plan is that proof, never run. Skip and scrub are both seeks,
so both wait for it. They are built, proven on a real phone against a cleared
cache, and only then committed — in that order, in one phase, so the order
cannot come apart.

If the gate fails, phase 3 stops and the failure becomes work of its own. Phases
1 and 2 still stand.

## Out of scope

The television surface (phase 4 of the android-foundation plan, blocked for want
of a television), the audio track picker, the buffer readout, watch state and
next-episode autoplay. §1 and §9 of the spec say why, and where each one goes.

## Review

All three phases are complete, in seven commits, `1494fbb..1223d26`.

Phase 1 set both seek increments to ten seconds and trimmed `PlayerUiState`
down to what it actually knows. Phase 2 gave the phone play/pause, a position
readout and a bar that takes itself away. Phase 3 built skip and scrub, held
them out of the history, ran the hardware gate, and committed only once it had
passed — which is the order the phase exists to enforce.

**The gate passed, on all four probes.** "Blade: Trinity", two parts, boundary
at byte 3,758,096,384: against a cleared cache, playback ran 1:00:18 → 1:04:02
untouched at 1.0× while the reads crossed it. Phase 3's review has the rest,
including the boundary estimate that was two minutes late and the run that was
discarded because of it.

Found afterwards, none of it blocking. Two were fixed: the bar drew with no
window-inset padding and sat partly under a three-button navigation bar, and
`clockTime` formatted against the platform default `Locale`. Two are recorded
in spec §9 and wait on a decision rather than a fix — pressing a control does
not re-arm the bar's fade timer, and a seek shows no sign that it is buffering.
One is follow-up work in another module, written up in phase 3's review: every
seek logs an `InterruptedException` from `MlibDataSource` as a load failure.
