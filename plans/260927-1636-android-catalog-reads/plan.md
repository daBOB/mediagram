# The core answers catalog reads whole (architecture review candidate I)

Branch `refactor/catalog-reads` (worktree `../mediagram-channel-index`), from main f98dec21 (0.68.13).

## Decisions (user-confirmed 2026-09-27, "accept your answers")

| # | Decision |
|---|----------|
| Q1 | Two core changes: the listing returns resolved artwork paths (artwork read once per listing, like the web's `/api/sets`), and a one-set lookup by id replaces "list everything, pick one" behind `CatalogRepository.mediaSet(id)` (callers unchanged). No Kotlin cache |
| Q2 | Resolved artwork everywhere: posters, backdrops, season posters (the web listing carries them; TV season plates look them up one by one today), and credit/person portraits (Rust resolves then discards them today) |
| Q3 | Delete the per-item `poster_path` API if nothing calls it any more (fix or remove its stale doc); otherwise keep what remains, with a cheap miss |
| Q4 | 0.68.14; bindings regenerated, native core rebuilt; Rust test on a seeded catalog; one `FakeCore` edit + a `CoreContract` case (tablet too); before/after on the tablet (debug `catalog` log per player open, cold-launch time to library); TV box untouched; merge when done |

## Baseline (tablet `caad49da`, 0.68.13, test profile)
- One episode open: 5 × `mediaSet(id)` = 223, 245, 251, 107, 115 ms (debug log `catalog`).
- Cold launch → library shown: 18.2, 16.8, 15.7 s (includes Telegram connect).

## Facts (main f98dec21)
- `CatalogRepository.sets()`: 1 `listSets` + up to 2 `posterPath` per set (poster, backdrop); ~1160 sets.
- `poster_path` (api/store.rs:72-91): 2 stats; a miss opens `library.db` and queries `artwork` (since 90546aba); doc at api/mod.rs:141-142 is stale.
- `list_sets` (api/store/editorial.rs) stats backdrops, returns keys only; `catalog::playable_set` exists internally.
- Credits resolve each portrait path in Rust (credits.rs:147-150) and return keys; Kotlin resolves again.
- `mediaSet` callers: PlayerChoicesController:114, UpNextAsync:49 (+updateRun), PlayerViewModelPreload:34/:56.
- Web reference: web/src/catalog/routes.ts:48-89 (`has`, artwork keys read once, artwork-routes.ts:43-50).

## Phases
| Phase | Status |
|-------|--------|
| 01 Rust: listing + one-set lookup + credits carry resolved artwork; seeded-catalog tests | done |
| 02 Bindings regenerated; Kotlin `CatalogRepository`/TV plates/credits on the new fields; `posterPath` removed if unused; FakeCore + CoreContract | done |
| 03 Unit tests + project check; docs, changelog, 0.68.14 | done |
| 04 Tablet: contract suite, before/after timings, smoke (lead) | done — RealCoreContractTest 9/9 incl. media_set |

## Review (2026-09-27)
- Code review: nothing visible changes on Android and `media_set` returns the same record
  as a listing. Applied: artwork keys read once per listing plus a per-listing memo
  (counter-tested), portraits reuse the open connection, key validation in the resolver,
  season plates keep their division guard, whole-record equality test, atomic artwork
  writes, docs made true. The disk-only backdrop is a known difference from the web,
  filed as daBOB/mediagram#1.
- Tablet `caad49da` (test profile), listing timed on device with a temporary log (removed):
  0.68.13 = listSets 122–179 ms + Kotlin mapping 1103–1144 ms (per-set artwork crossings);
  0.68.14 = listSets 127–174 ms (artwork included) + mapping 8–9 ms — about 9× faster.
  Cold launch to library (18–37 s on both builds, interleaved A/B) is dominated by
  Telegram connect/refresh, not the catalogue; the earlier "slower" numbers were drift.
- Smoke on 0.68.14: library, search, title page backdrop, cast portraits, playback.
