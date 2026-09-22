# Season poster wall

Series page shows its seasons as a wall of season posters (from TMDB);
opening one lists that season's episodes. Web + Android together (Surface Parity).

| # | Phase | Status |
|---|---|---|
| 1 | [Season poster keys + fetch (Rust)](phase-01-season-poster-keys-and-fetch.md) | done |
| 2 | [Web: season wall + season page](phase-02-web-season-wall.md) | done |
| 3 | [Android: season wall + season screen](phase-03-android-season-wall.md) | done |

## Key facts (verified)
- Cached `/tv/{id}` payloads already hold `seasons[].{season_number, poster_path}`
  (checked in ~/.local/share/mediagram/tmdb-cache) → **no new TMDB requests**.
- One fetch path for all three consumers: `mediagram_tmdb::posters::resolve_posters`
  + `download_into`, used by `mediagram posters`, `export_package`, and the
  Android core's `api/fetch.rs`.
- Key validator `mlib_spec::package::poster_key_is_valid` accepts exactly
  `source-kind-id`; web mirrors it (`posters.ts` KEY, `routes.ts` POSTER_PATH);
  core `poster_path` uses the spec's.

## Decisions (defaults, flag to change)
- Key: `tmdb-tv-<id>-s<n>` (4th optional part `s<digits>`; validator is source-agnostic, so any kind may carry it). Pre-release format
  change, fine per standing decision; old keys stay valid.
- Wall replaces the inline all-seasons list. Season opens its own page:
  web `#/series/<show>/<season>`, Android a season screen.
- **Single-season show: skip the wall**, go straight to the episode list
  (a wall of one is a click that says nothing).
- No season poster → the show poster stands in; no show poster → initials plate.
- Season plate caption: `Season N` + `n episodes` (watched progress if cheap).
- "Specials"/`Episodes` (season null) is a plate like any other, show poster.

## Verification
- Rust unit tests (key validity, season refs from fixture payload).
- `bun test`; web UI via stub harness (never the real player).
- Android unit tests + real phone check.
- Run `mediagram posters` against the real index to fetch season art.

## Version: minor bump (0.23.0) across the 3 manifests; changelog entry.

## Open questions
- None blocking; defaults above.
