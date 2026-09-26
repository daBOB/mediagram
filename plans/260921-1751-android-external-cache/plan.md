# A cache that can live on another volume — Implementation Plan

> **Superseded 2026-09-25** by [`260925-2046-external-cache-volume-and-lan-chunk-server`](../260925-2046-external-cache-volume-and-lan-chunk-server/plan.md). The budget half shipped differently in `b91e9e8` (live slider); phase 1 Task 1 is reused there verbatim.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make where the Android cache lives and how large it grows a viewer's
choice, so a device with room can hold more than one gibibyte less than a film.

**Architecture:** `SimpleCache` takes a `java.io.File`, which rules out the
Storage Access Framework and leaves exactly one permission-free path to
removable media: the app-private directories `getExternalCacheDirs()` creates
per volume. Two pure modules decide what the choices are (`CacheVolumes`,
`CacheBudget`), a settings record remembers which was made, and `CacheProvider`
— today a hardcoded directory and a hardcoded constant — reads both.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, media3
(`SimpleCache`, `CacheDataSource`), Robolectric for the tests that need a real
`Context`.

**Spec:** [`docs/superpowers/specs/2026-09-21-android-external-cache-design.md`](../../docs/superpowers/specs/2026-09-21-android-external-cache-design.md).
Where this plan and the spec disagree the plan is the later word and the phase
that diverges says so. Two such divergences exist already, both in phase 1 —
see **Divergences from the spec** below.

## Global Constraints

- Worktree `mediagram-android-cache`, branch `android-external-cache`, forked
  from `main` at `3b824ff`.
- **No new permission enters `AndroidManifest.xml`.** The entire design exists
  to avoid one. A task that finds itself wanting `MANAGE_EXTERNAL_STORAGE` or
  `READ_EXTERNAL_STORAGE` has taken a wrong turn and should stop, not ask.
- **`SimpleCache` takes a `java.io.File`.** No `content://` URI, no
  `DocumentFile`, no `Uri` anywhere in the cache path.
- Every file this work **creates or grows** stays **strictly under 200 lines**.
  200 exactly is over. `CacheProvider.kt` is 95 at the fork and loses its
  volume resolution rather than growing past the limit.
- Media3 `1.10.1`, Compose BOM `2026.06.01`, Material3 `1.4.0`, minSdk `24`,
  targetSdk `37`, AGP `9.3.1`, Kotlin `2.3.21`. Pins, not floors — raising
  media3 changes `:core:playback`'s source compatibility and is never part of
  this work.
- Module dependency direction is `ui → feature → core:data → core:rust`, and
  `:core:playback → :core:data`. **`:core:data` must not learn what a
  `CacheBudget` is**; it stores two strings and stays ignorant.
- Every media3 symbol is `@UnstableApi`. New files touching one carry
  `@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)`
  — androidx's opt-in, not Kotlin's, for the reason `CacheProvider.kt` gives.
- **No version bump on the branch.** Compute it at the merge against `main` as
  it actually stands. The spec §10 names `0.18.0`; that number is a prediction
  about where main will be, and the last such prediction was wrong.
- `scripts/check.sh` passes before anything is called done. Without
  `ANDROID_HOME` it skips the Android half entirely and proves nothing about
  this work — export it or run `(cd android && ./gradlew testDebugUnitTest lint)`
  directly.
- Conventional commits. No AI references. Code comments, test names and commit
  messages carry no plan references — they name the invariant, not its origin.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [What the choices are](phase-01-volumes-and-budget.md) | Not started |
| 2 | [A cache that reads the choice](phase-02-a-cache-that-reads-the-choice.md) | Not started |
| 3 | [Somewhere to make it](phase-03-the-storage-screen.md) | Not started |

## Key dependencies

Phase 1 is pure and changes no behaviour: two functions and their tests, with
nothing calling them. It exists first so that the rules — which volumes are
offered, how a budget resolves — are settled and tested before anything
depends on them.

Phase 2 is the gate. It is where the app's behaviour actually changes, and
where it must keep working on a device with no removable volume at all, which
is every device available to test on. After phase 2 the cache is bigger on
every device even though nothing can yet be chosen, because the default
budget replaces the 2 GiB constant.

Phase 3 needs 1 and 2 and is assembly: a screen over settled rules.

## Divergences from the spec

1. **The budget clamps against capacity, not free space.** The spec's
   `min(8 GiB, free / 2)` reads free space alone. Free space on a volume the
   cache is already using excludes what the cache holds, so re-resolving on
   each start would ratchet the budget down and trigger mass eviction. Phase 1
   resolves against `free + alreadyHeld - floor` instead. Same intent, correct
   across restarts.
2. **Every choice clamps, not only "as much as fits".** The spec clamps the
   open-ended preset. Clamping all of them is one rule instead of two and
   covers a fixed preset that was reachable when chosen and is not any more.

## What is already there, and must not be rebuilt

- `CacheProvider.get()` — the single-instance guard and the off-thread open.
  It gains a directory and a number; the reasoning in its doc comment about
  why it is `suspend` and why it is `StandaloneDatabaseProvider`-backed stays
  true and stays put.
- `CacheProvider.occupancy()` — already the System screen's Held row.
- `MenuItem(label, disabledReason)` in `AppChrome.kt:187` — an option that is
  unavailable and says why underneath its own label. Phase 3 reuses it for
  presets that do not fit; it does not invent a second answer to the question.
- `PackageSettings` in `:core:data/settings/` — the interface / in-memory /
  real-implementation shape phase 2's `CacheSettings` copies.
- `DataModule.kt` — where settings are provided to Hilt.
- `SystemRows` / `SystemScreen` — the pure-wording-beside-composable split
  phase 3 mirrors.

## Out of scope

**The television surface.** `:ui-tv` has a `build.gradle.kts` and no source;
Fire Stick, Chromecast and the TV box therefore have no screen on which to
open a setting and keep internal storage. Everything phases 1 and 2 build is
surface-independent and waiting for them. Three of the four devices that
motivated this work are in that sentence, which is why it is here and not
discovered later.

**The LAN cache server.** A box on the network holding bytes for every device
is the other half of what was asked for and gets its own spec. The shape is
already visible — the web player answers HTTP Range requests over a set's
bytes through its own configurable cache, so Android's side is an
`HttpDataSource`, not a server — but none of it is in this plan.

**Copying cached bytes between volumes.** Decided against in spec §6, not
deferred: the data's defining property is that it can be fetched again.

## Review

_Filled in when the work lands._
