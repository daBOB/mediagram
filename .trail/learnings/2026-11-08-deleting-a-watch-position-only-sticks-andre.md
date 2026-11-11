---
title: Deleting a watch position only sticks across devices if a completion at least as new is…
author: Andre
date: 2026-11-08
id: l-6d274c4c
scope: repo
status: active
touches:
  - web/src/state/merge.ts
---

## Decided
Watch-state sync has no tombstone rows (see `web/src/state/merge.ts` header): a
deleted `progress` row is beaten on merge only by a `watched` row at least as
new. 2026-09-25: "Mark finished" on an already-played title skipped the
`watched` re-stamp (`if (!isWatched)` on web, `alreadyWatched` on Android), so
the phone's newer position came back on the next pull — visibly at every
server restart (sync runs at start). Fixed in 0.44.1 by always re-stamping;
user confirmed.

**Why:** any code path that clears a position without a fresh completion
reintroduces this silently; tests pass locally because the resurrection only
happens after a sync with another device.

## Watch out
when touching finish/clear logic on either surface, keep
`clearProgress` paired with `setWatched(now)`; for any new "remove from
Continue" action, decide its tombstone first. Related:
compare-state-dbs-before-blaming-sync.

_From Claude Code's memory of this repo, last updated Sep 25._
