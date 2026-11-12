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

## The ferry (0.175.0, 2026-11-10)

User, looking at the look prototypes: "a fairy is missing on the map" (the ferry).
Built on `feat/fluechtig-ferry` from `main`; not committed.

- Four piers down the river, bank to bank where no bridge stands: 17 – 27 – 22 – 32
  (`FERRY` in `fluechtig_map.rs`, one line to change the route).
- Only a Schattenfahrt boards it: a ferry link is a link of the shadow ride's own
  kind. Hunters hold no shadow rides, so only the fugitive rides it; the travel
  log shows a shadow ride like any other. Not asked, taken from the genre.
- TV: the lane down the middle of the river in the shadow ride's colour, named
  "Fähre", a jetty and a dark ring at each pier. Phone: the pier tiles carry the
  same ring and the "Schatten" chip.
- The ferry test was watched failing twice: no ferry, then the ferry offered
  twice (as the link's ticket and as the shadow ride) before `steps_from` was fixed.
- `cargo test -p mediagram-game` 416, clippy clean; client typecheck, `bun test`
  113, build. Live run on `game-night-dev`, three phones, the fugitive on pier 27:
  `reports/ferry/` (TV at 960×540, the phone's two steps).
- Not done: balance (the ferry gives the fugitive three more ways over the river;
  no win-rate simulation exists), the TV box itself, the ride played to its end
  in the live run (stopped at the ticket question).

## The big city (proposal, 2026-11-10; nothing built yet)

User: "is our board equal to scotland yard board?", then "in equal i mean its the
same balanced? at least use the same number of field and lines".

- Reference totals, counted from a public data file of the classic board (totals
  only; its layout is not used and the file is not in the repo): 199 fields; taxi
  346 links; bus 99 links on 62 stations; U-Bahn 20 links on 14 stations; ferry 3
  links on 4 piers; mean distance 4.71, diameter 10 over all lines.
- Measured with the same two bots on each board (`reports/big-city/chase-balance-sim.py`):
  the fugitive wins 42 % on the reference with the classic rules, 23 % on our
  48-field city with our rules. Equal totals alone do not carry the balance: a
  first 199-field city with the same totals gave 26 %.
- `reports/big-city/big-city-generator.py` builds an own city from a seed with
  exactly the reference totals. What moved the win rate: a more compact street
  grid (18×11 plus a square, not 20×10) and many bridges (16, not 7). The chosen
  city (`big-city.json`): mean distance 4.78, diameter 10; fugitive 43.7 % against
  the reference's 42.1 % (pack hunters: 37.0 % against 35.9 %), 12,000 games each.
  All figures: `reports/big-city/big-city-figures.txt`.
- Still different: taxi-only distances (10.3 steps on average against 8.3), the
  TV plan being wider than the classic board.
- Render: `reports/big-city/big-city-199-fields-on-the-tv-1080p.jpg` (panels mocked).
  That one is the bare network; the user asked for "the graphical design".
- Three designs of the same city (`reports/big-city/design-*.jpg`, made by
  `big-city-design-render.py`): A Stadtplan (daylight map), B Nachtjagd (night),
  C Einsatzplan (blueprint). City blocks are the faces of the street net drawn
  inset; parks, river banks, landmark signs and district names are placeholders
  of mine. Stand-in font, mocked panels, not tried on the TV box.

Decided: "stadplan design" (A). The user then asked what the bottom-right
counter is for, and on hearing it: "its not needed use the avatar head on the
board". So there is no hunters' panel: each hunter piece stands on the plan as
its player's avatar head in the player's colour (the shared `Token` look), with
the piece's number as a small badge, because one player can move several
pieces. In the A render the fugitive's last sighting is their own head, faded,
with the round as its badge (my addition, not asked).

User, next: "maybe when i see the game in real time its easier to understand the
legends". So it was built to be watched, with my recommended answers to the two
open questions taken for now.

Built (0.175.0, on `feat/fluechtig-ferry`, not committed):

- The city is data: `crates/mediagram-game/cities/fluechtig-big-city.json`, made by
  `tools/fluechtig-city/generate_city.py` (seed 5 reproduces it), measured by
  `balance_sim.py` there. The copies of those tools under `reports/big-city/`
  are the earlier drafts.
- `Rules::big()` is the default: the big city, 24 rounds, sightings 3/8/13/18/24,
  five hunters with 10/8/4, fugitive 4/3/3 + 5 shadow rides + 2 double moves.
  `Rules::small()` is the 48-field chase; the rules' tests play it, the lobby
  does not offer it.
- The board on the wire carries the plan's size and the river; the TV works the
  Stadtplan out from it (`city-blocks.ts`, `stadtplan-art.ts`, `plan-lines.ts`,
  `StadtplanGround.tsx`): blocks are the faces of the street net, drawn inset.
- Hunters are avatar heads on the plan with the piece's number; the fugitive's
  last sighting is their faded head with the round; no hunters' panel.
- Measured again with the game's own start lists and double move (two rides in
  one round): fugitive 45.3 % (pack hunters 43.3 %) against the classic board
  with classic rules at 41.9 % (37.8 %), 12,000 bot games each.
- `cargo test -p mediagram-game` 418, clippy clean; client typecheck, `bun test`
  118, build. Live on `game-night-dev`: the house plays a whole chase on the TV
  page (`reports/big-city/live-tv-*.jpg`), fugitive's and hunter's phones by
  taps (`live-phone-*.jpg`).

User, watching it live: "can we try an isometric view ?". Two mock renders of the
same city, round 5, with blocks raised as rings of houses round courtyards and
the avatars as standing figures (`reports/big-city/raised-*.jpg`, made by
`big-city-raised-render.py`): A, the plan as it is with heights straight up
(fields keep their size); B, true isometric, the plan turned 45° (fields about a
third smaller on the TV, half the screen is table). Mocks only; the live game
still draws the flat Stadtplan.

User: "A". Built into the game: `stadtplan-art.ts` raises every dry block as a
ring of houses round a courtyard (walls that face south, roof with the yard cut
out), far to near with the trees; blocks the river runs through stay open. The
pieces are the avatars' standing figures beside their field (lower on the top
row, to the left at the right edge). District names and landmark signs went
with the flat look, as in the render the user chose. `bun test` 119; live:
`reports/big-city/live-tv-round-2-map-with-houses-and-figures.jpg`. The dev
host embeds the client at compile time, so a new client needs the host rebuilt.

User, seeing A live: "remove the houses". The plan is the flat Stadtplan again
(blocks, courtyards, district names, landmark signs, as chosen before the
isometric try); the standing figures stay. `bun test` 118; live:
`reports/big-city/live-tv-round-2-flat-stadtplan-with-figures.jpg`.

Still the user's to decide:

1. Keep the classic numbers (24 rounds, five hunters)? Built in for now.
2. Offer the 48-field city as a short game in the lobby, or drop it?

Not done: the TV box itself (only Chromium at 960×540); district names and
landmark signs are placeholders; every TV update still sends the whole city
(about 14 KB), which a LAN carries but a board id would not need.

Merging (2026-11-11): the games are moving into their own Android app on
`feat/spieltisch-app`, with a version line of their own (not on `main` yet). This
branch bumps the media-library manifests to 0.175.0, as `main`'s rule says. Agreed
with that session: whoever merges second moves this game-only bump onto the
Spieltisch line, at the next free number. No file overlap: this branch leaves
`host.rs`, `server.rs`, `catalogue.rs` and `lib.rs` alone.
