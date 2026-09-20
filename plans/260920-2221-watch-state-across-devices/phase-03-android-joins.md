# Phase 03 — Android joins

**Status:** not started

## Context

- [phase-01](phase-01-the-record-and-the-merge.md), [phase-02](phase-02-the-player-syncs.md)
- `crates/mediagram-core/src/api/mod.rs` — twelve methods, none of which touch
  progress
- `docs/superpowers/specs/2026-09-20-android-player-transport-controls-design.md`
  §1, which named this piece **C** and said it would get its own design
- `CLAUDE.md` § Surface Parity

## The gap is bigger than syncing

The phone does not keep a position at all. Not "does not share one" — does not
have one. There is no DataStore, no Room and no sqlite in the Kotlin sources,
and `PlayerUiState` lost its position fields when the transport controls
landed because nothing had ever ticked them.

So this phase is two things, and the first is not about sync: **the phone
needs somewhere to remember, before it can have anything worth sharing.**

## What gets built

**A local store.** The smallest thing that holds the same rows as the web
player's `progress` and `watched`. In `core` rather than in Kotlin, so the
record-building and merging from phase 01 are one implementation rather than
two that drift — the web player's is TypeScript, and a second in Kotlin would
be a third dialect of the same rules.

**Core methods.** Read and write a position; export a record; merge and import
one. Names to match what the web player calls them, because two surfaces over
one library should not need a translation table.

**The app using them.** Save on pause, on leaving the player, and on the
lifecycle event that means the app is going away. Resume where the web player
already does — `resume-point.js` holds that rule, and its answer for what
counts as worth resuming should be ported rather than re-decided.

## Open questions

- **Profiles.** The phone has no profile picker. Phase 01 keys the record by a
  profile name, so the phone needs one to write under: a setting, the first
  profile it sees in the merged record, or a single implicit viewer. Not
  decided here.
- **When the phone pushes.** A phone is off, asleep and on a metred connection
  far more than a desktop is. The web player's timer is the wrong shape.

## Todo

- [ ] a local store in `core`, with the phase 01 record shapes
- [ ] core methods for read, write, export, merge, import
- [ ] the app saves a position and resumes from it — the parity gap closes
      before the sync does
- [ ] pull on open, push on leaving
- [ ] decide the profile question
- [ ] a real device run, on the phone that is already on adb
- [ ] `./scripts/check.sh`

## Success criteria

- The phone remembers where a title was left, across a force-quit.
- A position set on the phone shows on the Continue shelf of a web player on
  another machine, and the other way round.
- A phone with no network still plays and still remembers, locally.
