# The TV and the phones word Grand Allee's moves separately

The host sends typed facts for each stage of a move (`GrandAlleeTurn` to the TV,
the phone's sheet to the player), and each client phrases them in its own words.
We decided against one shared narration source: nothing had drifted, because
the facts are typed and a new stage breaks the TV's build, and the two audiences
want different text on purpose. The phone speaks to the player ("du"), and the
TV narrates in the third person with a short line beside the die that changes
when the die lands. Facts the clients had been working out themselves (the
board, the bank's price in a bid, going broke) now come from the host instead
(decided 2026-11-09, 0.167.0).

## Consequences

- Duplicated wording between `live_views.rs` and the TV's
  `grand-allee-status.ts` is intended. Remove a duplicated *fact* by having the
  host send it; leave duplicated *wording* alone.
