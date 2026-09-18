# Phase 5 — Watchlist and collections

- Watchlist: one flag, its own shelf, toggled from a plate, a row, or the
  player.
- Collections: named lists, created and renamed and deleted from their own
  shelf, with any title addable from the player or from a row.

`position` on `collection_items` so a list keeps the order it was built in,
renumbered on insert rather than on every read.

The two stay separate rather than the watchlist becoming a built-in
collection: a flag is one row and one toggle, and expressing it as a list of
one buys nothing but a join.

Success: a title added twice appears once; deleting a collection takes its
items; renaming keeps them; a title in the catalog's Collections shelf opens
the same player everything else does.
