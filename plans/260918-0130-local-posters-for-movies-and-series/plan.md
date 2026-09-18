# Local posters for movies and series

Single phase. Small enough that phase files would be bloat.

## Problem

Posters only exist inside the published package. A player reading the local
index (`PosterStore(null)`, `web/src/index.ts:76`) has no artwork source at
all, so every card falls back to initials. Verified against the live library:
171 sets, 9 with a TMDB id, 0 rendering an image.

## Decision

Posters live **beside the index, wherever the index is**. In package mode that
is the catalog dir, unchanged. In local mode it is the data dir. One
expression covers both, so there is no second code path to keep honest.

Scope is movies and series only. `titles::distinct_titles` already selects
exactly `kind IN (movie, ep) AND tmdb IS NOT NULL`, so the course needs no
exclusion written anywhere — it simply has no id to key a poster by.

## Work

- [x] `export/mod.rs`: `restrict` moves here, shared by stage and posters
- [x] `export/posters.rs`: `download_into(http, refs, dir)` — the download loop
      and its limits move out of `Staging`, which only ever borrowed them
- [x] `export/stage.rs`: `fetch_posters` delegates, keeps manifest paths
- [x] `commands/posters.rs`: new `mediagram posters`, fetches into
      `<data dir>/posters/`, skips what is already held
- [x] `commands/mod.rs`, `main.rs`: register the subcommand
- [x] `web/src/index.ts`: `PosterStore(dirname(indexPath))`, always log the count
- [x] Tests: download resilience + skip-existing (Rust), local poster dir (web)
- [x] Docs: README command table, running-the-player.md, project-changelog.md

## Success criteria

- `mediagram posters` writes 9 jpgs to `~/.local/share/mediagram/posters/`
- Player logs a poster count in local mode and serves `/api/posters/<key>.jpg`
- Course cards keep their initials; nothing about the package path changes
- `cargo test`, `cargo clippy`, `bun test` all pass

## Review

Done and verified against the live library on 2026-09-18.

`mediagram posters` fetched 2 posters — 2 distinct titles, not 9: the 8
Spartacus episodes share one series id, plus Blade: Trinity. Through the real
router over the real index: 171 playable sets, 9 rendering a poster, both
images served as `200 image/jpeg` with JPEG magic bytes. Re-running reports
`0 fetched, 2 already held`.

638 Rust tests pass, 407 web tests pass, clippy is clean on all targets.

One pre-existing failure is untouched and unrelated:
`config_requires_api_hash_and_channel` asserts `config::load` rejects an empty
`api_hash` and it does not. It fails identically on the committed version of
that test file, so it predates this work. Either the validation or the
expectation is wrong; deciding which is a config-semantics call, not a poster
one.
