# Card games and Bauherr (the hotel game)

User, 2026-11-07 02:07: "lets start to build the card games and our hotel clone".
Builds the first slice of `../261106-1808-card-games/plan.md` and item 3 of
`../261106-1830-board-games/plan.md` (Bauherr, the Hotel slot).

## Decisions (user, 2026-11-07 02:10, all four recommended options)

- **Card games this run: Mau-Mau, Schwimmen, Durak.** No computer players needed;
  family-safe. Mau-Mau first, it proves the card table.
- **Mau-Mau: classic + Mau button.** 32 cards up to 5 players, 52 above. 7 = next
  draws 2 (stackable), 8 = next skips, Jack on anything and wishes a suit. Phone
  shows a "Mau!" button for the second-to-last card; forgotten → one penalty card.
- **Bauherr ends on a round limit, richest wins** (cash + land + buildings),
  earlier if everyone else is broke. Target 45–60 min.
- **Bauherr look: look test first.** 2–3 live town mockups on the box, user picks,
  then the TV renderer gets that look. Rules engine is built meanwhile.

Calls of mine (unconfirmed): name *Bauherr* and currency *Taler*; own hotel names
and price table; 2–4 players with a lobby gate, no bots (Hotel itself is a 2–4
game, so the board plan's "AI or 4-player minimum" was wrong); one `GameKind`
per game, not `GameKind::Cards` with a sub-mode.

Bauherr takes Hotel's *mechanics* (buy plots, build with a permit die, entrances,
guests pay per night) and nothing else: no name, hotel names, board, facades,
building pieces or rent table of the MB/Hasbro game.

## Phases

| # | Phase | Where | Status |
|---|---|---|---|
| 01 | [Card table + Mau-Mau](phase-01-card-table-and-mau-mau.md) | `../mediagram-cards` (feat/card-games) | done, merged 02:48 (156 tests; box unmeasured) |
| 02 | [Schwimmen and Durak](phase-02-schwimmen-and-durak.md) | `../mediagram-schwimmen`, `../mediagram-durak` | done, merged 10:14; review fixes merged 11:55 (hidden-information prompts, reveal values, bigger hand) |
| 03 | [Bauherr rules, host and phone](phase-03-bauherr-rules-host-phone.md) | `../mediagram-bauherr` (feat/bauherr) | done, merged 10:18; review fixes 10:36 |
| 04 | [Bauherr look test](phase-04-bauherr-look-test.md) | spike pages + box | done: round 1 rejected (toy-like); round 2 → user picked D Abendsonne, dice on the square (12:55) |
| 05 | [Bauherr TV board](phase-05-bauherr-tv-board.md) | after 03 + user's look pick | pending |
| 06 | [Merge, verify, version](phase-06-merge-verify-version.md) | feat/spieltisch | 0.152.0 done (13:40): cards + Bauherr rules, check.sh green, box sideload + card table measured; Bauherr board after 05 |

01, 03 and 04 run in parallel. 02 starts when 01 is merged into feat/spieltisch;
05 when 03 is merged and the user picked a look.

## Rules for every phase

- Branch from feat/spieltisch; commit on the phase branch; never push. **No
  version bumps and no changelog in phase branches** — 06 does both on merge
  (next number ≥ 0.152.0; main uses 0.148–0.150).
- Never stage `assets/` (the avatar session's files) or any `.claude/` dir.
- Rust edition 2024, `cargo clippy -p mediagram-game --all-targets -- -D warnings`
  clean, tests in sibling `*_tests.rs`, files under ~200 lines, German UI text,
  no plan/phase references in code, names or commits.
- Box rules (`plans/reports/benchmark-261105-1611-tv-box-3d-framework-spike-report.md`):
  three.js in the TV page at the 960×540 page scale, no HTML over the canvas during
  play, HUD pre-drawn + `initTexture`/`renderer.compile` at load, big flat surfaces
  unlit with baked light, no alpha-tested cut-outs, no shadow maps.
- Quality first on the box; ~32 fps is the floor, not 60 (user, 2026-11-07).
- Matte, never glossy. Heimlauf's look, the ceremony and its sound are approved —
  reuse them, do not change them.

## Results

- 0.152.0 on feat/spieltisch (4b44fec7), unpushed: Mau-Mau, Schwimmen, Durak, Bauherr rules/host/phone with a
  plain 2D TV board. 277 game tests; `scripts/check.sh` green after splitting seven modules under 200 lines (6ed4f3a5).
- Box 192.168.0.37, release 0.152.0 sideloaded (install-dm, speed-profile), not published to the channel. Real
  game night, 8 scripted phones over the LAN at human pace (1.2–2.4 s per move), `dumpsys gfxinfo` from deal to
  ranking:
  - Mau-Mau, 8 players: 183 s, 10 962 frames = 59.9 fps, janky 7 (0.06 %), p50 10 ms, p99 25 ms, 0 missed vsync.
  - Schwimmen, 8 players (reveals incl.): 661 s, 39 740 frames = 60.1 fps, janky 11 (0.03 %), p50 24 ms,
    p99 31 ms, 0 missed vsync. Heavier per frame than Mau-Mau but no dropped frames.
- Box left on the game lobby. The box only lets its own WebView be the TV (`not_allowed` for a LAN TV socket).
  - Durak, 6 players: 466 s, 27 073 frames = 58.1 fps, janky 32 (0.12 %), p50 24 ms, p99 34 ms, 4 missed vsync.
- 0.153.0 (fb3df002): Bauherr's 3D town board merged (6bdc2295); box measurement next.
