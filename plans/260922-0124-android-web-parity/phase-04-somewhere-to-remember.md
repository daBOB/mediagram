# Phase 4: Somewhere to remember

**Status:** Not started

**Deliverable:** A local store in `mediagram-core` holding the rows the web
player holds — position, watched, watchlist, kids, lists, preferences — and
core methods to read and write them. Nothing in the app uses it yet.

## Context

- `crates/mediagram-core/src/api/mod.rs` — fourteen methods, **none touching
  progress**. This phase is the first that adds to that surface
- [`260920-2221-watch-state-across-devices/phase-01-the-record-and-the-merge.md`](../260920-2221-watch-state-across-devices/phase-01-the-record-and-the-merge.md)
  — the record shape and merge rules, already designed and built for the web.
  **This phase must hold the same rows**, or the sync phase has two dialects to
  reconcile instead of one record to move
- [`260920-2221-watch-state-across-devices/phase-03-android-joins.md`](../260920-2221-watch-state-across-devices/phase-03-android-joins.md)
  — the gate that named this work and said the store belongs in `core`, not in
  Kotlin, so the merge rules are one implementation rather than three
- `web/src/state/schema.ts`, `web/src/state/store.ts` — what the web stores and
  what it calls each thing

## Key insight

**This is the gate, and its cost is not the storage.** A `rusqlite` table is an
afternoon. What is expensive to undo is the shape of the record and the names
of the methods, because five later phases and the cross-device sync all read
them, and the web player has already named every one of these things. Two
surfaces over one library should not need a translation table.

So the rule for this phase is: **the core's method names and row columns match
the web player's, and where they cannot, the phase says why.**

## What gets built

**The store**, in `mediagram-core`, beside the catalog database rather than
inside it — watch state survives a library refresh, and a refresh replaces the
catalog wholesale.

**The methods**, on the UniFFI surface: read a position, write a position, mark
watched, read what is watched, the watchlist, the kids marks, the lists, and a
free-form preference bag for phase 6. Plus export and import of the phase-01
record shape, so the sync phase has nothing left to invent.

**Nothing in Kotlin.** A `:core:data` repository over these methods is phase 5's
opening move, not this one's. This phase ends with Rust tests and an unchanged
app.

## What needs your say-so

**Profiles.** The web keys every row by profile and has a picker. The phone has
no profile at all. Three answers, and this phase cannot start without one:

1. **One implicit viewer.** Simplest, correct for a phone that is one person's.
   The sync record then needs a name to write under, and "the phone" is not a
   person.
2. **A setting.** A name typed once in Setup, matched against the profiles the
   merged record already holds.
3. **The picker, ported.** Full parity, most work, and wrong if nobody shares
   the handset.

The cross-device sync plan carries the same question open at its phase 01. One
answer should serve both.

## Success criteria

- Rust tests cover: write then read a position; a position that survives a
  catalog refresh; export producing the phase-01 record shape byte-for-byte;
  import merging by last-writer-wins per title.
- `cargo test` green, every `src/` file under 200 lines.
- The Android app builds and behaves exactly as before.
- `./scripts/check.sh` passes.

## Risks

- **Divergence from the web's names.** The failure mode is quiet: two correct
  stores that need a mapping table forever. Check each name against
  `web/src/state/schema.ts` as it is written, not afterwards.
- **UniFFI friction.** The roadmap already records that `caption::Episode`'s
  fixed-size array is not bindable. Any new type crossing the boundary here
  should be checked against that lesson before it is designed.

## Todo

- [ ] settle the profile question
- [ ] the store, beside the catalog database
- [ ] core methods, named against `web/src/state/schema.ts`
- [ ] export and import of the phase-01 record shape
- [ ] Rust tests, including survival across a refresh
- [ ] `./scripts/check.sh`
