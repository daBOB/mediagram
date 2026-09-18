# Phase 1 — The state store

The only writable database this player has ever had.

- `web/src/state/schema.ts` — DDL and a version, its own, unrelated to
  `mlib_spec::schema::SCHEMA_VERSION`. The player owns this one and may
  migrate it; it owns nothing about the index and may migrate none of it.
- `web/src/state/store.ts` — `WatchState`, wrapping the handle. Every method
  synchronous, as `bun:sqlite` is, and every one tolerant of a database that
  cannot be opened: a player whose state directory is read-only should lose
  watch positions, not refuse to play films.

Tables: `progress`, `watchlist`, `collections`, `collection_items`.

`progress.at_seconds` is where in the title, not how far through — a runtime
can be null in the index and a percentage cannot be recovered from one.

Success: opening twice is idempotent; a position survives a restart; a store
that cannot be opened degrades to one that remembers nothing.
