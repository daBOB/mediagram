# Code review — games lobby (2026-11-13), and what was done

Reviewer: code-reviewer agent over the uncommitted diff. Fixes verified by tests and on the dev host / funstation.

| # | Finding | Severity | Done |
|---|---------|----------|------|
| 1 | Right then Left mid-fade left the old backdrop: layers keyed by game kind, the reused `<img>` never fired `load` again | high | layers keyed by id (`ShelfBackdrop.tsx`); Playwright: back-and-forth ends on the right backdrop |
| 2 | After a dropped socket OK could start the host's old game (`sent` set before a send that was dropped) | medium | `start()` re-picks unless the host already has the focused game |
| 3 | Moves faster than the 400 ms fade dimmed toward the felt | medium | keeps the last fully faded-in layer under the one fading; Playwright: top opacity stays 1.00 on fast moves |
| 4 | TV's configure repeated stale quiz/kids/adult and could undo the leader's kids-mode tap | medium | host takes only `game` from the TV (`room.rs`), test `the_tv_changes_only_the_game_whatever_else_it_sends` |
| 5 | Echo detection held one pick in flight; a late echo could pull the row back | medium | ordered list of unechoed picks (`use-shelf-pick.ts`) |
| 6 | One Back on the shelf now ends the night for every phone | design | first Back asks ("Press Back again to end the game night", 3 s), second leaves; tests + checked on the box |
| — | Row glided from 0 on return from a game | low | first scroll is instant |
| — | `Catalogue` in `FakeSpieltischCore.kt` unused | low | removed; the core's `game_catalogue()` export stays (removing it means regenerating the bindings) |

Checked fine by the reviewer: no path traversal in `GamePageClient`, host validates every start, art fallbacks, Enter handling, retry.
