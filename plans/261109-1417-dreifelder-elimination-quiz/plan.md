---
title: "Dreifelder: elimination quiz party game"
description: "2-8 players pick field 1/2/3; wrong pick = out; last one standing wins. New game next to Kopfnuss."
status: in-progress
priority: P2
mode: tdd
created: 2026-11-09
branch: feat/kopfnuss
base: main 411a7a53 (0.163.0); merge main before release (spieltisch-games is at 0.165.0)
blockedBy: []
blocks: []
---

# Dreifelder

Brainstorm: `plans/reports/brainstorm-261109-1417-dreifelder-and-price-guess-report.md`.
Original game; no show names, art or look. Price game is a separate later plan (needs Creators API access).

## Rules (agreed)
- 2-8 players. TV: question + 3 fields (1/2/3). Phone: 3 buttons. 12 s after a 3 s intro.
- Wrong pick or no pick = out. All alive players wrong in one round = nobody out.
- Out players keep guessing on their phone: no points, no say in when a round ends.
- Last one alive wins. Cap 12 questions: fastest total of correct answers wins. Then the shared ceremony.
- Questions: 12 per game, 9 from `questions/allgemein.json` + 3 film questions from the film pool (correct + 2 of the 3 wrong answers). Films missing -> all general.

## Decisions (1, 2, 6 confirmed by user in Validation Session 1; 3-5 planning defaults)
1. **Not a contained `Quiz`.** `Quiz` has 4 options hard-wired, ends a question only when every connected player has answered (ghosts would stall rounds) and pays points to everyone. `DreifelderGame` keeps its own small round machine and reuses `Round::shuffled` (gets an option count) and the tap clamp (extracted from `Quiz::on_action`). Still approach A (new game like Kopfnuss); B (elimination inside `Quiz`) stays rejected.
2. **Tie among players out in the same round:** ranked by right guesses made *after* dropping out (before that all of them were equal, since every alive player answered every earlier round right). This is what makes ghost guessing count. Remaining ties: fastest correct total, then player id.
3. **The 12-question cap counts repeated all-wrong rounds**, so a game always ends.
4. **Final scores follow placement** (100 per player beaten) so the ceremony's score order equals the finishing order.
5. **Leaver:** an alive player who disconnects without a pick is out at the next reveal, even in an all-wrong round (confirmed by user 2026-11-09 after review: otherwise an absent player could win at the cap on the others' mistakes). If that would put everyone out, or no alive player is connected at a reveal, the game ends; alive players are ranked by speed total.
6. **Kids mode:** blocked, as Kopfnuss is.

## Phases
| # | Phase | Status |
|---|-------|--------|
| 1 | [Protocol, settings, lobby](phase-01-protocol-settings-and-lobby.md) | completed |
| 2 | [Elimination rules (pure)](phase-02-elimination-rules.md) | completed |
| 3 | [Game state machine](phase-03-game-state-machine.md) | completed |
| 4 | [Phone and TV views](phase-04-phone-and-tv-views.md) | completed |
| 5 | [Verification and release](phase-05-verification-and-release.md) | in-progress |

Each phase: tests first, watch them fail, then implement.

## Key dependencies
`Round::shuffled` and `Answer` (quiz_config.rs), `QuestionSupply` + shared `used` history (factory.rs), `TvFinal ranked` (TvKopfnuss pattern), Kopfnuss sound is NOT reused (follow-up).

## Open questions
- Decisions 3-5 (cap counts repeated rounds, placement scores, leaver rule) were not asked about; challenge them if wrong.
- Poster/shelf art for the TV shelf: none yet (Kopfnuss has none either).

## Validation Log

### Validation Session 1 (2026-11-09)
Verification (Full tier, 22 claims over the 5 phases): verified 21, failed 1, unverified 0.
- Failed: phase 3 said `QuestionSupply::deck` gives general + film questions; `deck()` (kopfnuss_supply.rs:86) draws general questions only, films come from the factory's separate draw. Fixed below.
- Also corrected by me: phase 4 said the TV shelf file is off limits. The release session only asked to avoid `ui-tv/catalog/TvCatalogScreen.kt` and `core/update`; Kopfnuss already added its `TvGamesShelf.kt` entry.

Interview:
1. Question mix -> **9 general + 3 film** (phase 1 factory draws 3 films; phase 3 mixes).
2. Round engine -> **own round machine** reusing `Round::shuffled` (option count) and the extracted tap clamp (decision 1 confirmed; overrides the brainstorm's "one Quiz per round").
3. Same-round drop-outs -> **more right guesses after dropping out** (decision 2 confirmed).
4. Kids mode -> **blocked**, like Kopfnuss (decision 6 confirmed).

### Whole-Plan Consistency Sweep
Searched all plan files for `deck`, `Quiz per round`, `general + films`, `off limits`, `not yet confirmed`: no stale statements left after the edits; phases 1, 3 and 4 match plan.md.

## Review (2026-11-09)

Built test-first on feat/kopfnuss after fast-forwarding to spieltisch-games 0.165.0; version 0.166.0.

- Tests: `cargo test -p mediagram-game` 345 pass, 30 of them new (rules 11, game 10, views 4, config 2, quiz 2, settings 1); `bun test` 103 pass; `bun run build` clean; clippy clean.
- Controller layout B (2026-11-09, 0.167.0): the user chose B of three rendered mockups (`reports/controller-mockup-*.jpg`): numbered buttons, a still-in/out status line, faded buttons and a dark reveal card for out players, the right field as a small key with a wrong pick struck through, no "Stand" line. Live screenshots replace phone-1 to phone-4 in `evidence/`, plus `phone-1b-alive-picked` and `phone-2b-reveal-right`.
- Live: `tools/smoke.mjs --game dreifelder` PASS on the host (leaver, all-wrong round, cap) before and after the review fixes; a 5-player run with a browser phone ranked same-round drop-outs by ghost guesses. Screenshots in `evidence/` (TV 1080p, phone 390 px, plus the Kopfnuss vote now that three keys fill the grid).
- Code review (code-reviewer agent) found, all fixed test-first:
  - High: with three fields the right answer sat in field 1 half the time (`Round::shuffled` dropped the first wrong answer after shuffling). Now reshuffles after dropping; test checks the spread over 3000 draws. Four-option path untouched.
  - Medium: `dreifelder.css` used size tokens that exist only in another session's uncommitted `styles.css` work; now plain sizes.
  - Medium: a leaver could survive all-wrong rounds and win at the cap; decision 5 tightened (user's choice).
  - Low: factory asked for 12 general questions when 9 are needed (history reset too early); mid-game joiner and empty question list now tested.
- Accepted as is: ghosts often get little time, since a round ends once every connected alive player answered (plan: ghosts have no say in when a round ends).

Open:
- TV box and tablet run, jank vs Kopfnuss rounds: left to the release session (user, 2026-11-09).
- Release of 47ef3b8c (0.166.0): no release session is running; `mediagram-spieltisch-16` is not one any more (2026-11-09). The user picks who releases it.
