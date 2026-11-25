# Tiến Lên on the card table

User, 2026-11-24: "lets implement our card games. create the desk with the cards
and the players hand desk on their smartphone. so we can apply the different
rules for schwimmen, mau mau, tien len, etc".

The card table on the TV and the hand on the phone already exist since
2026-11-07 (`../261107-0207-card-games-and-bauherr/plan.md`), with Mau-Mau,
Schwimmen and Durak on them. Shown both screens, the user decided:

- **Table and phone hand stay as they are** ("Keep both").
- **This run builds Tiến Lên only.** It is the one remaining card game of
  `../261106-1808-card-games/plan.md` that needs no computer players.

## Rules

Source: pagat.com, "Tien Len", the main version (read 2026-11-24). Quoted where
the wording carries a condition.

| Rule | In the code |
|---|---|
| 52 cards, 13 each for 2–4; with fewer than four the rest "are not used in the game" | `DEAL`, `TienLenGame::new`, seats 2–4 |
| Two high … Three low; Hearts, Diamonds, Clubs, Spades; "Rank is more important than suit" | `strength` |
| "the player with the 3 of Spades begins play. If no one has the 3 … whoever holds the lowest card begins. The player must begin by playing this lowest card, either on its own or as part of a combination." | `opener`, `opening` |
| "Each player in turn must now either beat the previously played card or combination … or pass" | `on_action` |
| "If you pass you are locked out of the play until someone makes a play that no one beats." | `passed`, `next` |
| A play nobody beats is set aside and its owner "starts again by playing any legal card or combination" | `next` |
| Single, pair, triple, four of a kind; sequence of "Three or more cards of consecutive rank (the suits can be mixed)", J-Q-K-A-2 allowed, A-2-3 not; double sequence of "Three or more pairs of consecutive rank" | `combo` |
| "a combination can only be beaten by a higher combination of the same type and same number of cards"; "you just look at the highest card" | `beats` |
| Four of a kind or three pairs in a row beat a single two; four pairs a pair of twos; five pairs three twos. "these exceptions only apply to beating twos, not other cards" | `beats`, `Combo::twos` |
| Bombs "can be played even by a player who has previously passed" | `next`, `allowed` |
| "As players run out of cards they drop out of the play … The play ends when only one player has cards left. That player is the loser" | `play`, `finish` |

Calls of mine (not confirmed by the user):

- Every game started from the lobby counts as a first game: the lowest card
  leads. The rulebook's "In subsequent games, the winner of the previous game
  plays first" needs a memory across games that the room does not keep.
- A player who passed is asked again only while twos are the play to beat, and
  may then lay only a bomb. Everyone who passed and holds cards enough for a
  bomb is asked, bomb or not, so the table learns nothing about a hand (repo
  rule: prompts never reveal a hand); the card count is open to all.
- A play laid when nobody is left to answer it lies on show for two seconds
  (`settle_ms`) before the table is cleared.
- Bombs of two kinds do not beat each other; the rulebook is silent.
- The house passes for an absent player and leads their lowest single card.
- The phone lets every hand card be picked on your turn and the host refuses a
  pick that is no legal play, with the reason.
- Ranking is the order of going out; the stake payments are left out.
- The undealt cards of a 2- or 3-player game are not drawn on the TV.

## Work

- [x] `tienlen_rules.rs` with tests: ranking, combinations, beating, bombs
- [x] `tienlen.rs`, `tienlen_moves.rs`: deal, lead, pass, lock-out, call-back on
      twos, going out, ranking, the house
- [x] `tienlen_views.rs`: phone desk and TV scene
- [x] Catalogue, `GameKind::TienLen`, lobby seat gate 2–4, shelf entry moved
      from "Coming soon" to playable
- [x] TV: a play lies side by side (`cards-tienlen.ts`), each card flies in on
      its own; texts pre-drawn
- [x] Phone: several picked cards no longer hide the next card to pick
- [x] Played to the end against the dev host (two socket bots, one phone page
      in headless Chrome), screenshots in `reports/screens/`
- [x] Version 0.196.0, changelog, architecture doc
- [x] Code review findings worked in (see Results)
- [x] Commit, merge into `main` (user: "yes looks good", then "commit and merge")
- [ ] A game on the funstation with real phones

## Results

Whole workspace: clippy clean, 3,112 tests pass (before the review fixes); after
them `cargo test -p mediagram-game`: 716 pass, clippy clean, the 200-line check
passes. Client: typecheck clean, 215 tests pass.
`reports/play-tien-len.mjs <port> <out-dir>` replays the run against
`game-night-dev`: two games played to the end, three players (Ana, Cem, Ben) and
four (Ana, Cem, Ben, Dana).

Found while looking at the run, both fixed:

- On the phone a picked card was raised above its right neighbour and hid it,
  so a run could not be picked card by card. Where several cards are picked
  (`data-several`), a picked card now stays in its place in the stack.
- The TV drew the deck's shadow with no deck on the table (also in Durak once
  the deck is drawn out). The shadow now needs a deck or a trump card.

Code review (2026-11-24, no rule error, no reachable panic), worked in:

- A play laid when the last play's owner was out and everyone else had passed
  was cleared in the same action and never shown (16–25 % of three- and
  four-player games in the reviewer's model). It now lies on show first.
- The bomb hint named four of a kind for a pair or triple of twos, which it does
  not beat. The hint now names what beats the twos on the table.
- A run of all thirteen ranks is a legal play, so the table keeps thirteen
  events, not twelve.
- The loser no longer keeps a "Passt" tag on the final screen.
- A passed player holding too few cards for a bomb is no longer called back.

Left as they are:

- A refused pick clears the phone's selection, so the cards are picked again.
  Keeping the pick would let a double tap lay other cards when the same player
  leads again; a fix needs the phone to wait for the host's answer.
- With four players the left and right name tags lie over those seats' fans.
  The tags sit there in every card game; thirteen-card fans make it show.
- Not seen in a real run, only in tests: the two-second hold, a bomb, the bomb
  hints.
