# Card games at the Spieltisch

User, 2026-11-06: "add a 52 cards game with these rules" and pasted a table of ten
card games (Mau-Mau, Euchre, Piquet, Herz, Schwimmen, Durak, Kings, Schafkopf,
Tiến Lên, Klabberjass) with player counts and one-line descriptions.

This is a catalogue of ten games, not one feature. The plan below builds the
shared card table once and lands one game on it (Mau-Mau); the other nine are
each a follow-on phase, ordered by what they need.

## What the pasted table gets wrong, and the call I propose

**"52 cards" is true of three of the ten.** Real decks: Hearts, Kings, Tiến Lên
are 52. Mau-Mau and Schwimmen are 32 in Germany. Durak is 36. Piquet is 32.
Schafkopf is 32. Klabberjass is 36. Euchre is 24/28/32.

Call: one French-suited pack of 52 in `cards.rs`, and every game takes a
**subset** (drop ranks below N). A 32-card Schafkopf deck is not a second deck
type, it is `Deck::from_rank(Seven)`. Schafkopf and Klabberjass are played here
in **French suits**, not Bavarian or Swiss — the TV box draws one card face set,
and French-suited Schafkopf is already common in Franconia. Recorded as a
deliberate simplification, not a rules error.

**Four of the ten need exactly four people** (Euchre, Hearts, Schafkopf) or
exactly two (Piquet). A family evening has two or three people at the table, so
those games need **computer players** or they are unplayable. Trick-taking AI is
its own project and is the single biggest cost in this list. Hearts' AI is
easy, Euchre's is medium, Schafkopf's is genuinely hard (partnerships, the solo
and Doppel contracts, a trump system of its own).

**Kings is a drinking game.** Every other game on this list is for a living-room
TV with children in the room, and the lobby already carries a *Nur für Kinder*
toggle. Kings cannot sit beside it unchanged. Needs a decision (see below).

**The room seats eight** (`host.rs:42 max_players: 8`, eight colours in
`room_seats.rs:8`). Schwimmen wants 2–9 and Kings 3–12; both get capped at 8 and
the lobby says so, the way Heimlauf already blocks 5+ players.

## What groups into shared engines

| Engine | Games | Shape |
|---|---|---|
| Shedding (match rank or suit, empty your hand) | Mau-Mau, Tiến Lên | beat what is on the table |
| Attack/defend shedding | Durak | one attacker, one defender, others pass |
| Trick-taking | Hearts, Euchre, Piquet, Schafkopf, Klabberjass | lead, follow, trump, trick to the highest |
| Hand value | Schwimmen | three cards, closest to 31 |
| Social / rule cards | Kings | each rank fires a table rule |

Three of these five need no AI at any player count: shedding, Durak,
Schwimmen, Kings. **Hearts is the only trick game playable without a bot** if
four people are present.

## Origin of each name

All ten are **traditional public-domain games**, so unlike the board games they
keep their real names (spec line 46: "The traditional Ludo race game is fine
with its usual rules"). Nothing here needs renaming — the origin column is
where to look the rules up.

| Pasted name | Origin | Public since | German UI label |
|---|---|---|---|
| Mau-Mau | German shedding game, ~1930s; Uno (Mattel, 1971) is its commercial descendant, not its origin | yes | Mau-Mau |
| Euchre | American, ~1810s, from the Alsatian Jucker | yes | Euchre |
| Piquet | French, first documented 1535 | yes | Pikett |
| Herz (Hearts) | American, ~1850s, from the Reversis family | yes | Herz |
| Schwimmen (31) | German, also Schnauz/Wutz/Bullen; *31* is its international name | yes | Schwimmen |
| Durak | Russian, "the fool"; codified in the Soviet era | yes | Durak |
| Kings (King's Cup) | folk drinking game, card meanings vary by region; branded party copies exist | yes | own name needed (see question 3) |
| Schafkopf | Bavarian, attested 18th c.; the Bavarian code is folk, not owned | yes | Schafkopf |
| Tiến Lên | Vietnamese, "Thirteen", from the Zheng Shangyou family | yes | Tiến Lên |
| Klabberjass | Dutch Klaverjas / Swiss-French Klaberjass, 19th c., Jass family | yes | Klabberjass |

Everything inside the game is German, so three of the pasted names change on
screen: Piquet → **Pikett**, Kings → its own name, and the rest stay as written.
No card game copies a commercial product, so the board games' origin list is the
only one carrying a "things to avoid" obligation.

## My proposed order

1. **Card table + Mau-Mau** (this project). Mau-Mau is the smallest rule set
   that exercises the whole seam: a deck, a hand only its owner sees, cards on
   the TV table, a turn, a pass, a winner. If Mau-Mau works, the table works.
2. **Schwimmen, Durak** — reuse the table, no AI, both fast and family-safe.
3. **Tiến Lên** — shedding with combos; the beat-comparison is the new part.
4. **Hearts** — first trick engine, still no AI at four players; add a simple
   bot after so three people can play.
5. **Kings** — after the drinking question is settled.
6. **Euchre, Klabberjass, Piquet, Schafkopf** — each needs its own trump system
   and its own AI. Schafkopf last.

## Architecture: where a card game plugs in

The seams Heimlauf opened are exactly the ones a card game needs.

**Host (Rust, authoritative).** Per `race.rs` / `race_rules.rs`:

- `cards.rs` — `Suit`, `Rank`, `Card`, `Deck::from_rank(lowest)`, seeded shuffle.
  Pure, no game knowledge. Tests in `cards_tests.rs`.
- `maumau_rules.rs` — legal plays, jack wish, seven-penalty, pass. Pure
  functions over a table state. Tests in `maumau_rules_tests.rs`.
- `maumau.rs` — turn flow, timing, absent players play themselves (the
  `absent_since` pattern in `race.rs:139`), `impl Game`.
- `maumau_views.rs` — `phone_view` / `tv_view`, pure, recomputed every event.
- `protocol_cards.rs` — wire types (`CardView`, `CardsScene`, `TableState`).
- `GameKind::Cards` in `protocol_settings.rs:11`, a branch in `factory.rs:26`,
  and a seat-count gate in `room_settings.rs::blocked()` beside `RACE_MIN`.

**Hidden information is already solved by the trait shape.** `Game::phone_screen`
takes a `PlayerId`, so each phone gets only its own hand; `tv_scene` never
carries a hand. `/tv` and images stay loopback-only, so nobody on the Wi-Fi can
read a hand. Cards are drawn, not fetched, so the "phones never receive an
image" rule is untouched — no `/img/` route is involved.

**Phone.** One new screen, because `PhoneScreen::Choice` cannot hold a hand:
`Cards { hand: Vec<CardView>, table: TableState, turn: PlayerId, can_play: Vec<u8> }`.
Tapping a card selects it, a button plays it. Everything else (waiting,
outcome, final ranking) reuses the existing `Wait` / `Outcome` / `Final`
screens, including Heimlauf's untimed choice for "no clock on a card turn".

**TV.** `TvScene::Cards { scene: CardsScene }` and a `client/src/tv/cards/`
renderer in three.js, deliberately reusing what Heimlauf proved on the box:

- the same table surface (`client/public/race/table-lacquered-cherry.jpg`), so
  a card game looks like the same table;
- `race-print.ts`'s pre-drawn canvas textures for the 52 card faces and the
  HUD text, drawn once at load — no HTML over the canvas, no runtime text;
- `renderer.compile` at load, which is what fixed Heimlauf's first-tap stutter;
- the approved 960×540 page scale, so it fits the box like every other scene.

A card back, a face-up card, a fan of played cards and a discard pile are four
arrangements of the same textured plane. That is far less geometry than the
race board.

**Sound.** Deferred. Heimlauf's sound system (`race-sound.ts`, cues scheduled
on the audio clock from host times) is reusable for a card snap and a shuffle,
but not in the first phase.

## Phases

- [x] 0 **Precondition:** commit the Heimlauf sound work. The tree held
      47 uncommitted changes at version 0.145.1 with 0.145.0/0.145.1
      changelog entries written and `plans/261106-1740-heimlauf-sound/plan.md`
      still all-unchecked. A ten-game project must not start on top of that.
      **DONE** — committed as `fd7009ce` "feat(game): Heimlauf, sound, avatars,
      TV settings and awards ceremony"; the tree is now at 0.151.0 and clean.
- [x] 0a **Also shipped since this plan was written:** the avatar catalogue
      (`crates/mediagram-game/src/avatars.rs`, 22 avatars, `Action::PickAvatar`,
      `PhoneScreen::Lobby { avatars }`, `/avatars/<id>-head.webp`, and a 3D
      ceremony stage in `client/src/tv/ceremony/`). Nothing here needs to build
      avatars; every game inherits them.
- [ ] 1 `cards.rs` + `cards_tests.rs`: suits, ranks, deck subsets, seeded shuffle
- [ ] 2 Protocol: `protocol_cards.rs`, `PhoneScreen::Cards`, `TvScene::Cards`,
      `GameKind::Cards`; regenerate and commit the ts-rs types (`check.sh` fails
      on an uncommitted protocol diff)
- [ ] 3 TV card table: `client/src/tv/cards/`, three.js, card textures and HUD
      pre-drawn at load
- [ ] 4 Phone hand: select, play, pass, on one new screen
- [ ] 5 Mau-Mau: rules, turn flow, absent players, views, factory, lobby option,
      seat gate
- [ ] 6 Verify: `scripts/check.sh` (clippy, cargo test, protocol committed,
      typecheck, build); three scripted phones against the dev host; the TV page
      on the box at ~60 fps; a game played to the end
- [ ] 7 Version, changelog, `docs/system-architecture.md` "Card games"

## Global constraints (carried from the game-night project)

- Everything inside the game is German; the TV app's entry label stays English.
- No names, boards or artwork of existing products. Mau-Mau, Herz, Schwimmen,
  Durak, Schafkopf are traditional public-domain games and keep their names;
  *Kings* is also called *King's Cup* and is a branded party product in places —
  it gets its own German name if it is built.
- The kids rule is never re-implemented in Rust; Kotlin decides via
  `forKidsProfile`. Card games are not title-based, so *Nur für Kinder* has no
  effect on the deck — except for Kings, which is the open question below.
- Rust edition 2024, `cargo clippy --all-targets --all-features -- -D warnings`
  clean, tests in sibling `*_tests.rs`, files under ~200 lines.
- No plan or phase references in code, comments, test names or file names.
- **Props the room must supply.** Cards are on screen, so nothing physical is
  needed — except Kings, whose card meanings are drinking rules and which therefore
  wants cups in the room. The games overview lists props per game (see
  `../261106-1830-board-games/plan.md`, "Props each game asks the room for"); if
  Kings is built, its prop line is written before it ships, and it needs the age
  gate below either way.
- Box rules from `plans/reports/benchmark-261105-1611-tv-box-3d-framework-spike-report.md`:
  no HTML over the canvas during play, HUD pre-drawn + `initTexture` at load,
  big flat surfaces unlit with baked light, no alpha-tested cut-outs.

## Open questions for the user

1. **Which game first?** I recommend Mau-Mau. It is the one everyone at a German
   table already knows, and it is the cheapest way to prove the card table.
2. **Exactly-four games (Euchre, Hearts, Schafkopf): computer players, or
   "needs four people"?** Bots make them playable at two or three, and cost
   more than the rest of the game each. Heimlauf's precedent is that an absent
   player's turn plays itself — that is a fallback, not an opponent.
3. **Kings.** Keep the drinking rules, keep the card rules with a harmless
   penalty instead of "drink", or hide it entirely when *Nur für Kinder* is on?
4. **French suits for Schafkopf and Klabberjass?** One card face set on the TV.
   Bavarian and Swiss suits would mean a second deck skin.

## Results

(to be filled in)
