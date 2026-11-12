---
title: "Kopfnuss — quiz party game with phone tricks"
status: shipped
mode: tdd
created: 2026-11-08
source: ../reports/brainstorm-261108-1631-kopfnuss-quiz-party-game-report.md
---

# Kopfnuss — quiz party game with phone tricks

Approved design: [brainstorm report](../reports/brainstorm-261108-1631-kopfnuss-quiz-party-game-report.md).
Genre-inspired, not a recreation: no names, visuals or signature effects of
Knowledge Is Power.

Each round, players vote on a category, then each sends one trick at an opponent,
then everyone answers 3 questions. After 7 rounds comes the Gipfelsturm finale,
and a fun fact for the winner. Every phase starts with failing tests.

## Change from the brainstorm

The report planned to pull scoring and the fair-tap clamp out of `quiz.rs`.
Instead, **each round contains a `Quiz` instance** (`Quiz::new(cfg{rounds:3}, 3 questions)`).
It already runs intro, answer and reveal, scores into `ctx.players` and reports
`is_finished()`. Kopfnuss forwards actions and ticks to it, and adds `tricks` to its
`Choice` screen. The finale scores no speed, so it needs no clamp. The result:
`quiz.rs` stays untouched and no refactor phase is needed.

## Phases

| # | Phase | Status |
|---|---|---|
| 01 | [Protocol, lobby and question supply](phase-01-protocol-lobby-and-question-supply.md) | done |
| 02 | [Round engine: picks, tricks, 3 questions](phase-02-round-engine-picks-tricks-questions.md) | done |
| 03 | [Gipfelsturm finale and fun fact](phase-03-gipfelsturm-finale-and-fun-fact.md) | done |
| 04 | [Phone trick effects](phase-04-phone-trick-effects.md) | done |
| 05 | [TV scene](phase-05-tv-scene.md) | done |
| 06 | [Verification and release](phase-06-verification-and-release.md) | done except the TV-box run and the people test night |

Order: 01, then 02 and 03 (host only, can run in parallel), then 04 and 05 (client only, can run in parallel), then 06.

## Key dependencies

- `src/quiz.rs`, `quiz_config.rs`, `quiz_views.rs` (contained per round, read-only)
- `src/general.rs` (general pack by category), `film_questions.rs` (category "Eure Filme")
- `PhoneScreen::Choice` gets a `tricks` field (4 existing constructors get `tricks: vec![]`)

## Coordination: parallel Grand Allee session

`plans/261108-0455-grand-allee` (phases 04–06 are open) commits in the same
`mediagram-games` worktree (branch `spieltisch-games`) and also edits `protocol.rs`,
`protocol_settings.rs` (GameKind), `factory.rs`, `room_settings.rs`, `TvApp.tsx`,
`PhoneApp.tsx`, `PhoneLobby.tsx` and `ui/lobby.ts`. There is no logical dependency, only a textual one.
Kopfnuss is therefore built in **its own worktree** (`../mediagram-kopfnuss`, branch
`feat/kopfnuss`, branched from `spieltisch-games`). Keep shared-file edits to one-line
arms, rebase before merging, and pick the version number when committing.

## Done when

- [ ] Kopfnuss in the lobby, starts with ≥2 players, blocked in kids mode
- [ ] Rust tests: last-place tie-break, 3-trick cap and duplicate bounce, tricks last exactly 3 questions, finale head start, 2 s lock, winner at the top and at the 90 s cap
- [ ] bun tests: swap permutation over time, letter reveal, hold progress
- [ ] `cargo test -p mediagram-game`, `bun test`, `bun run typecheck` green
- [ ] Screenshots in `reports/evidence/`: TV pick phase, TV trick landing, phone per trick, TV climb, fact
- [ ] Version: next minor, in all 3 manifests; changelog entry

## Validation Log

### Session 1 — 2026-11-08

**Verification results:** tier Full (6 phases). Claims checked: 16. Verified: 14 · Failed: 2 · Unverified: 0.
- Failed: "min 2 players (as Quiz)": Quiz has no minimum (`room_settings.rs:77` returns `None`). Fixed: a new `KOPFNUSS_MIN = 2`.
- Failed: the lobby list is only in `ui/lobby.ts`. The game buttons are also at `PhoneLobby.tsx:80`. Fixed: added to phase 01 and to the shared-file list.
- Verified: the 4 `Choice` constructors (`quiz_views.rs:33`, `poster_views.rs:26`, `race_views.rs:70,82`), the `opens_at` key at `PhoneApp.tsx:149`, `rank_of`, `general.rs:35` `include_str`, ts-rs export via `.cargo/config.toml`, the 3 manifests, `useServerNow` on rAF, `Quiz::new(cfg, questions, now)`, the quiz `Final` phone view, `tools/smoke.mjs --game`, and that the TvApp `Quiz` component is local (needs exporting).

**Decisions (4 questions):**
1. Worktree: Kopfnuss gets its own worktree `../mediagram-kopfnuss` on `feat/kopfnuss`, because the Grand Allee session commits in the same `spieltisch-games` worktree. Changed: plan.md, phase 01 step 0, phase 06 step 4.
2. Min players: 2. Changed: phase 01.
3. Trick targets: connected players only; with nobody else connected, the trick step is skipped. Changed: phase 02 (requirement and test 6).
4. Apply both verification fixes: yes. Changed: phase 01.

### Whole-Plan Consistency Sweep
- Files reread: plan.md, phase-01 … phase-06
- Decision deltas checked: 5 (worktree, min players, targets, lobby files, the earlier switch from extraction to contained Quiz)
- Reconciled stale references: 3 ("as Quiz", "latest main", "separate branch")
- Unresolved contradictions: 0
- Note: the plan files are untracked in `mediagram-games/plans/`; the new worktree won't contain them, so cook must use the absolute plan path.

## Review (2026-11-08)

Built in worktree `/home/andre/Workspace/spieltisch/mediagram-kopfnuss`, branch `feat/kopfnuss`, version 0.160.0 (uncommitted).

**Checks:** `cargo test -p mediagram-game` passes 331 tests (32 of them Kopfnuss); clippy is clean; `bun run typecheck` is clean; `bun test` passes 68 (6 of them trick functions); `:ui-tv:compileDebugKotlin` compiles.

**Live runs** (host on :8091, 2 bots and the browser phone "Dora", gstack browse), with 25 screenshots in `reports/evidence/`:
- The lobby blocks a single player. The pick phase ends early once everyone is done, or after 12 s. The resolve screen shows the votes and each trick landing.
- All four tricks seen on a real page:
  - Wechselbad: the order changed after 1.7 s (Kastanie/Eiche/Linde/Buche, then Buche/Linde/Kastanie/Eiche).
  - Schneckenpost: half the letters in mid-question.
  - Spiegel: mirrored text.
  - Kleber: an early release (0.4 s) answers nothing; a full hold answers after 1024 ms, scored "Richtig!".
- Stacked tricks: badges on the phone, a trick strip next to each player's seat on the TV.
- Climb, fact, and a podium in climb order.
- With the bots answering in under a second, a full game took about 5 minutes. The 10–12 minutes with people is not yet measured.

**Fixed after the code review** (`reports/code-review-261108-kopfnuss-implementation-report.md`), each with a test seen failing first:
- H1: the climb screen has no clock again, as the spec had it, and the host accepts taps up to 150 ms early. A phone whose clock runs ahead, or a lost tap, no longer freezes the phone.
- M1: a tap opens the next pick step or climb question 400 ms later (`next_gap_ms`), so the second tap of a double tap is dropped.
- M2: Kopfnuss is on the Android TV games shelf, without art for now.
- M3: held answers suppress the long-press menu, text selection and touch callout.
- Low: the tie-break counts only players still connected as last place.
- Also from the live run: the Spiegel hint no longer breaks mid-word; the phone clock no longer wraps; a refused pointer capture no longer stops a Kleber hold.

**Deviations from the plan:**
- `quiz.rs` was touched: answer shuffling moved to `Round::shuffled` for the finale. Rng order is unchanged and the quiz tests pass.
- The quiz TV view moved to `TvQuiz.tsx` and `ChoiceScreen` to its own file, to avoid a circular import and keep files under 200 lines.
- `Final` carries the finishing order.
- The factory's history is now `Arc<Mutex<HashSet>>`.

**Open:**
- [ ] The user reviews `questions/fakten.json` (60 facts).
- [ ] A run on the TV box and a game with people, to time the 10–12 min and check whether Kleber + Schneckenpost is still answerable.
- [x] Podium points: the user chose a climb bonus. 100 points per step, and from the bottom up anyone not ahead of the player below is lifted past them (`Climb::award`, tests `the_summit_bonus_makes_the_points_follow_the_climb` and `the_climb_winner_ends_with_the_most_points`).
- [ ] Poster and backdrop art for the TV shelf.
- [ ] Low items left from the review: Wechselbad loses keyboard focus on a swap; a player who joins mid-climb sees "Keine Fragen mehr".
- [x] Shipped 2026-11-09 as 0.163.0 (versionCode 163000, channel message 50730): feat/kopfnuss merged with spieltisch-games@8690b8d6 (159bc40a), sound effects (0ca84c8d), main 411a7a53. spieltisch-games fast-forwarded to 0ca84c8d. Kopfnuss's 0.160.x numbers were renumbered into 0.163.0; next version 0.163.1 / 0.164.0 from main.
- Do not commit the untracked `crates/mediagram-game/.claude/` (the reviewer's notes) or `crates/mediagram-game/production/` (session logs).
