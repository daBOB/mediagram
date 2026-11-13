# Flüchtig, made clear: roles, rules, one figure each, bridges and streets

Design for the mediagram repo, branch `feat/fluechtig-onboarding` (from `main` at b7fb7193,
Spieltisch 0.177.1). Renders: `reports/mockups/` (TV cards, legend, phones in role colours) and
`reports/bridges/` (the city before and after). Throwaway mockup code: `mockup/` (screens drawn
with the real components plus DOM patches) and `bridges/pick_bridges.py` (the city edit).

## Why

The user played Flüchtig and found it "soo confusing": a player cannot tell whether they are the
fugitive or a hunter, nothing explains how the game works or what the lines are, and one hunter's
avatar stood on the plan up to five times ("why has the agent 5 figures on the board"). On the map,
streets and bus lines ran straight over the open river and buses cut through blocks ("U need to
think in streets, each taxi (white) stop is a crossing").

Success: a first-time table understands who runs, who hunts, how to win and what each line means
before round 1, without anyone explaining; every player sees their role on their own phone all
game; the plan reads as a real city; the chase stays near 50/50 at every table size.

## Decided by the user (2026-11-12)

1. **One figure per player.** Every hunter moves exactly one figure. Exception: at two players the
   one hunter moves **two** figures. (Replaces "five pieces, dealt round-robin".)
2. **Rules explained on the TV before round 1**, as cards; roles on the phones.
3. **Phone colours by role:** the fugitive's phone is **dark night**, the hunters' phones **police
   blue** (render: `reports/mockups/sheet-phones-role-colours.jpg`).
4. **The river:** remove every taxi and bus link across open water; **a few drawn bridges** carry
   them instead.
5. **Think in streets:** every field is an intersection; a bus drives along streets between its
   stops, never straight through blocks; no "green border" beside a bridge's street.
6. Lobby stays **2–6 players**.

## Decided by Claude (correctable)

- **Balance ladder** (user did not object; measured with `tools/fluechtig-city/balance_sim.py`,
  1,500 bot games each, on the bridged city). Rounds stay 24, hunter tickets stay 10 taxi / 8 bus /
  4 U-Bahn per figure, fugitive keeps 4/3/3 plain tickets and the inflow rule:

  | Figures | Players | Sightings | Shadow rides | Double moves | Fugitive wins |
  |---|---|---|---|---|---|
  | 5 | 6 | 3, 8, 13, 18, 24 (as today) | 5 | 2 | 43.0 % |
  | 4 | 5 | every 3rd round (3, 6, … 24) | 5 | 2 | 45.6 % |
  | 3 | 4 | every 3rd round | 2 | 1 | 48.7 % |
  | 2 | 3, and 2 (one hunter, two figures) | every 3rd round | 1 | 0 | 49.5 % |

  For comparison, the reference board the classic numbers come from scored 42.1 % with the same
  bots. The bots are simple; real tables will differ. Measured again in the build on the final
  city with `balance_sim.py 1500 …:game5 …:game4 …:game3 …:game2` (2026-11-13): the same
  43.0 / 45.6 / 48.7 / 49.5 %. Numbers live in one place in Rust (a `Rules`
  constructor by figure count), and the TV/phone texts read them from the scene, never hard-coded.
- **Three rules cards** (render `reports/mockups/sheet-tv-rules-cards-and-legend.jpg`): 1 "Wer ist
  wer?" (fugitive's figure vs the hunters' figures, how each side wins), 2 "Die Linien" (taxi, bus,
  U-Bahn, ferry drawn as on the plan, with their stop rings), 3 "Unsichtbar – fast immer" (travel
  log lit up, sighting rounds, the orange hideout circles, shadow rides and double moves — the
  counts from the ladder; omit double moves when 0). ~10 s each, the room's leader can skip from
  the phone, like Grand Allee's intro.
- **Phone during the cards: a role card** — "Du bist flüchtig" (hideout field, "nur du siehst es",
  rounds to survive, sighting rounds) or "Du bist Jäger" (your figure(s) and fields, who hunts with
  you); the leader's has "Regeln überspringen". **During play: a role bar** on top of every
  Flüchtig phone screen ("Du bist flüchtig · Nur du siehst dein Versteck" / "Du bist Jäger").
- **Naming:** with one figure each, texts name the player, not a piece number: TV banner "Ben ist
  dran", news "Cem fährt mit dem Bus nach 166.", phone header "Deine Figur · Feld 106" with the
  player's avatar head in the disc. Piece numbers appear only when a player has two figures
  ("Ben zieht Figur 2", badges on the plan).
- **Line legend** always on the TV, bottom right: Taxi, Bus, U-Bahn, Fähre, "Dana?" hideout mark.
- **Bridges** (render `reports/bridges/after-bridges-buses-on-streets.jpg`): six, hand-picked from
  short, near-square crossings spread along the river: 55–73, 58–76 (taxi + bus), 117–135, 83–101,
  68–105 (taxi + bus; plus bus 105–106 so it reaches a stop), 89–108. Drawn as a stone deck (dark
  edge, light deck) under the street, over water and ferry lane.
- **Streets for buses:** a bus link is drawn along the shortest taxi route between its stops, as a
  teal stripe down the street's middle. Where that route was over 1.8× the straight distance, a
  street was laid on the bus link: 114–115, 22–23, 49–68, 135–136, 149–168, 26–43. A bus stops at
  every green ring it passes: a ride whose street route runs through another stop is split stop to
  stop (only 104–106 → 104–105, 105–106).
  Result: taxi 346 → 342, bus 99 → 89, U-Bahn 20 and ferry 3 unchanged; every network connected.
  Reproduce: `BRIDGES="55-73 58-76 117-135 83-101 68-105 89-108" BUS="58-76 68-105 105-106"
  python3 bridges/pick_bridges.py <city> <out>`.
- **U-Bahn stays straight and dashed** (underground) and keeps its six river crossings. **Ferry
  unchanged.**

## Approaches considered

- *Intro cards:* host-driven stage (as Grand Allee: `Stage::Intro`, timer, leader skip, phones
  know the stage) **— chosen**, vs a TV-only timer (phones could not show the role card in step or
  offer the skip).
- *Card content:* client-drawn from scene data **— chosen** (cards carry figures and line swatches;
  numbers come from the scene), vs server text lines like Grand Allee (no room for graphics).
- *Bridges and bus routes:* derived on the client from the board (a deck wherever a taxi/bus link
  crosses the river; bus routes by shortest street path, memoised per city) **— chosen**, no
  protocol change; vs shipping bridge and route geometry in the protocol (more data, same picture).
- *City edit:* edit the existing city file with a script (`bridges/pick_bridges.py` → moved into
  `tools/fluechtig-city/`) **— chosen**, keeps every field where players know it; vs regenerating
  with fewer bridges (moves the whole city, re-balancing from scratch).
- *Balance at small tables:* the ladder on the big city **— chosen**; vs the 48-field city for
  small tables (3 players still 88 %) vs leaving it lopsided (92 % fugitive at 3 players).

## Goals / non-goals / anti-goals

- Goals: the six user decisions above; a table needs no spoken explanation.
- Non-goals: new rules beyond the ladder; sound; a tutorial mode; changing the small city;
  U-Bahn routing; the ceremony.
- Anti-goals (failure even if it works): a phone or the TV that shows the fugitive's field early;
  a role card that a hunter can confuse with the fugitive's; texts with hard-coded numbers that
  drift from the rules; a plan where a line runs where no street or bridge is.

## Constraints

- Code in `crates/mediagram-game` (Rust host + `client/` React/TS, built with bun); files under 200
  lines where the codebase holds to it; doc comments on public items; German UI text.
- Only the fugitive's own phone ever carries their field (unchanged invariant).
- The intro's skip and the first move must not be answerable by a double tap (`chain_ms`, as
  Grand Allee's skip).
- Versioning: Spieltisch line, minor bump, every manifest of the line (repo `CLAUDE.md`).
- Visual changes are verified with retained screenshots of each screen touched (TV 960×540 @2x,
  phones 390×800), as the earlier Flüchtig phases did.
- Tests that pin today's numbers change with intent: link counts (346/99/20/3 → 342/89/20/3),
  five pieces, round-robin dealing, reveal rounds.

## Builder check (2026-11-12)

A fresh reviewer read this as the builder: "nothing important left". Its one low finding (the
104–106 bus drawn through stop 105) is fixed above by splitting rides at the stops they pass. It
confirmed: the intro follows Grand Allee's pattern; the Android TV and controller apps show the
web client in a WebView, so no native work; the small city runs in tests only.

## Left to the builder

- How news names players (structured news events vs formatting in the view layer).
- Exact intro timing per card, the role-bar wording beyond the mockup, card layout details.
- Where the bridge deck is clipped to the banks and its exact colours.
- Whether the bridge picker becomes part of `generate_city.py` or stays a separate tool, as long
  as the city file is reproducible from a committed script.
- Test strategy details, within the project's rules (logic tests for the ladder, dealing, intro
  flow and role views; screenshots for TV and phones).
