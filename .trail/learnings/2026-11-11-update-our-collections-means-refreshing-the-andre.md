---
title: "\"Update our collections\" means refreshing the TMDB franchise data and pushing the index"
author: Andre
date: 2026-11-11
branch: main
cost: { minutes: 5, tokens: 23355 }
id: l-816548ac
scope: repo
status: active
src: agent
touches:
  - scripts/setup-funstation.sh
  - crates/mediagram/src/metadata/title_details.rs
---

## Decided
- 2026-11-12, the user's choice: "update our collections" means a data refresh, not a UI change. Reinstall the CLI from main, then run a sync-index with a TMDB refresh, which pulls and merges the channel index, re-asks TMDB, fetches posters and pushes, so films get the franchises TMDB has added since.

## Tried and ruled out
- Reworking the Collections page, or hand-editing the lists. Both were offered; the user wanted the data refreshed.

## Watch out
- Franchise membership comes from each film's cached TMDB entry, so without `--refresh-older-than` a run only adds franchises for newly added films. An existing franchise's own record (name, overview) is never fetched again.
- A stale installed CLI must be reinstalled first (here it was 0.158.0 against main 0.176.3), because the push goes to the channel that every player reads.

In the code:
- `cargo install --path crates/mediagram --locked`, then `mediagram sync-index --refresh-older-than 30`
- crates/mediagram/src/metadata/title_details.rs backfill_franchise skips a franchise that is already recorded
