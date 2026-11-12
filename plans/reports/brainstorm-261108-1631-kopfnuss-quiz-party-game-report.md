# Brainstorm: Kopfnuss — quiz party game with phone sabotage

Date: 2026-11-08 · Status: design approved · Next: `/ck:plan`

## Problem

TV-box party quiz where players vote categories and sabotage each other's phones, ending in a speed race. Inspired by the genre (category vote, stacking sabotage, finale race); **not** a recreation of Knowledge Is Power — none of its names (Power Play, Pyramid of Knowledge, Grand Finale), door-run visual or its gunk-wipe / ice-tap effects. Known flaw of the genre to fix by design: too much picking, too little quizzing (~15 questions / 30 min).

## Decisions (user-confirmed)

| Topic | Decision |
|---|---|
| Pace | 3 questions per round, 7 rounds (config) + finale |
| Sabotage | phone-touch tricks, original set of 4 |
| Finale | climbing race "Gipfelsturm" |
| Scope | playable MVP, new game kind, 2D TV in existing quiz style |
| Vote | 3 offered categories, majority; tie → last-placed player's vote, else random |
| Trick economy | everyone 1 per round, pick 1 of 2 offered + target; max 3 per phone |
| Fun fact | `questions/fakten.json`, ~60 German facts, reviewed by user |
| Name | Kopfnuss |
| Kids mode | blocked for Kopfnuss (lobby `blocked` message) |

## Approaches for tricks

- **A (chosen):** `tricks: Vec<Trick>` field on existing `PhoneScreen::Choice` (empty for other games). Phone renders effects. Swap keeps original index; hold sends `at` = hold end, so speed score penalizes naturally. Devtools-cheatable — acceptable for family game, host still clamps `at`.
- B: host-enforced (per-player permutation, press/release actions) — jittery holds, mid-question pushes; rejected.
- C: new `PhoneScreen::Kopfnuss` — duplicates Choice rendering; rejected (DRY).

## Rules

**Round:**
1. Pick phase, 12 s (ends early when all done). Per-player phone sequence: vote category (1 of 3) → trick (1 of 2) → target (not self). TV shows the 3 categories + who is done.
2. Resolve: majority category; tie → last place's vote, else random. Tricks applied in pick order; 4th trick on a phone or duplicate trick **bounces** (TV shows it).
3. 3 questions from category via existing quiz engine; speed score 1000→500. Tricks last all 3 questions.

**Categories:** 12 from `allgemein.json` + "Eure Filme" (film questions from `quiz_mix`). Category with <3 unused questions tonight not offered.

**Tricks:**
- Wechselbad — answer buttons swap places every 1.5 s
- Schneckenpost — answer texts appear letter by letter over 4 s
- Spiegel — answer texts mirrored
- Kleber — hold 1 s to pick

**Finale Gipfelsturm:**
- 10 steps; head start by rank 1st→3, 2nd→2, 3rd→1, rest 0 (ties share rank)
- per-phone rapid-fire stream, question text on phone; everyone same questions, same order (~15–20 questions consumed)
- correct +1 step; wrong → 2 s lock
- no tricks; first to top wins; 90 s cap → highest step, tie → faster
- winner fact on TV + winner phone

**Length:** ~70 s/round → ~10–12 min total, ~25 questions per 10 min (4–5× genre density). All timings/counts in config.

## Touchpoints

New (game-owned): `src/kopfnuss.rs`, `kopfnuss_config.rs`, `kopfnuss_tricks.rs`, `kopfnuss_climb.rs`, `kopfnuss_views.rs`, `protocol_kopfnuss.rs` (+ `_tests.rs`), `questions/fakten.json`, `client/src/tv/TvKopfnuss.tsx`, `client/src/phone/choice-tricks.ts(x)`.

Shared (conflict risk with parallel Grand Allee session): `protocol.rs` (GameKind, TvScene, `Choice.tricks`), `factory.rs`, `TvApp.tsx`, `PhoneApp.tsx`, lobby game list, `room_settings`.

Reuse: ~~extract speed scoring + fair-tap clamp~~ superseded in plan — each round contains a `Quiz` instance (3 questions); `quiz.rs` untouched. See `plans/261108-1631-kopfnuss-quiz-party-game/plan.md`.

## Acceptance

- Kopfnuss in lobby, starts with ≥2 players
- unit tests: tie-break by last place, 3-trick cap + duplicate bounce, finale head start, 2 s lock, winner at top / at cap
- trick visible only on target phone, lasts exactly 3 questions
- `cargo test` + `bun test` green
- screenshots: TV pick phase, phone with each trick
- version bump minor in all three manifests

## Out of scope

3D staging, sound, kids mode, score tricks, new question content beyond facts.

## Risks

- Shared-file conflicts with Grand Allee session → keep shared edits small, merge often.
- Question bank: ~26/category; repeated votes for one category drain it → "not offered when <3 left" rule.
- Kleber + Schneckenpost stacked could make a phone near-unanswerable in 12 s → 3-cap; tune in playtest.

## Unresolved

- Fact texts need user review before ship.
