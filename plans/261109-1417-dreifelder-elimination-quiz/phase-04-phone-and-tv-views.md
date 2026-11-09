---
phase: 4
title: "Phone and TV views"
status: completed
priority: P1
effort: "5h"
dependencies: [3]
---

# Phase 4: Phone and TV views

## Overview
What each phone and the TV show. Views stay pure (recomputed and diffed on every event).

## Tests first
- Host (`dreifelder_views.rs` tests): alive player gets `Choice` with 3 options and a clock; out player gets `Choice` with prompt "Du bist raus – rate mit" and no clock pressure on the room; after a pick `picked` is set; reveal gives alive-right "Weiter!", alive-wrong "Raus!", ghost "Richtig/Falsch, ohne Wertung".
- Client (`bun test`): helper that places players onto field 1/2/3 from `picks` (alive vs out, unpicked row); 3-key phone layout rule.

## TV (`client/src/tv/TvDreifelder.tsx` + `dreifelder/`)
Question with three coloured fields (reuse `ANSWER_COLORS`); at reveal avatars (`Token`) stand in the field they chose, the right field lights, the wrong ones darken; eliminated players slide to an "Out" row; header "Runde x von 12 – n noch dabei". Final: `TvFinal ranked`. Reuses `TvQuiz` clock/seat pieces where they fit.

## Phone
3 `.key`s: CSS rule so three keys fill the grid (third spans both columns) instead of an empty fourth cell. Use the design tokens now in `styles.css` (`--radius-*`, `--text-*`, `--felt-deep-edge`).

## Related Code Files
- Create: `src/dreifelder_views.rs`, `client/src/tv/TvDreifelder.tsx`, `client/src/tv/dreifelder/dreifelder-fields.ts` (+ css)
- Modify: `client/src/tv/TvApp.tsx` (scene switch), `client/src/styles.css` (3-key grid), `android/ui-tv/.../TvGamesShelf.kt` (`ShelfGame("dreifelder", "Dreifelder", "2–8 players", null, null)`)

## Success Criteria
- [x] Screenshots of question, reveal and final on the TV page and phone width, kept in the plan's `evidence/`.
- [x] `bun run build` clean.
- [ ] Jank no worse than Kopfnuss rounds (1-2 %): needs the box run.

## Risks
The shelf entry list is not release-session territory (they only asked to avoid `ui-tv/catalog/TvCatalogScreen.kt` and `core/update`); Kopfnuss already added its entry the same way. <!-- Updated: Validation Session 1 -->

## Done (2026-11-09)
- Evidence in `evidence/` (TV 1920x1080, phone 390x844): lobby, question intro/open, reveal with two out, all-wrong reveal, final ceremony; phone alive question, out, ghost question and reveal, final.
- After the first renders: pawns in the fields went up a size, and the empty clock slot at the reveal now says "Raus: …", "Alle daneben – alle bleiben drin" or "Alle richtig – alle bleiben drin".
- No bun test for the 3-key CSS rule: a CSS rule has no logic to unit-test; the phone screenshots show it.
- `byFinish` (TvFinal.tsx) and `final_by_order` (quiz_views.rs) are shared with Kopfnuss instead of copied.
- Jank on the box not measured yet (box run is phase 5, needs the user's go-ahead).
