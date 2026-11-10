# Phase 2: The media app answers Spieltisch

## Context links

- Plan: [plan.md](plan.md)
- Code today:
  - `crates/mediagram-core/src/game_titles.rs:22-111` — titles from the library
  - `crates/mediagram-core/src/game_posters.rs:21-97` — textless posters via TMDB
  - `android/feature/games/src/main/kotlin/GameNightConfig.kt:28-48` — kids lock and sets
  - `crates/mediagram-game/src/server.rs:44,146` — the `/img` route reads file paths

## Overview

- Priority: second.
- Status: pending.
- What it does: Spieltisch shows the library's films (Poster-Raten, and the film rounds of
  the quiz, Kopfnuss and Dreifelder) under the active profile's kids lock, all read from the
  media app. Spieltisch holds no Telegram session, no TMDB key and no profile data.

## Key insights

- The titles' images are paths inside the media app's private storage, which Spieltisch cannot open. Files cross as file descriptors from a provider.
- `GameTitle` is already `Deserialize` (the dev host loads it from JSON), so titles can cross as JSON in that shape. Neither side needs the other's crate.
- Kids rules (`profile.kids`, `kidsLimit`, `forKidsProfile`, hidden sections) stay in the media app. Spieltisch only receives the answer.

## Requirements

- Functional:
  - At night start, Spieltisch reads the night from the provider: kids lock, the titles allowed for the profile, and the visible titles.
  - The game host serves a poster or still by fetching it from the provider once into Spieltisch's cache.
  - Textless posters are fetched by the media app (it has the key and the TMDB crate).
- Fail safe:
  - If the media app is missing or doesn't answer within 3 s: kids-locked, no titles, and a line on the shelf saying the films are unavailable.

## Architecture

- Media app: a `GameNightProvider` (authority `com.mediagram.android.games`).
  - Guarded by a `signature` permission that only the same signing key holds.
  - `query(/night)` returns one row: `kids_locked`, the titles' JSON.
  - `openFile(/image/{key}/{poster|still})` and `openFile(/textless/{key})` return files.
  - Built on the existing `GameNightConfig` logic and two uniffi calls in `mediagram-core`:
    `game_night_titles(config) -> String` (the JSON) and `textless_poster(key) -> Option<path>`.
    These replace `start_game_night` in phase 4.
- Spieltisch:
  - `MediaLibraryBridge` reads the provider.
  - The titles' `poster`/`still` become paths in Spieltisch's cache.
  - The host's image route gets a fetch hook from the embedder, the same pattern as `PosterLookup` today: if the file is missing, ask Kotlin to copy it from the provider.
- Contract: one fixture, `crates/mediagram-game/tests/fixtures/night-titles.json`.
  The core's test produces that shape; the game crate's test parses it.

## Related code files

- Create:
  - media app `GameNightProvider.kt` + its permission in `app/src/main/AndroidManifest.xml`
  - `crates/mediagram-core/src/api/game_night_export.rs`
  - Spieltisch `MediaLibraryBridge.kt`
  - the fixture
- Modify:
  - `crates/mediagram-game/src/server.rs` (image fetch hook)
  - `crates/mediagram-game/src/titles.rs` if paths need deferring
  - `crates/spieltisch-core` (config takes the titles' JSON and the hook)
  - Spieltisch's view model (reads the bridge)

## Implementation steps

1. Core: `game_night_titles` + `textless_poster`, with tests, reusing `game_titles.rs` and `game_posters.rs`.
2. Media app provider and its signature permission. Robolectric tests: the night row, files by key, unknown key → FileNotFound, and a foreign caller refused.
3. Game crate: the image fetch hook and the fixture contract test. Its existing tests stay green.
4. Spieltisch: the bridge, the 3 s fail-safe and the cache.
5. Emulator, both apps: a Poster-Raten round shows library posters, a kids profile gets only kid-safe titles, and with the media app stopped Spieltisch is locked. Time a night's start with the media app's process killed beforehand.

## Todo

- [ ] core exports + tests
- [ ] provider + permission + tests
- [ ] image fetch hook + fixture contract
- [ ] Spieltisch bridge, fail-safe, cache
- [ ] emulator check incl. cold-start timing → `reports/`

## Success criteria

- Poster-Raten in Spieltisch shows the library's posters.
- A kids profile in the media app means kid-safe titles only in Spieltisch.
- No media app means locked with no films, and never unlocked.

## Risk assessment

- Cold start: a provider call starts the media app's process and its Rust core. If that costs more than a couple of seconds, the media app answers `/night` from a cached snapshot written whenever the profile or library changes.
- Large libraries: the titles' JSON goes through a cursor row. Above about 1 MB, return it as a file from `openFile(/night.json)` instead.

## Security considerations

- The signature permission is the only gate. Without it, any app on the box could read the library's titles and posters.
- The kids lock fails closed.
- With the lock on, only `kids_ok` titles leave the media app (the provider filters `game_night_titles`' output). The host filters again, so a title that isn't kid-safe never crosses the boundary on a kids profile.

## Next steps

Phase 3: releases and updates for each app.
