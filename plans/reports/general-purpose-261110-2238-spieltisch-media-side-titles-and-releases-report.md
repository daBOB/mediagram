# Media side of the Spieltisch split: titles export and release posts per app

Branch `feat/spieltisch-media-side`, uncommitted. No version bumped.

## What changed

Titles and posters (`mediagram-core`):
- `src/game_titles.rs` builds a core-owned `NightTitle` (+ `TitleKind`), `Serialize`, in `GameTitle`'s JSON shape. No longer imports `mediagram-game`.
- `src/api/game_night_export.rs` (new): uniffi `game_night_titles(config) -> String` and `textless_poster(title_key, tmdb_key) -> Option<String>`; `night_titles()` is the one builder both paths use.
- `src/api/game_night.rs`: `start_game_night` builds through `night_titles()` and hands the host `Vec<GameTitle>` via a serde round trip of the same JSON.
- `src/dto/game_night.rs`: `NightTitlesConfig { kids_set_ids, visible_set_ids }`.
- `src/game_posters.rs`: `CACHE_DIR` const; the per-title download locks are now process-wide, so per-call lookups from the export still serialise downloads of one title.
- Contract: `crates/mediagram-game/tests/fixtures/night-titles.json`, checked by `game_titles_tests.rs` (core serialises to exactly it) and `crates/mediagram-game/tests/night_titles_fixture.rs` (parses it as `Vec<GameTitle>`). `GameTitle` needed no serde change.

Release posts per app:
- `mlib-spec/src/app_caption.rs`: `render/parse/newest` take the tag; `MEDIA_APP_TAG = "#mlib-app"`, `SPIELTISCH_TAG = "#spieltisch-app"`. Old `PREFIX`/`MARKER` removed (unused elsewhere).
- `mediagram/src/app_release/publish.rs`: package table → tag + file stem; newest-check and unpin per tag.
- `mediagram-core/src/api/channel/app_release.rs`: `ReleaseApp { MediaApp, Spieltisch }`; `latest_app_release(handle, app)`.
- `docs/mlib-spec.md` §7a documents the second tag.
- Android: `AppUpdater` passes `ReleaseApp.MEDIA_APP`; `FakeCore` implements the new calls; bindings regenerated.

## Tests added

- caption: each app reads only its own tag; a higher games versionCode is invisible to the media app's `newest`.
- publish: Spieltisch tag + `spieltisch-*.apk/.dm` names; `each_app_replaces_only_its_own_release` (both directions, and a media versionCode below Spieltisch's is accepted). Watched it fail with an unpin-any-app rule.
- core: titles JSON equals the fixture; export returns JSON under the caller's rules; textless needs a TMDB key; a cached textless poster is found by title key; `ReleaseApp` tags.
- Android: `AppUpdaterTest` asserts the updater asks for `MEDIA_APP`.

## Check

`scripts/check.sh` passed in full ("all checks passed", Gradle `BUILD SUCCESSFUL`). An earlier run, made while another worktree's check loaded the machine, failed once in `ui-mobile`'s `CacheBudgetViewModelDispatchTest` (it reads a thread that `Dispatchers.IO` work records, which `waitForIdle` does not wait for). It passed alone and in the full rerun, and this change does not touch that module.

## Deviations / open

- `download_app_release` does not take the app: the `AppRelease` it is given already names its message, so the parameter would be unused. One line to add if wanted.
- `game_night_titles` sends every title with its `kids_ok`, and the kids lock travels separately (the host drops unsafe titles when locked). Dropping unsafe titles in the export when the profile is locked would be defence in depth; not done, as the lock is not in the config.
- The shared scratchpad: another agent's `check.log` at the same scratchpad path was overwritten by my first check run; mine now logs under `scratchpad/spieltisch-media-side/`.
