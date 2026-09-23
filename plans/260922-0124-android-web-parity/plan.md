# Android reaches the web player — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the gap between the two surfaces over one library, soonest-value
first — beginning with the 312 sets whose second audio language the phone cannot
reach and the 206 whose subtitles it cannot draw at all.

**Architecture:** Three tiers, in the order they must be built. Choices ExoPlayer
already knows about — audio, text, speed, how the picture is fitted — need
nothing outside `:ui-mobile` and `:core:playback`. Everything a viewer expects to
still be true tomorrow — resume, watched, watchlist, lists, remembered choices —
waits on a local store in `mediagram-core`, which today exposes fourteen methods
and not one that touches progress. The screens that read that store come last,
because they are assembly over settled rules.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 `1.4.0`, media3 `1.10.1`,
Hilt, Robolectric, MockK; Rust and UniFFI for the core surface phase 4 adds.

**Reference:** the web player, per CLAUDE.md § Surface Parity. Every phase names
the module that already made the decision rather than deciding it again.

## Global Constraints

- Media3 `1.10.1`, Compose BOM `2026.06.01`, Material3 `1.4.0`, minSdk `24`,
  targetSdk `37`, Kotlin `2.3.21`. Pins, not floors.
- Module direction is `ui → feature → core:data → core:rust`, and
  `:core:playback → :core:data`.
- Every media3 symbol is `@UnstableApi`; new files touching one carry
  androidx's opt-in, as `CacheProvider.kt` explains.
- Every file this work creates or grows stays **strictly under 200 lines**.
- **No version bump on a branch.** Compute it at the merge against `main` as it
  then stands.
- Conventional commits. Code comments, test names and commit messages name the
  invariant, never a phase or a finding code.
- `./scripts/check.sh` passes before anything is called done. Without
  `ANDROID_HOME` it skips the Android half and proves nothing.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [Audio a viewer can choose](phase-01-audio-tracks.md) | Not started |
| 2 | [Subtitles that draw](phase-02-subtitles.md) | Not started |
| 3 | [Speed, and how the picture is fitted](phase-03-speed-and-picture.md) | Not started |
| 4 | [Somewhere to remember](phase-04-somewhere-to-remember.md) | Superseded — done by [android watch-state sync](../260922-2135-android-watch-state-sync/plan.md) |
| 5 | [Resume, and what has been watched](phase-05-resume-and-watched.md) | Superseded — done by [android watch-state sync](../260922-2135-android-watch-state-sync/plan.md) |
| 6 | [Choices that stick](phase-06-choices-that-stick.md) | Not started |
| 7 | [The next episode](phase-07-the-next-episode.md) | Not started |
| 8 | [Watchlist, kids and lists](phase-08-watchlist-kids-and-lists.md) | Superseded — done by [android watch-state sync](../260922-2135-android-watch-state-sync/plan.md) |
| 9 | [A start page](phase-09-a-start-page.md) | Superseded — done by [android watch-state sync](../260922-2135-android-watch-state-sync/plan.md) |
| 10 | [Search, notes and documents](phase-10-search-notes-and-documents.md) | Not started |

Phases 1–5 are written out. Phases 6–10 are sketched: they are real work with a
settled purpose, and the phase that unblocks each one will know more about its
shape than this document can.

## Key dependencies

Phases 1, 2 and 3 are independent of each other and of everything below. Any of
them can ship alone and each is worth shipping alone.

**Phase 4 is the gate.** It decides where a position lives and what the core
calls the operations on it. Phases 5, 6, 7, 8 and 9 all read that decision, and
getting it wrong is the only mistake here that is expensive to undo.

Phase 5 needs 4. Phase 6 needs 4 and whichever of 1–3 shipped. Phase 7 needs 5.
Phase 8 needs 4. Phase 9 needs 5, 7 and 8, and is otherwise a port.

## What is already there, and must not be rebuilt

- **`PlayerViewModel` / `PlayerHandle` own the library; media3's state holders
  own transport.** Settled by the transport-controls round and written into
  `feature/player`'s module documentation. Phases 1–3 attach to it.
- `Shelves.kt` — grouping, natural order and the document rule, all tested.
- `AppChrome.kt`'s `MenuItem(label, disabledReason)` — an unavailable option
  that says why under its own label. Any new one reuses it.
- `PreferenceScope` has a web counterpart in `preference-scope.js`: TMDB show
  key, then show name, then set id. Phase 6 ports that order, it does not
  invent one.
- `resume-point.js` — what counts as worth resuming, a glance, or finished.
  Phase 5 ports those rules.
- `home-shelves.js` — the Continue and Next up model, pure and tested. Phase 9
  ports it.

## Out of scope

**The television surface.** `:ui-tv` has a `build.gradle.kts` and no source, so
the Fire Stick, the Chromecast and the TV box have no screen to put any of this
on. That is a new surface, not a gap in an existing one, and it gets its own
round. Everything phases 1–8 build is surface-independent and waiting for it.

**Cross-device sync.** Already planned, at
[`260920-2221-watch-state-across-devices/phase-03-android-joins.md`](../260920-2221-watch-state-across-devices/phase-03-android-joins.md).
Phase 4 here builds the local store that phase its gate names, so the two must
agree on the record shape; the pushing and merging stay there.

**A Conversion block, an Evicted row, catalogue-side playback stats, posters
shipped with the catalog.** Deliberate differences, already recorded in
`docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md` §9.

**Documents that open.** Phase 10 covers it. The phone now shows a course's
documents and says it cannot open one — the vanishing was the bug and it is
fixed; a viewer for handouts is a feature.

## What needs your say-so

**Profiles.** The web player has a picker and keys watch state by profile. The
phone has none. Phase 4 cannot store a position without deciding whether the
phone is one implicit viewer, carries a setting, or grows the picker. Named in
phase 4, not decided here.

## Review

_Filled in when the work lands._
