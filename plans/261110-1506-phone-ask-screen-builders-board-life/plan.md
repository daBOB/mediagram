# The phone's ask, screen builders and the board's life

Three deepening refactors in `crates/mediagram-game`, chosen from an architecture
review on 2026-11-10 and settled with the user in two decision rounds (all 18
recommendations accepted). Terms: `CONTEXT.md` § Spieleabend (**Ask** is new).

**Do not start before the user says the test night is over.** The test night
plays 0.172.0 (`0c854259`).

## Phases

| # | Phase | Touches | Version | Status |
|---|---|---|---|---|
| 1 | [Merge Flüchtig](phase-01-merge-fluechtig.md) | whole crate | 0.173.0 | pending |
| 2 | [Merge Dreifelder](phase-02-merge-dreifelder.md) | whole crate | 0.174.0 | pending |
| 3 | [Screen builders](phase-03-screen-builders.md) | host only | 0.174.1 | pending |
| 4 | [The ask](phase-04-the-ask.md) | host and phone | 0.175.0 | pending |
| 5 | [The board's life](phase-05-board-life.md) | TV only | 0.175.1 | pending |

In this order, one after the other. Both merges come first so each deepening is
done once, on all ten games. Phase 3 puts every choice screen behind one
builder before phase 4 reads its fields. Phase 5 shares no file with 3 and 4.

## Rules for every phase

- One commit per phase on `spieltisch-games` (phase 1 adds one docs commit),
  after `cargo test -p mediagram-game`, and `bun run typecheck`, `bun test` and
  `bun run build` in `crates/mediagram-game/client`, pass and the diff is reviewed.
- Version bump in `Cargo.toml`, `Cargo.lock`, `web/package.json`,
  `android/app/build.gradle.kts`; changelog in `docs/project-changelog.md`.
- The lead commits each phase once its checks and review pass (user, 2026-11-10).
  Implementing agents do not commit or bump versions.
- Never push. Never commit, move or delete other sessions' untracked files
  (`production/`, `client/production/`, `client/.claude/`, `.trail/README.md`,
  the Kopfnuss plan and its brainstorm report).
- Files under 200 lines. No plan references in code or comments.
- No player-visible change unless the phase says so.

## Decisions (user, 2026-11-10)

1. Flüchtig and Dreifelder are merged into `spieltisch-games` first.
2. The test night plays 0.172.0; everything here lands after it.
3. The term is **Ask**. Avoid question, prompt, decision.
4. The host states and checks the ask: a code on the phone's view, sent back with every game tap.
5. Asks are the choice, the sheet's options, the sealed bid, the card desk's buttons and Flüchtig's tiles.
6. Client modules are plain TypeScript with a thin React binding, tested with `bun test`; no DOM test library.
7. The board's life carries Race, Cards, Grand Allee and Flüchtig. Quiz, Poster, Kopfnuss and Dreifelder stay as they are.
8. Screen builders are functions; variants, wire and generated TypeScript stay. Buttons take their colour by cycling.
9. Shared phone screens first; a game may bring its own. ADR 0003; the stale comment in `protocol.rs` is fixed.
10. The same ask: same prompt, options, open time, bid limits; for cards, prompt and hand. Cash lines, hotels, the clock's end, tricks, the echoed answer and the table's cards do not count.
11. After a tap the button shows pressed and all buttons are dead until the host's next screen; then the phone follows that screen. The timed choice's own lock goes.
12. Strict: a game tap with a missing or wrong code is refused silently and the screen re-sent. Lobby and TV actions carry no code.
13. One guard for the double tap: a new ask stays dead 400 ms after the player's own last tap. The phone's 700 ms, 3 s and local lock go. The hosts' 400 ms opening gaps stay.
14. The ask module owns which ask is up, whether it is live, sending the tap, and when a screen starts fresh. The buzz stays its own module.
15. Six behaviours are the board life's interface and its tests (phase 5).
16. Two builders only: a choice and the final place.
17. This order, this plan, the rules above.
18. **Ask** is in `CONTEXT.md` (written 2026-11-10, uncommitted; goes in with phase 1's docs commit).

## Later (not in this plan)

- The review's other candidates: standings stated once, one host rule for taking
  an answer, board sound, the card table, starting a game and tonight's history,
  the three.js stage shell. `TvFinal`'s `jingle` and `title` flags belong to the first.
- Removing the hosts' opening gaps (`next_gap_ms`, Grand Allee's `later`): phase 4
  makes them redundant for the double tap, but tests and clocks depend on them.
