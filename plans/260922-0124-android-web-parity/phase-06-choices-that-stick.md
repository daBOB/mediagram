# Phase 6: Choices that stick

**Status:** Not started — sketch

**Deliverable:** Audio language, subtitle language, subtitle size and backing,
speed and fitting are remembered per show, so a viewer sets them once for a
series rather than once an episode.

## Context

- Phases 1, 2, 3 — the controls; phase 4 — the preference bag they write to
- `web/public/lib/preference-scope.js:30-45` — **the reference** for what "this
  show" means: the TMDB show key, then the show name, then the set id
- `web/public/lib/audio-chooser.js:56-73` — remembered by **language**, never by
  track ordinal, because episode two's track order is not episode one's

## The shape

`PreferenceScope.kt`, pure, porting the three-step key order. Each control from
phases 1–3 reads its remembered value on open and writes on change. Nothing
here is a new decision; it is the web's rules over phase 4's storage.

The one thing to hold on to: a remembered audio choice is a **language tag**,
resolved against whatever tracks the current file happens to carry. A stored
ordinal silently selects the wrong language on the next episode.

## Todo

- [ ] `PreferenceScope.kt`, ported, with tests
- [ ] each control reads and writes through it
- [ ] a real device run across two episodes of one show
- [ ] `./scripts/check.sh`
