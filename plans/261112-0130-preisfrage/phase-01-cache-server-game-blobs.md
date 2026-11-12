---
phase: 1
title: "Cache server: game blobs"
status: done
priority: P1
effort: "3h"
dependencies: []
---

# Phase 1: Cache server: game blobs

## Overview
The home cache server keeps two blob kinds, TMDB JSON (`/v1/tmdb/{key}`, newer write replaces) and artwork
(`/v1/art/{size}/{name}`, first write stands). Preisfrage needs a weekly list that is replaced and photos keyed
by ASIN, so a third kind: `/v1/game/{game}/{name}`, newer write replaces, GET/HEAD open on the LAN, PUT signed
with the server token exactly like the others. Media line (the cache server ships with it).

## Tests first
- `blobs_tests.rs`: `BlobKind::Game` puts under `blobs/game/{game}/{name}`; a second put replaces the first;
  evicting game blobs follows the same budget and oldest-first rule; a scan at open finds them again.
- `http/blob_routes_tests.rs`: GET/HEAD/PUT `/v1/game/preisfrage/articles.json` and
  `/v1/game/preisfrage/photos-B0ABC12345.jpg` (one path level under the game, so the store name stays flat);
  unsigned or wrongly signed PUT refused; bad game or name (`..`, `/`, uppercase game, no extension, too long)
  answers 404 without touching disk; JSON over 2 MiB and images over 16 MiB refused; content type by extension.

## Architecture
- `blobs.rs`: `BlobKind::Game` (replace on write, like `Tmdb`); layout comment and the scan learn `game/`.
- `http/blob_routes.rs`: route `/v1/game/{game}/{name}`; `game_target`: game `[a-z]{1,24}`, name
  `[A-Za-z0-9._-]{1,80}` ending `.json` or `.jpg`/`.png`; cap `MAX_JSON` for `.json`, else `MAX_IMAGE`;
  `content_type` answers `application/json` for game `.json`.
- `rules.rs`: the name checks beside `valid_art_name`.

## Related Code Files
- Modify: `crates/mediagram-cache/src/blobs.rs`, `blobs_tests.rs`, `http/blob_routes.rs`, `http/blob_routes_tests.rs`,
  `rules.rs` (+ tests)
- Docs: `docs/running-the-player.md` § Home cache server (the third kind, its paths, that GET is open);
  `docs/project-changelog.md`

## Success Criteria
- [x] Every case above seen failing first, then green; existing cache tests unchanged and green.
- [x] `scripts/check.sh` green. Media line minor bump (new route): 0.177.0.
- [x] No file over 200 lines (split `blob_routes.rs` if the game target pushes it over).

## Risks
- Blob eviction is oldest-first across all blobs: a week of poster downloads could push out old photos.
  Acceptable: the list is rewritten weekly and photos are re-put with it (phase 2).
