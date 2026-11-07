# Phase 02 — Schwimmen and Durak

**Status:** done, merged 10:12 (Schwimmen) and 10:14 (Durak); review fixes 11:55. Two
games on phase 01's card table: rules + flow + views + lobby entry each, no new
wire types unless the generic desk/scene really cannot express something.

## Schwimmen (2–8 players, 32 cards)

Each player 3 cards, 3 face up in the middle, 3 lives (coins on the TV). On a turn:
swap one hand card with one middle card, swap all three, pass (*schieben*), or
knock (*klopfen*: everyone else has one more turn, then the reveal). If everyone
passes in a row, the middle is replaced from the deck. Hand value: the best suit's
sum (Ass 11, Bild 10, numbers their value) or three of a kind = 30½. 31 (*Feuer*)
ends the round at once. The lowest hand loses a life (ties: all lowest); with no
life left a player *schwimmt* (one more round), the next loss is out. Last player
left wins. Absent player: best swap by hand value, knocks at ≥ 27.

## Durak (2–6 players, 36 cards, classic throw-in)

6 cards each, the bottom card is trump and lies face up under the deck. The
attacker leads; the defender beats each attack card with a higher card of its suit
or any trump, or takes everything. Others may throw in cards of ranks already on
the table, in seat order after the attacker, up to 6 attacks and never more than
the defender holds. All beaten → discarded (*Bito*), the defender attacks next;
taken → the player after the defender attacks. Hands refill to 6 from the deck,
attacker first, defender last. Players out of cards when the deck is empty are
safe; the last one holding cards is the *Durak*. Ranking: order of getting out.
Absent player: lowest legal card, takes when it cannot beat, throws in nothing.

## Success criteria

Same as phase 01 per game: tests for every rule above, scripted phones play each
game to the end, desktop screenshots.
