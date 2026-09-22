# Phase 5: Resume, and what has been watched

**Status:** Not started

**Deliverable:** The phone opens a title where the viewer left it, marks a
title finished when they finish it, and shows both on the card. The parity gap
closes here; the sync gap stays open and is not this phase's.

## Context

- Phase 4 — the store and its methods
- `web/public/lib/resume-point.js:14-79` — **the reference**, and the rules are
  the whole of it: under 30 seconds or 10% is a glance and resumes nothing;
  within the last `min(5%, 60s)` counts as finished
- `web/public/lib/player.js:488-514` — saved every ten seconds while playing,
  and flushed when the page goes away
- `android/ui-mobile/src/main/kotlin/PosterCard.kt:32` — the card, which today
  shows art, a title and a count
- `web/public/lib/set-badge.js:24-51` — the progress rule and the watched tick,
  and where they sit on a plate
- `android/feature/player/src/main/kotlin/PlayerUiState.kt:7-10` — states that
  it carries no position, and gives the reason. **That reason still holds.** A
  position read from the store on open is not a second copy of the playhead

## Key insight

The rules for what counts as a resume point are the part worth getting right,
and they are already written and tested on the other surface. Porting them is
cheap; re-deciding them means a phone that offers to resume a film at eight
seconds and a laptop that does not, over one library.

**When to save is where the phone genuinely differs.** The web saves on a timer
and flushes on `sendBeacon` at tab close. A phone is killed, not closed: the
process can end without a lifecycle callback that runs long enough to finish a
write. So the phone saves on pause, on leaving the player, and on `onStop` —
and the timer is the backstop, not the mechanism.

## What gets built

**`ResumePoint.kt`, pure, in `:core:playback` or beside the store.** The three
rules ported verbatim, with the web's own test cases as its tests.

**A repository in `:core:data`** over phase 4's methods, in the shape
`CatalogRepository` already establishes.

**Saving.** On pause, on leave, on `onStop`, and every ten seconds while
playing.

**Resuming.** `PlayerHandle.open` seeks to the stored position when
`ResumePoint` says it is one. `PlayerUiState` still carries no position.

**On the card.** The progress rule and the watched tick, placed as
`set-badge.js` places them. A row in `CollectionScreen` gets the same tick.

## Success criteria

- Watch four minutes of an episode, leave the app entirely, reopen: it resumes
  within a second of where it was.
- Watch fifteen seconds and leave: it does not offer to resume.
- Watch to the last thirty seconds: it is marked watched, not resumed.
- Kill the app from the recents list mid-playback: the position survives.
- `ResumePoint` tested against the same cases as `resume-point.js`.
- `./scripts/check.sh` passes.

## Risks

- **`onStop` is not guaranteed on a kill.** Saving on pause and on leave is
  what actually covers it; treat `onStop` as a third chance, not the answer.
- **A tick on a card needs the store read on the catalog path.** That is a read
  per shelf render if done carelessly. Read once into the ViewModel's state.

## Todo

- [ ] `ResumePoint.kt`, ported with its cases
- [ ] a `:core:data` repository over phase 4
- [ ] saving on pause, leave, stop, and a ten-second backstop
- [ ] resuming on open
- [ ] progress and the watched tick on cards and rows
- [ ] a real device run, including a kill from recents
- [ ] `./scripts/check.sh`
