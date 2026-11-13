# Flüchtig Made Clear Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A first-time table knows who runs, who hunts and what each line means before round 1; every hunter moves one figure; the plan reads as a real city with bridges and buses on streets.

**Architecture:** Rust host (`crates/mediagram-game/src/fluechtig*.rs`) gains a rules ladder by figure count, one-figure dealing, player names in texts, and a Grand-Allee-style intro stage carried to TV and phones in new protocol fields. The React client draws the cards, legend, role card, role bar and role themes from scene data; bridge decks and bus street routes are derived on the client from the board. The city JSON is edited by a committed tool.

**Tech Stack:** Rust (serde, ts-rs exports TS types to `client/src/protocol/` on `cargo test`), React 18 + TypeScript built and tested with bun, Python 3 tools.

**Spec:** [design.md](design.md) (approved 2026-11-13). Renders: `reports/mockups/`, `reports/bridges/`. Mockup code to port from: `mockup/mockup.tsx`, `mockup/mockup.css`, `bridges/pick_bridges.py`.

All paths below are relative to `crates/mediagram-game/` in the worktree `mediagram-fluechtig`, branch `feat/fluechtig-onboarding`, unless they start with `plans/` or `docs/`.

## Global Constraints

- No commits unless the user says so; each task ends with its tests green, ready to commit.
- German UI text; own names (never London or Ravensburger names).
- Only the fugitive's own phone carries their field while the chase runs (unchanged invariant).
- Numbers shown on TV and phones come from the scene/phone data, never written into texts.
- The skip and the first move: a tap's follow-up question opens `chain_ms` later.
- Lobby stays 2–6 players (`catalogue.rs` unchanged).
- Files under ~200 lines; doc comments on public items; comments say why, never cite this plan.
- Bash commands must not contain the words `node_modules` or `dist` (a hook blocks them).
- `cargo test -p mediagram-game` regenerates `client/src/protocol/*.ts`; keep the regenerated files with the Rust change.
- Version: Spieltisch line, next minor above `main`'s at build time, in all four manifests (Task 12).

## Review Focus

1. Two players: the lone hunter's figures are told apart ("Figur 1/2") in prompts, news and badges, and the fugitive is never offered a double move (0 left). Tests: Task 1, Task 3.
2. A tap while the cards run (eager fugitive, stale screen) moves nothing; the leader's double tap on skip cannot answer the first question. Test: Task 4.
3. The leader leaves during the cards: the next connected player may skip; the cards still close on their own. Test: Task 4.
4. An absent fugitive during the cards: the house waits until the cards end, then plays after the grace. Test: Task 4.
5. Names ending in s, x or z get a correct genitive on the cards ("Max’ Feld"). Test: Task 6.

## Phases

| # | Phase | Tasks | Status |
|---|-------|-------|--------|
| 1 | [Rules ladder, one figure each, names](phase-01-rules-and-table.md) | 1–3 | done |
| 2 | [Rules-cards stage on the host](phase-02-rules-cards-stage.md) | 4–5 | done |
| 3 | [TV cards, legend, phone role screens, themes](phase-03-tv-and-phone-screens.md) | 6–8 | done |
| 4 | [Bridges, buses on streets, balance](phase-04-bridges-and-streets.md) | 9–11 | done |
| 5 | [Version, checks, screenshots, review](phase-05-release.md) | 12 | done |

Order: 1 → 2 → 3; 4 can run after 1 (it touches the city and the ground drawing only); 5 last.

## Results (2026-11-13)

Built inline (Native), nothing committed (user's rule). Rulings and deferred minors: the build ledger (`.superpowers/sdd/plan/progress.md`, git-ignored) and the hand-over message.

- **Version:** Spieltisch 0.181.0 in all four manifests (`main` had moved to 0.180.0 by the merge). Changelog entry in `docs/project-changelog.md`.
- **Checks:** `cargo test -p mediagram-game` 444 pass; clippy clean; client typecheck, `bun test` 125 pass, build ok.
- **Balance** (`balance_sim.py 1500`, final city): 5 / 4 / 3 / 2 figures → fugitive wins 43.0 / 45.6 / 48.7 / 49.5 %.
- **City:** `add_bridges.py` reproduces the approved city exactly and is idempotent; taxi 342, bus 89 on 63 stops, U-Bahn 20 on 14, ferry 3 on 4.
- **Live run** (dev host, headless Chromium via gstack browse, TV 960×540 @2, phones 390×800 @2): `reports/live-run/` — the three cards at four players, play with "Ben ist dran" and the legend, bridges and bus stripes, two players with "Dana zieht Figur 2" and badges 1/2, role cards (night / police blue, skip only on the leader's), final screen back in house green. Skip from the leader's phone ends the cards; a restart shows them again; the house finished a chase with the phones away.
- **Review** (fresh Opus reviewer): "with fixes", no critical. Fixed: card 1 overflowed at six players with long names (now a wider card 1 and smaller figures when more than three hunt; `tv-7a` before, `tv-7b` after, live layout check red → green). Seven minors deferred.
- **Before committing:** merge `main` — `protocol.rs` (a new `PhoneScreen` variant next to the boxed Flüchtig phone) and the changelog will conflict; re-read `main`'s version. Leave `mockup/bundle` and `mockup/dist` out of the commit.
- **Not done:** the TV box itself (only Chromium at its size); card 1 at four players with a 16-letter runner name may reach into the dimmed log band.
