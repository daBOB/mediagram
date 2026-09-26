# Phase 1 — Core read API (Rust, UniFFI)

**Status:** pending. Web reference: `web/src/catalog/{shows,credits}.ts`, `routes.ts`.

## Requirements
- `SetSummary` gains `collection_id`, `collection_name`, `series_type`, `show_status`,
  attached in `list_sets` like genres/facts (C`api/store/editorial.rs`, `shows/facts.rs`) —
  optional columns probed, so a v8 index reads as `None`.
- New read modules: `credits::for_title(conn, kind, id)`, `credits::for_person(conn, id)`,
  `credits::people_matching(conn, terms)` (both umlaut spellings, most-credited first,
  each with its title keys), `franchises::all(conn)`. Index first, then the device's
  fetched `details.db` where applicable.
- UniFFI: one `api/credits.rs` with its own `#[uniffi::export] impl Core` block
  (`title_credits`, `person`, `franchises`, `search_people`); DTOs `CreditRecord`,
  `PersonRecord`, `FranchiseRecord` in a new `dto/credits.rs` (dto.rs is near the limit).
- Portraits, lazily (user decision): `fetch_portrait(person_id)` downloads one
  `tmdb-person-<id>.jpg` (w185) from the profile path in `credits`, into the device's
  artwork dir, and returns its path; `poster_path` already serves it once present. Called
  from Kotlin when a Cast row / person page shows someone without a file. No bulk fetch,
  and no TMDB key needed: the image CDN URL is public.
- Regenerate bindings (`scripts/generate-android-bindings.sh`), rebuild the core
  (`scripts/build-android-core.sh`, 4 ABIs).

## Tests
Beside each module + `tests/api_surface.rs`, `dto_mapping.rs`; a v8 index (no tables)
answers empty, never errors. 200-line rule for every `src/` file.
