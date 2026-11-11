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
- Status: done; the night is answered from a fingerprinted snapshot (see Result).
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

## Design details (settled 2026-11-10, before building)

- **Where the provider lives:** `android/feature/games`, beside `GameNightConfig`, which
  holds the kids and section rules it needs. Phase 4 removes the module's UI, view model
  and announcer, and keeps the config and the provider.
- **Permission:**
  - `com.mediagram.android.permission.GAME_NIGHT`, `protectionLevel="signature"`.
  - Both apps declare it. If Spieltisch is installed before the media app, it still holds
    the permission once the media app arrives.
  - The provider requires it to read.
- **Package visibility (Android 11+):**
  - Spieltisch declares `<queries><provider android:authorities="com.mediagram.android.games"/></queries>`.
  - The media app declares `<queries><package android:name="com.mediagram.spieltisch"/></queries>`,
    needed in phase 4 to launch it.
- **Night row:**
  - `query(/night)` returns one row: `kids_locked`, and the `titles` JSON from
    `game_night_titles` under the active profile's config, built from the same
    `GameNightConfig` rules as an in-app night.
  - No profile, or the media app not ready: locked and `[]`.
- **Files:**
  - `openFile(/image/{key}/{poster|still})` and `openFile(/textless/{key})` serve a title
    only if it is in the night's allowed titles for the active profile right now.
    Otherwise FileNotFound, so a key can't fetch a picture the profile may not see.
  - Read-only file descriptors, nothing else.
- **Spieltisch's images:**
  - The host's `/img` route and the textless lookup ask the embedder for a file the first
    time. Kotlin implements a uniffi foreign trait, `MediaImages { fetch(key, kind) -> path? }`.
    It copies the descriptor into Spieltisch's cache and returns the cached path.
  - Rust calls it off the async runtime (`spawn_blocking`).
  - Only the host's entry point and its image route change in `mediagram-game`.

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

- [x] core exports + tests
- [x] provider + permission + tests
- [x] image fetch hook + fixture contract
- [x] Spieltisch bridge, fail-safe, cache
- [x] emulator check incl. cold-start timing → `reports/`

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

## Result (2026-11-11)

Built as designed, then the risk below came true and its mitigation was built
too (coordinator's decision, 2026-11-11). Every new test was watched failing
against a broken version (`reports/provider-broken-versions.log`).

**Before the snapshot** (`reports/provider-emulator.log`): on the emulator
(`Google_TV_1080p`, x86, a 28,038-set library, 1,764 titles, 606 KB of JSON),
`/night` took **3735–3885 ms cold** and 2491–2971 ms warm, so a cold night
missed the 3 s limit and was locked. Warm, the cost was all in the media app:
`catalog.sets()` ~0.8 s, `gameNightConfig` ~0.2 s, `Core.gameNightTitles`
~1.2 s. Every first image fetch rebuilt the same night.

**A first snapshot design** (kept up to date by a background observer) was replaced after
review: the observer rebuilt on every progress save, and a snapshot checked only by profile
id and kids flag could still be served after a kids profile's rules tightened.

**As built: a fingerprinted night, the warm-up and an 8 s limit** (`reports/fingerprint-emulator.log`):

- The fingerprint covers the chosen profile's id, kids flag, kids limit and hidden sections,
  the kids marks, and the catalog marker. Everything is read from the core on every ask.
- The catalog marker is `catalogFacts()`: origin, `publishedAt`, set and poster counts. It
  costs 230–400 ms on the emulator; the profile and the marks take under 25 ms.
- A free marker would need a new core export, the `current` link's target.

| `/night` | ms |
|---|---|
| cold media app, with Spieltisch's warm-up | 929, 988, 1345 |
| cold media app, no warm-up | 1084, 1104, 1872 |
| warm media app | 427, 545, 598 |
| first ask after a catalog-marker change, warm | 4350 (a build) |
| first ask after a change, cold, with the warm-up | 4493 (one build, the night's ask joined it) |
| no readable snapshot at all, both cold | 5010 (one build) |

- Every case answered inside the 8 s limit.
- After the re-review (`reports/rereview-emulator.log`):
  - The fingerprint also carries the app version, which the snapshot's header
    requires, and `described`, a digest of the fetched descriptions.
  - The build lists the sets from the core in hand, and the locked work is
    limited to 30 s.
  - Slow textless misses are remembered too.
  - The marker cost 224–396 ms with no description store on this emulator,
    and 233–368 ms with a synthetic store of 2,000 described titles: no
    measurable change (the digest takes 7 ms on the host).
  - The first ask after the store appeared rebuilt in 4077 ms.
  - From the snapshot: cold with the warm-up 1058–1448 ms, cold without it
    1109–1212 ms, warm 396–541 ms.
  - The first ask under a new app version rebuilt (5671 ms, both cold),
    inside the limit.
- In the media app: a build took 4.2–4.7 s, an answer from the snapshot 235–984 ms. The
  ask after a change was served from the snapshot again (525 ms).
- A first image check through the provider took 5–134 ms, with no build.
- A title without a textless poster was asked about once per night.
- The active profile is the core's own `chosen_profile`; there is no settings copy. Reading
  it with the profile list took 2–23 ms once the core was built.
- Still unseen on a device: an image crossing, since this library has no film posters (only
  31 documentary artworks), so Poster-Raten says "Nicht genug Poster".
- Media app disabled: locked in 73 ms, and the shelf says the films aren't available. The
  shell (no permission) is refused by the manifest.

**Fix verification** (`reports/code-reviewer-261111-0410-spieltisch-phase-2-fix-verification.md`):
all six fixes hold, commit-ready. Kept as known:

- The 30 s limit cuts off a build that takes longer, on every ask: such a box never gets films.
  It fails closed and is now logged; the box run measures it (phase 5).
- The limit can't cut in while the core itself is being built or a failed close retried.
- A textless poster the media app's TMDB fetch timed out on is not asked for again that night:
  the title keeps its lettered poster. Chosen over re-asking a slow fetch on every round.

## Next steps

Phase 3: releases and updates for each app.
