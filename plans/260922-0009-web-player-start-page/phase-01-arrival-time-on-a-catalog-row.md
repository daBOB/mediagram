# Phase 1 — Arrival time reaches the browser

Spec: `docs/superpowers/specs/2026-09-22-web-player-start-page-design.md` §2.

## Overview

Priority: first, and independent of phase 2. The Latest rows rank by when a
title arrived, and that fact currently stops at the server.

## Key insight

`listPlayable` already orders by `created_at DESC`, so the arrival order is
in the payload — but implicitly, in array position, and `groupLibrary()`
sorts movies by title and collections by name, which destroys it. A rule
about time reads time.

## Requirements

- Every catalog row the browser receives carries `addedAt`, epoch millis.
- No other behaviour changes; the column is already in every `SELECT`'s
  table and adds ~8 bytes a row to a 294 KB payload.

## Related code files

- `web/src/catalog.ts` — `COLUMNS`, `PlayableSet`
- `web/public/lib/library.d.ts` — `CatalogSet`
- `web/test/catalog.test.ts` — pins what a row carries

## Implementation steps

1. Add `created_at AS addedAt` to `COLUMNS`.
2. Add `addedAt: number` to `PlayableSet`, documented as the uploader's
   arrival time and not a release date.
3. Add the same to the browser's `CatalogSet` declaration.
4. Confirm `forBrowser()` in `routes.ts` needs no edit — it spreads the row.
5. Test: a row from `listPlayable` carries `addedAt`, and the order it comes
   back in agrees with that field descending.

## Todo

- [ ] `COLUMNS` and `PlayableSet`
- [ ] `CatalogSet`
- [ ] catalog test
- [ ] `bun test`

## Success criteria

`/api/sets` rows carry `addedAt`; `bun test` green.

## Risks

Fixtures that build a `PlayableSet` by hand lose a field the type now
requires. Bun strips types rather than checking them, so this surfaces as a
wrong test rather than a failed build — check the fixtures by hand.

## Security

None. `created_at` says when a file was uploaded, which the catalogue
already implies by listing it.
