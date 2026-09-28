---
phase: 1
title: "Index: original language, anime overrides, backfill, `edit --anime`"
status: completed
priority: P2
effort: 5h
dependencies: []
---

# Phase 1 — Index: original language, anime overrides, backfill, `edit --anime`

## Overview

Uploader-only. Schema v11 gives every TMDB title its original language and adds
a hand-set override table; `mediagram metadata` backfills the language from the
TMDB disk cache (no network); `mediagram edit <set-id> --anime yes|no|auto` sets
or clears an override; `pull-index`/`push-index` merge both between the two
uploading machines. No reader changes behaviour yet: web and core readers
ignore a column/table they do not query. Ships as its own release.

## Requirements

- `shows.original_language TEXT` (TMDB ISO 639-1 code, e.g. `ja`), written by the
  one `shows` writer on every `add`, `add-show`, `metadata` and device fetch.
- `anime_overrides(source, kind, id, anime, set_at)`: `anime` 1 = force in,
  0 = force out, NULL = back to the automatic rule (kept as a row: a tombstone
  must travel through a merge, a deleted row cannot).
- Override is per TMDB title (`source`,`kind`,`id` — the `shows`/poster key),
  so it covers every episode of a series, including episodes uploaded later.
- `edit --anime` is index-only (no caption rewrite, no Telegram); needs a TMDB id
  and kind `movie` or `ep`; `--dry-run` writes nothing; never pushes.
- Merge: `shows.original_language` rides the existing `shows` NULL-fill;
  `anime_overrides` merges last-writer-wins on `set_at`.
- An older channel snapshot (no column/table) merges cleanly (0 taken).
- Web `EXPECTED_SCHEMA` moves to 11 in the same commit (lockstep test).

## Architecture

**Why not a `docu`-style caption kind:** anime series need `ep` season/episode
structure and TMDB identity; the kind field cannot carry both.

**Why a separate table, not a column on `shows` (decision):**
1. `mediagram_core::shows::upsert` (`crates/mediagram-core/src/shows/mod.rs:37`)
   replaces a row whole on every `metadata` run; a person's decision must not be
   in that row.
2. `crates/mediagram-core/tests/shows_upsert_covers_schema.rs:78-93` fails for any
   `shows` column the writer does not set — an override column would have to be
   written by the TMDB writer, i.e. clobbered.
3. `merge_shows` (`crates/mediagram/src/index/merge_shows.rs:17-23`) only fills
   NULLs. An override changed yes→no on machine A would be reverted by machine B's
   next publish (B keeps its non-NULL `1`, A's `0` never reaches it), and
   `auto` (NULL) would be refilled from the channel. The override needs
   last-writer-wins; a timestamped row gives it in one SQL statement.

**Why not per set (`sets` column or caption field):** a series is hundreds of
sets; later episodes would not inherit it, and a series could split across two
departments.

**Data flow**

```
add / add-show / metadata ──> mediagram_tmdb::details::from_details
   (cached /movie|/tv payload: original_language)          │
                                                           ▼
                                 mediagram_core::shows::upsert ──> shows.original_language
edit <set> --anime yes|no|auto ──> index::anime_overrides::set ──> anime_overrides row (set_at = now)
push-index / pull-index ──> merge_from: merge_shows (NULL-fill) + merge_anime_overrides (newer set_at wins)
readers (phases 2–3) ──> rule(kind, genres, original_language, override)
```

**Schema v11** (`crates/mlib-spec/src/schema.rs`, new `const V11` after `V10` at :118):

```rust
const V11: &[&str] = &[
    "ALTER TABLE shows ADD COLUMN original_language TEXT",
    "CREATE TABLE IF NOT EXISTS anime_overrides(
        source TEXT NOT NULL,
        kind TEXT NOT NULL,
        id INTEGER NOT NULL,
        anime INTEGER,
        set_at INTEGER NOT NULL,
        PRIMARY KEY(source, kind, id)
    )",
];
```

Doc comment on `V11` states: language is TMDB's code from the cached payload,
optional to every reader; overrides kept apart from `shows` because its writer
replaces rows whole; NULL `anime` is a kept row so clearing reaches the other
machine; `set_at` (Unix seconds) decides a merge.

**Merge rule** (`merge_anime_overrides::merge`), one statement:

```sql
INSERT INTO main.anime_overrides(source, kind, id, anime, set_at)
SELECT source, kind, id, anime, set_at FROM channel.anime_overrides WHERE true
ON CONFLICT(source, kind, id) DO UPDATE
   SET anime = excluded.anime, set_at = excluded.set_at
 WHERE excluded.set_at > anime_overrides.set_at
```

Returns `conn.changes()` → `MergeReport.anime_overrides_taken`. Skipped (0) when
`shared_columns(conn, "anime_overrides")` is empty (channel predates v11), the
`merge_artwork.rs:17-20` precedent. Equal `set_at` keeps the local row: one
person sets overrides; a same-second disagreement between two machines is not a
real case.

**`edit --anime`** (`EditArgs.anime: Option<AnimeChoice>`, clap `ValueEnum`
`yes|no|auto`, `conflicts_with_all` every other edit flag except `dry_run`):

```
$ mediagram edit 01JQ… --anime yes
set 01JQ… — Mila Superstar (tmdb-tv-46348: every set of this title)
  anime: automatic -> yes
wrote the override; run `mediagram push-index` to publish
```

Refusals: no TMDB id → "set … has no TMDB id; an anime override belongs to a
TMDB title — give it one with `--tmdb` first"; kind `tut`/`doc`/`docu` → "set …
is a <kind>; only films and series can be anime". Same value again → "already
says <x>; nothing to do".

## Related Code Files

**Create**
- `crates/mlib-spec/src/schema.rs` gains `V11` (modify, see below)
- `crates/mediagram-tmdb/src/tmdb_title_refs.rs` — `CollectionRef`, `CreatedBy`,
  `SeasonRef` moved out of `tmdb_types.rs` (now exactly 200 lines; the new field
  would break `crates/mediagram/tests/code_standards.rs:9` LIMIT). Re-exported
  from `tmdb_types` with `pub use`, so no caller path changes.
- `crates/mediagram/src/index/anime_overrides.rs` — `get(conn, kind, id) -> Result<Option<bool>>`
  (None = automatic, whether no row or a NULL row), `set(conn, kind, id, anime: Option<bool>, at: i64)`;
  `kind` spelled via `mediagram_tmdb::posters::kind_key` (`posters.rs:170`).
- `crates/mediagram/src/index/merge_anime_overrides.rs` — `pub(super) fn merge(conn) -> Result<usize>`.
- `crates/mediagram/src/edit/anime.rs` — `AnimeChoice` enum; pure
  `target(row: &SetRow) -> Result<(Kind, u64)>` (the refusals) and
  `run(conn, row, choice, dry_run) -> Result<()>`.

**Modify**
- `crates/mlib-spec/src/schema.rs` — `SCHEMA_VERSION` 10→11 (:8); `READABLE_SCHEMAS`
  add 11 (:27); `GROUPS` add `V11` (:41); `OLDEST_READABLE_SCHEMA` doc (:10-20)
  gains "v11 only `shows.original_language` plus the wholly new `anime_overrides` table".
- `crates/mediagram-tmdb/src/tmdb_types.rs` — `DetailsResponse` gains
  `#[serde(default)] pub original_language: Option<String>` beside `genres` (:101).
- `crates/mediagram-tmdb/src/lib.rs` — `mod tmdb_title_refs;`.
- `crates/mediagram-tmdb/src/details.rs` — `TitleDetailsRow.original_language`
  (after `series_type`, :62); `from_details` fills it, blank → `None`.
- `crates/mediagram-core/src/shows/mod.rs` — `upsert` (:37) writes the column
  (insert list, `excluded` update, param); `get` (:83) reads it via
  `optional_column(conn, "original_language")` (:158); doc of `optional_column`
  names v11.
- Every `TitleDetailsRow { … }` literal (add `original_language`):
  `crates/mediagram-core/src/api/enrich/details_tests.rs:11`,
  `crates/mediagram-core/src/shows/sidecar_tests.rs:9`,
  `crates/mediagram-core/tests/shows_query.rs:63`,
  `crates/mediagram-core/tests/shows_upsert_covers_schema.rs:33` (value `Some("ja")`),
  `crates/mediagram-core/tests/index_extras_fetched_genres.rs:48`,
  `crates/mediagram/tests/index_shows.rs:19`. (Non-test sites: `details.rs:75`,
  `shows/mod.rs:98` — both covered above. 8 total.)
- `crates/mediagram/src/index/mod.rs` — `pub mod anime_overrides; mod merge_anime_overrides;`.
- `crates/mediagram/src/index/merge.rs` — table in module doc (:9-16) gains
  "`anime_overrides` | Missing keys inserted; shared keys take the later `set_at`";
  `MergeReport.anime_overrides_taken: usize`; call after `merge_artwork::merge` (:155).
- `crates/mediagram/src/channel_index/report.rs` — print "N anime override(s) taken" when > 0 (beside :51).
- `crates/mediagram/src/commands/args.rs` — `EditArgs.anime` (:117-154).
- `crates/mediagram/src/commands/edit.rs` — right after `get_set` (:29-30):
  `if let Some(choice) = args.anime { return crate::edit::anime::run(&conn, &row, choice, args.dry_run); }`
  (file 186 lines → ~189).
- `crates/mediagram/src/edit/mod.rs` — `pub mod anime;`.
- `web/src/catalog.ts` — `EXPECTED_SCHEMA = 11` (:24; enforced by
  `crates/mediagram/tests/shared_playable_sql.rs:39`); `OLDEST_READABLE_SCHEMA` doc (:26-36) names v11.
- Tests: `crates/mediagram-tmdb/tests/details.rs` (language read, blank → None);
  `crates/mediagram/src/index/merge_tests.rs` (cases below);
  `crates/mediagram/tests/edit_plan.rs` or `edit/anime.rs` inline tests (refusals);
  `crates/mediagram/src/cli_tests.rs` (parse `--anime yes`, reject `--anime maybe`,
  reject `--anime yes --title x`); `crates/mediagram/tests/schema_migrations.rs`
  (a v10 file gains the column and table; `an_upgraded_database_matches_a_fresh_one` :83 covers shape).
- Docs: `docs/mlib-spec.md` §6 new "Schema v11 additions" after v9's (:247-289);
  `docs/system-architecture.md` §10.1 heading → v11 plus two bullets (:759);
  `README.md` command table — `metadata` row (:230) mentions original language,
  `artwork` / `edit` row (:232) mentions `--anime yes|no|auto`;
  `docs/project-changelog.md` new top entry.
- Versions: `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts`
  `versionName` — minor bump (0.75.5 → 0.76.0 if main is still there; bump by
  pattern from whatever main carries). `versionCode` untouched.

**Delete** — none.

## Implementation Steps

1. Schema: add `V11`, bump `SCHEMA_VERSION`, `READABLE_SCHEMAS`, `GROUPS`, docs.
   Run `cargo test -p mlib-spec` (`schema_tests.rs:12` range test, `:27` one group per version).
2. Web lockstep: `EXPECTED_SCHEMA = 11` + doc line. `cargo test -p mediagram --test shared_playable_sql`.
3. TMDB: move the three ref structs to `tmdb_title_refs.rs`, add `original_language`
   to `DetailsResponse` and `TitleDetailsRow`, fill in `from_details`
   (`.clone().filter(|t| !t.trim().is_empty())`). Test in `mediagram-tmdb/tests/details.rs`.
4. Writer: `shows::upsert`/`get` in core; fix the 6 test literals. Run
   `cargo test -p mediagram-core --test shows_upsert_covers_schema` (red until upsert writes it).
5. Overrides store: `index/anime_overrides.rs` `get`/`set` (`INSERT … ON CONFLICT(source, kind, id)
   DO UPDATE SET anime = excluded.anime, set_at = excluded.set_at`), with tests:
   set yes → get `Some(true)`; set auto → row kept, get `None`.
6. Merge: `merge_anime_overrides.rs` + wire into `copy_kept` and `MergeReport`,
   `report.rs` line. Tests in `merge_tests.rs`:
   - channel-only override inserted; local-only kept;
   - channel newer (`set_at` greater) replaces local; channel older does not;
   - channel NULL (auto) newer than local 1 → local becomes NULL (tombstone travels);
   - channel snapshot at v10 (no table) → 0 taken, no error;
   - a second merge reports 0;
   - `shows.original_language` NULL locally is filled from a channel row even when the rows' `lang` differ (it is not `LANGUAGE_TEXT`, `merge_shows.rs:26`).
7. `edit --anime`: `AnimeChoice` (`#[derive(clap::ValueEnum)]`), `EditArgs.anime` with
   `conflicts_with_all = ["refresh", "kind", "tmdb", "clear", "title", "show", "year", "season", "episode", "chap", "path"]`,
   dispatch in `commands/edit.rs`, `edit/anime.rs` run (print label = `row.show` for `ep`,
   `row.title` for `movie`; key via `mediagram_tmdb::posters::poster_key`; stamp
   `crate::clock::now_unix()`). Tests for refusals and the no-op.
8. Docs + changelog + version bump. Comments/tests name no plan or phase.
9. `scripts/check.sh` green.
10. Operator backfill and publish — **on one machine, with no upload running**:
    1. `mediagram pull-index` (merge first; resume any pending uploads per the usual one-machine-at-a-time rule).
    2. `mediagram metadata` — opens the index (migrates to v11) and re-reads every
       title from `<data dir>/tmdb-cache`. No key, no network: all 981 `shows` rows
       on this machine have a cached details payload carrying `original_language`
       (checked 2026-09-28: 988 cached details payloads, 0 rows without one).
       Certification requests come from the same cache.
    3. Check: `sqlite3 ~/.local/share/mediagram/library.db "SELECT COUNT(*) FROM shows WHERE original_language IS NOT NULL"` ≈ 981, and
       `… "SELECT kind, COUNT(*) FROM shows WHERE original_language='ja' AND ','||replace(genres,', ',',')||',' LIKE '%,Animation,%' GROUP BY kind"`
       → about 32 movie / 4 tv (35 of those are in `sets` today).
    4. `mediagram push-index`. (`mediagram sync-index` does 1–4 plus posters in one go.)
    5. **Upgrade the second uploading machine before it publishes again.** Its first
       `pull-index` fills `original_language` by the NULL-fill; it does not need its own `metadata` run.

## Success Criteria

- [ ] A v10 `library.db` opened by the new build is at v11 with the column and table; a fresh one matches it.
- [ ] `mediagram metadata` offline (no `tmdb_key`, network off) fills `original_language` for every title with a cached payload.
- [ ] `edit <ep-set> --anime yes` writes one `anime_overrides` row keyed `('tmdb','tv',<show id>)`; `--anime auto` leaves the row with `anime` NULL and a newer `set_at`; `--dry-run` writes nothing; refusals for no TMDB id and for `tut`/`docu`.
- [ ] Merge tests above pass; `pull-index --dry-run` twice in a row reports 0 overrides the second time.
- [ ] `crates/mediagram/tests/code_standards.rs` green (tmdb_types.rs ≤ 200 after the split).
- [ ] `scripts/check.sh` green; web still reads a v10 and a v11 index (no reader change).
- [ ] Published channel index carries `original_language` for ~981 titles.

## Risk Assessment

| Risk | L×I | Mitigation |
|---|---|---|
| Second machine on v10 publishes: its snapshot has no column/table, so the channel loses languages and overrides until the v11 machine publishes again (anime would fall back into Movies/Series on readers) | M×M | Operator step 10.5: upgrade both uploaders before either publishes after the backfill. Local data is never lost (merge never deletes), so the next v11 publish restores it. |
| Upsert-in-SELECT parse ambiguity | L×L | `WHERE true` in the SELECT (SQLite docs' requirement); the merge tests execute it. |
| Clobbering an override on `metadata` | — | Impossible by construction: separate table, not written by `shows::upsert`. |
| `rescan` (rebuild from captions) loses local overrides | L×L | Captions never held them; the channel snapshot does — `pull-index` after a rescan inserts them back (channel-only keys are inserted). |
| Line limits (`tmdb_types.rs` 200, `shows/mod.rs` 181, `edit.rs` 186, `merge.rs` 171, `args.rs` 179) | M×L | Split named above; others stay < 200 with the listed additions. |

**Rollback:** revert the commit. A v11 index keeps working with a v10 build as a
*reader* (readers accept newer schemas: `mlib-spec/src/package/mod.rs:157-164`,
`web/src/package/pointer.ts:145-150`), but a v10 *uploader* refuses nothing and
simply ignores the extra column/table; its next publish drops them from the
channel (see risk 1). No data migration to undo.

## Security Considerations

No new input surface beyond a clap enum; SQL uses bound parameters; the override
is data about public TMDB titles.
