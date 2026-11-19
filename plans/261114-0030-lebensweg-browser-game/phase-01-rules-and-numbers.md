# Phase 1 — Rules and numbers

Status: done (2026-11-14) · Priority: first

## Context
- Research: `research/original-rules-and-numbers.md` (the original's rules and every number, with sources).
- Board data: `BOARD` / `MORE` in the look prototype (`prototype/lebensweg`, `plans/261112-0055-lebensweg/lebensweg-diorama-look-prototype.html:89-117`).
- Template: `crates/mediagram-game/src/grand_allee/` (rules.rs, money.rs, board.rs, play.rs, live*.rs).

## Requirements
- Same rules and numbers as the original edition the board follows; our own names (no Hasbro/MB names, no "LIFE").
- Every stretch and every field kind × 1.3 (`MORE`), already so in `BOARD`; money scaled so a game earns and spends
  like the original (checked in phase 2, not assumed).
- The host owns the board: it expands `BOARD` into fields (kind, stage, branch) with a fixed seed and sends them to
  the TV; the TV only lays them out. One source of truth, no second copy of the field kinds in TypeScript.
- Board as a graph (`Vec<Field { kind, stage, branch, next: Vec<usize> }>`), forks are fields with two exits.

## Architecture
`src/lebensweg/`: `board.rs` (BOARD data, expansion, graph), `rules.rs` (spin, move, pass/land effects, forks, STOPs,
cards, loans, retirement, score; pure functions on a `Table` state), `cards.rs` (careers, salaries, houses, life
tokens, event texts), `live*.rs` (the `Game` impl: turns, clocks, absent players, views), `protocol_lebensweg.rs`.

## Steps
- [x] Read the research; rule table below (each rule → number → source).
- [x] `board.rs` + tests: the original's 142 spaces in order (148 fields at 1×), 192 at 1.3×, every route reaches a home.
- [x] `rules.rs`/`turn.rs`/`effects.rs`/`perks.rs` + tests: spin, move, forks, STOPs, every space effect, loans, perks,
      retirement, estate tokens, score.
- [x] `cards.rs`, `spaces.rs`: careers, salaries, houses, stocks, 25 + 8 tokens, 142 space texts in our own words.
- [x] `cargo test -p mediagram-game lebensweg`: 29 green, incl. 20 seeded 4-player games that end cleanly; clippy clean.

## Rules as built

Sources: R = 2000 rulebook (Hasbro PDF), C = fan clone GamingPackXV4 (`research/…` §14–15). Rulebook wins on conflict.

| Rule | Number | Source |
|---|---|---|
| Players, spinner | 2–6, 1–10; between two numbers = spin again (5 % here, clone's wheel 16 %: tuning knob) | R, C |
| Start money | 10,000 × 1.3 = 13,000 | R |
| Studium | tuition 40,000 × 1.3 in loans of 20,000; opens degree careers; Berufswahl STOP: 3 careers, 3 salaries, keep 1 each | R |
| Ausbildung | 1 career (no degree) + 1 salary at random at Start; drives through Berufswahl | R |
| Payday | salary on every payday field passed or landed | R |
| Careers | 9 (Buchhaltung, Kunst, Profisport, IT-Beratung, Medizin, Bühne, Polizei, Vertrieb, Lehramt), perks as on the cards | C (card texts) |
| Perk amounts | Polizei 5,000 on a 10 (R; clone 10,000), Kunst 10,000 on a 1, IT 50,000 on a gap, Vertrieb 5,000 per purchase | R, C |
| Salaries | 20,000–100,000, taxes 5,000–45,000 | C |
| Houses | 9 deeds 40,000–200,000 × 1.3, insurance 10,000–50,000 × 1.3; 1 drawn at random at Hauskauf | R (random draw), C (deeds) |
| Car insurance / stock | 10,000 × 1.3 / 50,000 × 1.3; stock pays 10,000 per spin of its number | R |
| Loans | automatic in 20,000; 5,000 interest each, repaid at retirement | R |
| Hochzeit | partner + 1 token + spin again | R |
| Spaces | 142 in the clone's order; career fields pay the career's holder, taxes to Buchhaltung | C |
| Tokens | 25 (+7 at 1.3: 32, bots) worth 10,000–50,000; when the pile is empty a token is taken from another player | C (values), R (stealing) |
| Retirement | end fork: Villa am See (richest takes 4 × 1.3 = 5 set-aside tokens, shared on a tie) or Landhaus (+1 token, safe) | R |
| Score | cash + token values | R |

Clone defects not copied: owners paying their own career field (B1) and Buchhaltung its own taxes (B2), Vertrieb
earning on its own purchases (B3), retirement debt dropped (B4: here the debt stays in the cash), tile values dealt
by seat (B8: here every token keeps its value). Clone space 54 prints 1,500, read as 15,000. Clone space 81
("sell or buy a house" STOP) is in neither rulebook and left out.

Change against the look prototype: its field kinds were spread at random by kind count; the board now follows the
original's space order, so it has 192 fields instead of 195 (phase 4 re-lays and re-checks the board). Which spaces come twice is `stretch.rs` (phase 2: chosen so every kind stays at 1.3 ×).

Not built (no effect on the outcome): repaying a loan early (interest is flat, so it never pays off).

## Success
Unit tests cover every field kind and every STOP; a seeded game played by `rules.rs` alone ends with a winner.

## Risks
- The fan clone and the rulebooks disagree → follow the rulebook, note the difference.
- Money × 1.3 skews the forks (risky vs safe path) → phase 2 measures it.
