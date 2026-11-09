# Deepen the game-night modules

Five deepening refactors in `crates/mediagram-game`, chosen from an architecture
review on 2026-11-09 and settled with the user in one decision round (all 14
recommendations accepted). Terms: `CONTEXT.md` § Spieleabend.

## Phases

| # | Phase | Wave | Status |
|---|---|---|---|
| 1 | [Game catalogue](phase-01-game-catalogue.md) | 1 (parallel) | pending |
| 2 | [Host states the facts](phase-02-host-states-facts.md) | 1 (parallel) | pending |
| 3 | [House grace in the room](phase-03-house-grace.md) | 1 (parallel) | pending |
| 4 | [Simulator plays the live game](phase-04-simulator-plays-live-game.md) | 2 | pending |
| 5 | [Typed move stages](phase-05-typed-move-stages.md) | 3 | pending |

Wave 1 phases touch mostly different files and run in parallel worktrees
(`/home/andre/Workspace/spieltisch/wt-*`, branches `refactor/*` from 33f98539).
Phase 4 needs phase 3 (`house_at` on `GameCtx`); phase 5 rewrites the `tick`
and `auto_index` that phase 4 reshapes.

## Rules for every phase

- One commit per phase on `spieltisch-games`, after `cargo test -p mediagram-game`,
  `bun run typecheck` and `bun test` (in `crates/mediagram-game/client`) pass and the
  diff is reviewed. Version bump in `Cargo.toml`, `Cargo.lock`, `web/package.json`,
  `android/app/build.gradle.kts`; changelog in `docs/project-changelog.md`.
- Implementing agents do not commit or bump versions; the lead applies, bumps, commits.
- Never push. Never commit other sessions' untracked files.
- Files under 200 lines (a deep module may span files). No plan references in code.
- No player-visible behaviour change unless the phase says so.

## Decisions (user, 2026-11-09)

1. Simulator drives `GrandAlleeGame` through the `Game` trait, jumping deadlines; no bots in rooms.
2. A playing bot raises the money on a Doppelt when its other properties at the bank's price cover the shortfall; the house keeps auctioning the hotel in question.
3. A 2–8 s default simulator run is fine; parallelise player counts only above 5 s.
4. Refactor only: new baseline tables, targets re-checked, `Rules` and think times unchanged.
5. Move: typed stages carry the step and the debt; no `Decide` seam.
6. The house's idle carries through a 6's extra roll; add a test.
7. One table-wide grace: `GameCtx::house_at(player, since) -> Option<ServerTime>`; `auto_ms` fields deleted; one shared name helper.
8. No behaviour change for absent bidders or the quiz/poster/Kopfnuss waiting rule.
9. Game catalogue: host table sent in the lobby state; exhaustive factory match.
10. Android TV shelf keeps its copy with a comment pointing at the catalogue.
11. Host facts: only the three small fixes.
12. TV/phone wording oddities go to a separate list (below).
13. Game terms live in a `CONTEXT.md` section.
14. Three waves of sub-agents, one reviewed commit per phase.

## Later (not in this plan)

- TV shows the mover's "du" prompt ("Ben: Dir fehlen 800 Taler. Was versteigerst du?").
- The mover's own phone narrates in the third person ("Ben kauft Lagune.").
- "Kein Gebot" shows twice on the TV at once (banner and panel).
- Absent bidders in a debt auction pass at once, without the grace.
