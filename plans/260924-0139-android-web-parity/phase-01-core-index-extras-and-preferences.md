# Phase 01: Core — index extras, preferences, profile delete

## Context links
- `web/src/routes.ts:369-391` (`forBrowser`: `genres`, `hasSummary`, `subtitles` per row)
- `web/src/assets.ts:25-51` (summary / subtitle / languages queries)
- `web/src/state/schema.ts:169-176`, `web/src/state/store.ts:144-146,297-320` (preferences, delete)
- `crates/mediagram-core/src/api/store.rs:55-70` (`list_sets`, where `fsk` is joined)
- `crates/mediagram-core/src/shows/mod.rs:104` (`certifications`, the pattern for genres)
- `crates/mediagram-core/src/api/enrich/details.rs:36-56` (index row first, fetched row fills)
- `crates/mediagram-core/src/state/schema.rs:15-103`, `state/profiles.rs`, `api/state.rs:28-137`

## Overview
Priority P1 · Status pending · No UI. Every later phase reads what this adds.

## Key insights
- Web puts `genres: string[]`, `hasSummary`, `subtitles: string[]` on every
  catalog row, keyed by poster key. `SetSummary` (`crates/mediagram-core/src/dto.rs:12-43`)
  has none of them; `TitleInfo.genres` is a comma string for one title only.
- Genres on the phone: index `shows.genres` first, then the device-fetched
  `details.db` for a title the index has none for — coarser-grained than
  `title_info`'s whole-row precedence, so a genre shelf can show a fetched
  genre list for a title whose overview still comes straight from the index.
  Deliberate difference from web (index only), same reason posters differ.
- `OLDEST_READABLE_SCHEMA = 6` (`crates/mlib-spec/src/schema.rs:14`); `assets`
  (v4) and `shows.genres` (v5) are always present, so no optional-column dance.
  One asymmetry this buys: a broken or missing `assets`/`shows` table in the
  *index* propagates as a `CoreError` (`list_sets` never expects one, unlike
  the web's `tolerate()` in `web/src/assets.ts`/`shows.ts`), while the same
  table missing from the device's own fetched sidecar is tolerated and logged
  — that store was always optional. Acceptable because `OLDEST_READABLE_SCHEMA`
  guarantees both index tables exist in anything this build will open.
- Preferences: web stores three opaque capped strings per `(profile, scope, name)`,
  empty value = forget, meaning lives in the reader. Port verbatim.
- Profile delete: web `DELETE FROM profiles`, children cascade. Core already
  has `ON DELETE CASCADE` + `foreign_keys` on (`state/mod.rs:87`).

## Requirements
- `SetSummary` gains `genres: Vec<String>`, `subtitles: Vec<String>` (langs,
  sorted), `has_summary: bool`.
- `Core::set_text(set_id, kind, lang) -> Option<String>`; kind limited to
  `"summary" | "subtitle"`, anything else `None`.
- State schema +1 group: `preferences` table identical to web's.
- `Core::preferences(profile_id) -> Vec<Preference{scope,name,value}>`,
  `Core::set_preference(profile_id, scope, name, value: Option<String>) -> bool`
  (same 200-char cap as `MAX_PREFERENCE`, `web/src/state/store.ts:95`).
- `Core::delete_profile(id) -> bool`; clears `chosen` if it was that profile.

## Architecture
index `library.db` --list_sets--> SetSummary{+genres,+subtitles,+has_summary}
  --CoreClient.listSets--> CatalogRepository --> MediaSet{+genres,+subtitleLanguages,+hasSummary}.
`set_text` opens the index read-only per call (like `title_info`); no cache.
`state.db` preferences: written by the player (phase 04), never synced.

## Related code files
Create:
- `crates/mediagram-core/src/shows/genres.rs` (index genres by poster key + fetched fill)
- `crates/mediagram-core/src/catalog_assets.rs` (subtitle langs map, summary set, `set_text` query)
- `crates/mediagram-core/src/api/set_text.rs` (uniffi export impl block)
- `crates/mediagram-core/src/state/preferences.rs` + `preferences_tests.rs`
- `crates/mediagram-core/src/api/preferences.rs` (uniffi exports: get/set)
- `crates/mediagram-core/tests/index_extras_in_catalog.rs`
Modify:
- `crates/mediagram-core/src/dto.rs` (3 fields; keep < 200 — terse docs)
- `crates/mediagram-core/src/api/store.rs:55-70` (join the three)
- `crates/mediagram-core/src/state/schema.rs` (new group), `state/profiles.rs` (`delete`), `api/state.rs` (`delete_profile`)
- `crates/mediagram-core/src/lib.rs`, `shows/mod.rs` (mod decls only)
- `android/core/rust/.../mediagram_core.kt` (regenerated)
- `android/core/data/src/main/kotlin/CoreClient.kt`, `DefaultCoreClient.kt`, `CatalogRepository.kt`
- `android/core/model/src/main/kotlin/MediaSet.kt`
- every fake `CoreClient` in tests (grep `: CoreClient` under `android/**/src/test`)

## Implementation steps
1. `shows/genres.rs`: `genres(conn) -> HashMap<String, Vec<String>>` mirroring
   `certifications` (split on `,`, trim, drop empties), plus fetched fill from `details.db`.
2. `catalog_assets.rs`: `subtitle_languages(conn)`, `summaries(conn)` (one query each), `text(conn, set_id, kind, lang)`.
3. Extend `SetSummary` + `summary_from` defaults; join in `list_sets`.
4. `api/set_text.rs` export; validate `kind`.
5. Schema group: web's `CREATE TABLE preferences` verbatim; `VERSION` moves by 1.
6. `state/preferences.rs`: list/set/forget with caps; tests incl. cascade on profile delete.
7. `profiles::delete` + `Core::delete_profile`.
8. Build core, regenerate bindings, wire Kotlin client, model, repository, fakes.

## Todo
- [x] genres map + fetched fill
- [x] subtitle langs / summary presence / `set_text`
- [x] SetSummary fields + list_sets join
- [x] preferences schema group, store, exports
- [x] delete_profile
- [x] bindings regenerated, Kotlin plumbing, fakes updated
- [x] check.sh, bump, changelog
- [ ] device: catalog still loads (v6 and v7 index) — left for the controller's device check

## Success criteria
- `index_extras_in_catalog.rs`: a set with two VTT rows lists `["de","en"]`; a
  summary row sets `has_summary`; genres by poster key; fetched-only title gets genres.
- `preferences_tests.rs`: set/replace/forget, cap refusal, cascade on delete.
- Migration test: an existing state.db at the old version upgrades with rows intact.
- Device: catalog loads, counts unchanged (`System` screen).

## Risks
- `dto.rs` 185 lines → may cross 200. Mitigation: move `summary_from` into `dto/summary.rs`.
- `list_sets` slower (two extra queries over 566 sets): trivial; measure once on device.
- Schema bump on a live device: ALTER-only migration, tested from the prior version.

## Security
`set_text` takes a set id and fixed kind whitelist; parameterised SQL only.
Preference strings are opaque, capped, never interpreted by the core.

## Next steps
02 (search reads the same summaries), 04 (preferences), 06 (subtitles), 11 (notes), 12 (delete).
