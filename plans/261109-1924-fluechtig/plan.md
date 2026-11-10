# Flüchtig, the hidden-move chase

User, 2026-11-09: "lets create the scotland yard clone" → rules prototype
(`fluechtig-rules-prototype.html`, branch `prototype/fluechtig`) → "looks fine. go on".
Source description: `plans/261106-1830-board-games/plan.md` §6. Own city, own
names; nothing of Ravensburger's map, station names or ticket art.

## Decided (from the prototype, approved as it plays)

- Rules exactly as the prototype's rules module: 48-field city, taxi/bus/U-Bahn,
  18 rounds, reveals in rounds 3/8/13/18, hunters 10/6/3 tickets, fugitive
  4/3/2 + 3 shadow rides + 1 double move, spent hunter tickets go to the fugitive.
- The TV shows the possible hideouts; a double move on a reveal round shows only
  where it ends; the fugitive's ticket stock is public; hunters move in turn.
- 2–6 players. One fugitive drawn at random; 4 hunter pieces (5 at six players)
  dealt round-robin to the others, so two players means one person runs all four.
- Phone: first the `Sheet` screen, ticket then destination; replaced by its own
  screen, destination then ticket (see "The look"). A question opened by a tap
  opens `chain_ms` later (repo learning: a double tap must not answer it).
- An absent mover's move plays itself after the table's house grace.
- TV: a 2D SVG board built from the prototype's look (no three.js, no assets),
  the fugitive's real route drawn at the end, then the shared awards ceremony.
  Winners score 1.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Rules core](phase-01-rules-core.md) | done |
| 2 | [Game in the room](phase-02-game-in-the-room.md) | done |
| 3 | [TV page, lobbies, release](phase-03-tv-lobbies-release.md) | done |

## Results (0.173.0, 2026-11-09)

Built as 0.171.0; committed as 0.173.0 because `spieltisch-games` had meanwhile
used 0.171.0 and 0.172.0 for Grand Allee. Rebased onto it (0c854259, only the
version files and changelog conflicted): `cargo test -p mediagram-game` 373,
clippy clean, client typecheck, `bun test` 108, build.

- Moved onto `spieltisch-games` at 6087c5e5 (it had gained the game catalogue
  and the shared house grace while this was built): Flüchtig is a catalogue
  entry (the room's seat gate, both lobbies), the factory's explicit arm, and
  uses `GameCtx::house_at` instead of its own `auto_ms`.
- Code review (no high findings) led to two fixes: a double move offers only
  first steps that leave a second (a cut-short double told the room the field
  had no way on), and only the mover's sheet carries the moving `opens_at`.
- `cargo test -p mediagram-game`: 352 pass; clippy clean; client typecheck,
  `bun test` (103) and build pass. Re-checked live on the rebased build: the
  catalogue lobby lists Flüchtig, a game starts, the house plays for absent phones.
- Live run on `game-night-dev`, three players on three origins, TV at 960×540:
  lobby → start → real two-tap moves → round 2; then the house finished the
  game (phones closed): "Ana ist erwischt", route drawn, ceremony.
  Screenshots: `reports/live-run/`.
- Changed on the way: the phone's header lines were too long for the Sheet's
  narrow column, so ticket counts moved onto the ticket buttons
  ("Taxi · noch 4"). The shared ceremony named only the first of two winning
  hunters; `TvFinal` takes an optional `title`, used for "Die Jäger gewinnen!".
- Not done: the TV box itself (only headless Chromium at the box's size);
  balance (no simulation of win rates); sound cues.

## The look (2026-11-09)

The views were first built without mockups. `fluechtig-look-prototype.html` (branch
`prototype/fluechtig`, `?variant=A|B|C`, a real round-5 scene) showed three looks for TV and
phones, renders in `reports/look-prototype/`. User: "tv screen from C and
controller from B".

- TV (C): the plan edge to edge, felt panels over it (round, whose turn in the
  mover's colour, rounds to the next sighting, latest news + travel log +
  fugitive's tickets, hunters' fields). Added beyond the mockup: the news line.
- Phones (B): own screen `PhoneScreen::Fluechtig`; field first as number tiles,
  then the ticket only when more than one gets there (a hunter mostly taps once;
  the fugitive always chooses while shadow rides last). Beyond the mockup: past
  12 possible hideouts a phone gives their number, the TV rings them.
- Live run on `game-night-dev` (three phones, real taps for round 1, the house
  finished: "Ana ist erwischt", route, ceremony): `reports/live-run/` (replaces
  the old look's screenshots).
- Code review (one high): a fugitive with only one ticket to a field moved in one
  tap, so one tap or two told the table which line they rode (two-kind pairs
  18–26, 23–31; a fast shadow ride meant a U-Bahn link). Now only hunters move
  in one tap; regression test watched failing first. Lows fixed: the tap lock
  holds until the next question (slow downlink), click not pointer-down (scroll
  on a tile), a timer instead of a 60 fps clock, double-move reveal wording on
  TV and phone, the bottom-row sighting label above its field, long names in the
  TV pill. Left: "Danach" names a stuck hunter or the fugitive after round 18's
  last hunter (cosmetic).
- `cargo test -p mediagram-game` 360, clippy clean; client typecheck, `bun test`
  104, build. Re-checked live with real clicks.
- Both prototypes (rules and look) live only on the throwaway branch
  `prototype/fluechtig`, one commit each with the question and the answer; the
  renders stay here.
